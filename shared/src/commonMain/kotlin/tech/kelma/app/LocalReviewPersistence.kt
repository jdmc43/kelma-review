package tech.kelma.app

import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import tech.kelma.db.KelmaQueries

private val syncCapabilitiesSerializer = ListSerializer(String.serializer())

internal fun reviewRetractionSyncEnabled(queries: KelmaQueries): Boolean {
    val encoded = queries.selectSyncState { _, capabilities -> capabilities }.executeAsOneOrNull() ?: return false
    val capabilities = runCatching {
        Json.decodeFromString(syncCapabilitiesSerializer, encoded)
    }.getOrDefault(emptyList())
    return ReviewRetractionSyncCapability in capabilities
}

internal fun loadLocalReviewSnapshot(
    queries: KelmaQueries,
    nowMillis: Long,
    studyDayPolicy: AccountStudyDayPolicy,
): LocalReviewSnapshot {
    val schedules = queries.selectLocalSchedules { cardId, phase, dueAt, stability, difficulty,
            scheduledDays, repetitions, lapses, lastReviewAt, step ->
        LocalCardSchedule(
            cardId = cardId,
            phase = phase.asReviewPhase(),
            dueAtMillis = dueAt,
            stability = stability,
            difficulty = difficulty,
            scheduledDays = scheduledDays.toInt(),
            repetitions = repetitions.toInt(),
            lapses = lapses.toInt(),
            lastReviewAtMillis = lastReviewAt,
            step = step?.toInt(),
        )
    }.executeAsList().associateBy(LocalCardSchedule::cardId).toPersistentMap()
    val remoteDueDates = queries.selectSyncCardDueOverrides { cardId, dueAtMillis, clientModifiedAt ->
        cardId to TimedDueDate(dueAtMillis, rfc3339ToEpochMillis(clientModifiedAt) ?: 0L)
    }.executeAsList().toMap()
    val localDueDates = queries.selectEffectiveLocalCardDueOverrides {
            cardId, dueAtMillis, clientModifiedAt ->
        cardId to TimedDueDate(dueAtMillis, clientModifiedAt)
    }.executeAsList().toMap()
    val dueDateOverrides = (remoteDueDates.keys + localDueDates.keys).mapNotNull { cardId ->
        val remote = remoteDueDates[cardId]
        val local = localDueDates[cardId]
        val effective = if (local != null && local.modifiedAtMillis >= (remote?.modifiedAtMillis ?: 0L)) {
            local
        } else {
            remote
        }
        effective?.dueAtMillis?.takeIf { it > 0L }?.let { cardId to it }
    }.toMap()
    val studyDay = studyDayAt(nowMillis, studyDayPolicy)
    // Exact integer bounds for "today" so the loops below skip ~80k per-review studyDayAt() calls.
    val todayWindow = studyDayWindow(nowMillis, studyDayPolicy)
    val buriedCardIds = queries.selectLocalCardBuriesForDay(studyDay).executeAsList().toSet()
    val buriedNoteGuids = queries.selectLocalNoteBuriesForDay(studyDay).executeAsList().toSet()
    val overrides = queries.selectLocalDeckOverrides { source, replacement -> source to replacement }
        .executeAsList().toMap()
    val confirmed = queries.selectActiveReviews {
            reviewId, sourceCardId, noteGuid, cardOrd, deckName, _, _, _, _, _, reviewKind, _, _ ->
        val identity = reviewLimitCardKey(noteGuid, cardOrd.toInt(), sourceCardId)
        CountedReview(reviewId, cardOrd.toInt(), noteGuid, identity, deckName, reviewKind.toInt())
    }.executeAsList()
    val firstReviewIds = confirmed.groupBy(CountedReview::cardIdentity)
        .mapValues { (_, events) -> events.minOf(CountedReview::reviewId) }
    val counts = mutableMapOf<String, MutableDeckStudyCounts>()
    val reviewLimitConsumedCardKeys = mutableSetOf<String>()
    var reviewedToday = 0
    confirmed.filter { it.reviewId in todayWindow }.forEach { event ->
        val deckName = event.deckName.remapDownloadedDeckName(overrides) ?: return@forEach
        val deckCounts = counts.getOrPut(deckName, ::MutableDeckStudyCounts)
        when {
            firstReviewIds[event.cardIdentity] == event.reviewId -> deckCounts.newCards++
            event.reviewKind == AnkiReviewKindReview && reviewLimitConsumedCardKeys.add(event.cardIdentity) -> {
                deckCounts.reviews++
            }
        }
        reviewedToday++
    }
    val studiedCardsByNote = mutableMapOf<String, MutableSet<Int>>()
    confirmed.filter {
        it.reviewId in todayWindow && it.noteGuid.isNotBlank()
    }.forEach { event ->
        studiedCardsByNote.getOrPut(event.noteGuid, ::mutableSetOf).add(event.cardOrd)
    }
    val synchronizedCounts = mutableMapOf<String, MutableDeckStudyCounts>()
    queries.selectStudyDays { day, deckName, newStudied, reviewStudied, _, _, _ ->
        if (day != studyDay) return@selectStudyDays
        val visibleDeck = deckName.remapDownloadedDeckName(overrides) ?: return@selectStudyDays
        val total = synchronizedCounts.getOrPut(visibleDeck, ::MutableDeckStudyCounts)
        total.newCards += newStudied.toInt()
        total.reviews += reviewStudied.toInt()
    }.executeAsList()

    val confirmedIds = queries.selectSyncReviewIds().executeAsList().toSet()
    val localEvents = queries.selectAllLocalReviewEvents {
            _, cardId, noteGuid, cardOrd, deckName, _, reviewedAt, _, _, beforeJson, _, wasNew,
            reviewId, _, consumed ->
        LocalCountedReview(
            cardId = cardId,
            reviewId = reviewId,
            cardOrd = cardOrd.toInt(),
            noteGuid = noteGuid,
            reviewedAtMillis = reviewedAt,
            deckName = deckName,
            wasNew = wasNew == 1L,
            consumedReviewLimit = consumed == 1L ||
                consumed < 0L && beforeJson?.contains("\"phase\":\"Review\"") == true,
        )
    }.executeAsList()
    val locallyConsumedReviewKeys = mutableSetOf<String>()
    // This device's own answers today: confirmed ones are already in the shared
    // history; unconfirmed ones (not uploaded yet) are in no other count.
    val ownConfirmed = mutableMapOf<String, MutableDeckStudyCounts>()
    val ownUnconfirmed = mutableMapOf<String, MutableDeckStudyCounts>()
    localEvents.filter { it.reviewedAtMillis in todayWindow }.forEach { event ->
        val deckName = event.deckName.remapDownloadedDeckName(overrides) ?: return@forEach
        val isUnconfirmed = event.reviewId !in confirmedIds
        val cardKey = reviewLimitCardKey(event.noteGuid, event.cardOrd, event.cardId)
        val deckCounts = (if (isUnconfirmed) ownUnconfirmed else ownConfirmed)
            .getOrPut(deckName, ::MutableDeckStudyCounts)
        when {
            event.wasNew -> deckCounts.newCards++
            event.consumedReviewLimit &&
                !(isUnconfirmed && cardKey in reviewLimitConsumedCardKeys) &&
                locallyConsumedReviewKeys.add(cardKey) -> deckCounts.reviews++
        }
        if (event.consumedReviewLimit) locallyConsumedReviewKeys += cardKey
        if (isUnconfirmed) {
            if (event.noteGuid.isNotBlank()) {
                studiedCardsByNote.getOrPut(event.noteGuid, ::mutableSetOf).add(event.cardOrd)
            }
            reviewedToday++
        }
    }
    reviewLimitConsumedCardKeys += locallyConsumedReviewKeys
    combineStudiedToday(counts, synchronizedCounts, ownConfirmed, ownUnconfirmed)
    val localCardIds = queries.selectLocalCards { cardId, _, _, _, _ -> cardId }
        .executeAsList().toSet()
    val changedByDeck = mutableMapOf<String, MutableSet<Long>>()
    localEvents.filter { it.cardId !in localCardIds }.forEach { event ->
        event.deckName.remapDownloadedDeckName(overrides)?.let { visibleDeck ->
            changedByDeck.getOrPut(visibleDeck, ::mutableSetOf).add(event.cardId)
        }
    }
    val revision = queries.selectMaximumReviewMutationId { value -> value ?: 0L }.executeAsOne()
    return LocalReviewSnapshot(
        revision = revision,
        studyDay = studyDay,
        studyDayPolicy = studyDayPolicy,
        schedules = schedules,
        dueDateOverrides = dueDateOverrides,
        buriedCardIds = buriedCardIds,
        buriedNoteGuids = buriedNoteGuids,
        reviewedToday = reviewedToday,
        studiedTodayByDeck = counts.mapValues { (_, value) ->
            DeckStudyCounts(value.newCards, value.reviews)
        },
        studiedCardOrdsByNoteToday = studiedCardsByNote.mapValues { it.value.toSet() },
        reviewLimitConsumedCardKeysToday = reviewLimitConsumedCardKeys,
        lastReviewDeck = (
            queries.selectLatestLocalReviewDeck().executeAsOneOrNull()
                ?: if (reviewRetractionSyncEnabled(queries)) {
                    queries.selectLatestSyncReviewDeckForRetraction().executeAsOneOrNull()
                } else {
                    null
                }
            )?.remapDownloadedDeckName(overrides),
        pendingSyncByDeck = changedByDeck.mapValues { (_, cardIds) ->
            PendingDeckChanges(changedCardIds = cardIds)
        },
    )
}

