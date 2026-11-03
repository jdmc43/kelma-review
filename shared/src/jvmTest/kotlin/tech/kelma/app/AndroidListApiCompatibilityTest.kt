package tech.kelma.app

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Kotlin's MutableList.removeFirst()/removeLast() compile to java.util.List methods
 * added in Android 15 (API 35) when targeting it, and crash with NoSuchMethodError
 * on Android 14 and earlier. Use removeAt(0) / removeAt(lastIndex) instead.
 */
class AndroidListApiCompatibilityTest {
    @Test
    fun sharedAndAndroidSourcesNeverCallRemoveFirstOrRemoveLast() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val offenders = listOf("shared/src", "androidApp/src").flatMap { dir ->
            File(root, dir).walkTopDown().filter { it.isFile && it.extension == "kt" && "Test" !in it.path }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        if (Regex("""\.remove(First|Last)\(\)""").containsMatchIn(line)) "${file.relativeTo(root)}:${index + 1}" else null
                    }
                }.toList()
        }
        assertTrue(offenders.isEmpty(), "removeFirst()/removeLast() crash on Android 14 and earlier: $offenders")
    }

    @Test
    fun nestedConditionalSectionsStillRender() {
        val definition = JsonObject(mapOf(
            "flds" to JsonArray(listOf("Front", "Back", "Extra").map { JsonObject(mapOf("name" to JsonPrimitive(it))) }),
            "tmpls" to JsonArray(listOf(JsonObject(mapOf(
                "ord" to JsonPrimitive(0),
                "qfmt" to JsonPrimitive("{{Front}}{{#Back}}<i>{{#Extra}}[{{Extra}}]{{/Extra}}{{^Extra}}(none){{/Extra}}</i>{{/Back}}"),
                "afmt" to JsonPrimitive("{{FrontSide}}<hr id=answer>{{Back}}"),
            )))),
        ))
        val notetype = SyncNotetype(notetypeId = 1, name = "Nested", definition = definition)
        fun render(vararg fields: String) = CardTemplateRenderer.render(
            SyncCard(1, "g", "Deck", 0), SyncNote("g", 1, fields.toList()), notetype, emptyMap(),
        ).front
        assertTrue("[x]" in render("q", "a", "x"), render("q", "a", "x"))
        assertTrue("(none)" in render("q", "a", ""), render("q", "a", ""))
        assertTrue("<i>" !in render("q", "", "x"), "outer section hidden when Back is empty")
    }

    @Test
    fun closingTagsUnwindStylesInOrder() {
        val rendered = renderInlineHtml("<b>bold <i>both</i></b> plain")
        assertEquals("bold both plain", rendered.text)
        assertEquals(2, rendered.spanStyles.size)
    }
}
