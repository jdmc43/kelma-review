package tech.kelma.app

import kotlin.test.Test
import kotlin.test.assertTrue

class IntentIdFormatTest {
    // Mirrors KelmaSync's intent validation (internal/handler/note_mark.go).
    private val canonicalUuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    @Test
    fun generatedIntentIdsAreCanonicalLowercaseUuids() {
        repeat(200) {
            val id = randomUuidString()
            assertTrue(canonicalUuid.matches(id), "KelmaSync would reject intent id $id")
        }
    }
}