internal fun loadLocalSchedule(queries: KelmaQueries, cardId: Long): LocalCardSchedule? =
    queries.selectLocalSchedule(cardId) { id, phase, dueAt, stability, difficulty,
            scheduledDays, repetitions, lapses, lastReviewAt, step ->
        LocalCardSchedule(
            id,
            phase.asReviewPhase(),
            dueAt,
            stability,
            difficulty,
            scheduledDays.toInt(),
            repetitions.toInt(),
            lapses.toInt(),
            lastReviewAt,
            step?.toInt(),
        )
    }.executeAsOneOrNull()

internal fun upsertLocalSchedule(queries: KelmaQueries, schedule: LocalCardSchedule) {
    queries.upsertLocalSchedule(
        schedule.cardId,
        schedule.phase.name,
        schedule.dueAtMillis,
        schedule.stability,
        schedule.difficulty,
        schedule.scheduledDays.toLong(),
        schedule.repetitions.toLong(),
        schedule.lapses.toLong(),
        schedule.lastReviewAtMillis,
        schedule.step?.toLong(),
    )
}

internal fun String.asReviewPhase(): ReviewPhase =
    ReviewPhase.entries.firstOrNull { it.name == this } ?: ReviewPhase.Review

private data class TimedDueDate(val dueAtMillis: Long, val modifiedAtMillis: Long)
private data class CountedReview(
    val reviewId: Long,
    val cardOrd: Int,
    val noteGuid: String,
    val cardIdentity: String,
    val deckName: String,
    val reviewKind: Int,
)
private data class LocalCountedReview(
    val cardId: Long,
    val reviewId: Long,
    val cardOrd: Int,
    val noteGuid: String,
    val reviewedAtMillis: Long,
    val deckName: String,
    val wasNew: Boolean,
    val consumedReviewLimit: Boolean,
)
private class MutableDeckStudyCounts(var newCards: Int = 0, var reviews: Int = 0)

private const val AnkiReviewKindReview = 1

/**
 * Today's studied counts per deck. The shared review history covers every
 * device that has synced, so it is the primary count. A synchronized daily
 * snapshot comes from one client's own counters (for example an Anki that
 * counted only its own answers), so it never replaces the history: together
 * with this device's uploaded answers it can only raise the count. Answers not
 * uploaded yet are in neither and are added on top.
 */
private fun combineStudiedToday(
    history: MutableMap<String, MutableDeckStudyCounts>,
    synchronized: Map<String, MutableDeckStudyCounts>,
    ownConfirmed: Map<String, MutableDeckStudyCounts>,
    ownUnconfirmed: Map<String, MutableDeckStudyCounts>,
) {
    synchronized.forEach { (deckName, snapshot) ->
        val own = ownConfirmed[deckName]
        val counts = history.getOrPut(deckName, ::MutableDeckStudyCounts)
        counts.newCards = maxOf(counts.newCards, snapshot.newCards + (own?.newCards ?: 0))
        counts.reviews = maxOf(counts.reviews, snapshot.reviews + (own?.reviews ?: 0))
    }
    ownUnconfirmed.forEach { (deckName, pending) ->
        val counts = history.getOrPut(deckName, ::MutableDeckStudyCounts)
        counts.newCards += pending.newCards
        counts.reviews += pending.reviews
    }
}
