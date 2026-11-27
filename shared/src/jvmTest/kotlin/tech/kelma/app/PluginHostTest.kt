package tech.kelma.app

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import tech.kelma.app.testplugins.BasePlugin
import tech.kelma.app.testplugins.FeaturePlugin
import tech.kelma.app.testplugins.NotAPlugin
import tech.kelma.app.testplugins.ThrowingPlugin
import tech.kelma.app.testplugins.ViolationPlugin
import tech.kelma.app.testplugins.WrapRendererPlugin
import tech.kelma.db.KelmaDatabase

class PluginHostTest {
    private val json = Json { ignoreUnknownKeys = true }

    private class Fixture(val driver: JdbcSqliteDriver, val store: PersistentCollectionStore)

    private fun fixture(): Fixture {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KelmaDatabase.Schema.create(driver)
        return Fixture(driver, PersistentCollectionStore(KelmaDatabase(driver)))
    }

    @Test
    fun jarPluginsLoadInDependencyOrderAndSafeModeStopsAllCode() = runTest {
        val f = fixture()
        val commands = PluginCommandRegistry()
        val host = f.store.createPluginHost(commands, PluginEventRegistry(), PluginRendererRegistry())
        try {
            host.install(pluginJar(manifest("tech.kelma.base", BasePlugin::class, capabilities = emptySet()), BasePlugin::class))
            val feature = manifest(
                "tech.kelma.feature",
                FeaturePlugin::class,
                capabilities = setOf(PluginCapability.Commands, PluginCapability.Events),
                dependencies = listOf(PluginDependency("tech.kelma.base", "1.0.0")),
            )
            val running = host.install(pluginJar(feature, FeaturePlugin::class))
            assertEquals(listOf("tech.kelma.base", "tech.kelma.feature"), running.running.map(RunningPlugin::pluginId))
            assertEquals(
                PluginValue.StringValue("Kelma from a JAR:review:Deck"),
                commands.invoke(
                    PluginCommandInvocation(
                        "tech.kelma.feature.hello",
                        mapOf("name" to PluginValue.StringValue("Kelma")),
                        PluginCommandContext(screen = "review", deckName = "Deck"),
                    ),
                ),
            )
            assertEquals("base started", host.logs("tech.kelma.base").first().message)
            assertTrue(host.publish(PluginEvent("review.completed", mapOf("rating" to PluginValue.StringValue("Good")))).isEmpty())
            assertEquals("rated:Good", host.logs("tech.kelma.feature").first().message)

            val safe = host.setSafeMode(true)
            assertTrue(safe.safeMode)
            assertTrue(safe.running.isEmpty())
            assertTrue(commands.list().isEmpty())
            assertFalse(f.store.listInstalledPlugins().any { it.status == PluginStatus.Failed })

            val restored = host.setSafeMode(false)
            assertEquals(2, restored.running.size)
            assertEquals(3, commands.list().size)
        } finally {
            host.close()
            f.driver.close()
        }
    }

    @Test
    fun commandResultsCrossTheBoundaryAsJsonCompatibleValues() = runTest {
        val f = fixture()
        val commands = PluginCommandRegistry()
        val host = f.store.createPluginHost(commands, PluginEventRegistry(), PluginRendererRegistry())
        try {
            host.install(pluginJar(manifest("tech.kelma.feature", FeaturePlugin::class, setOf(PluginCapability.Commands, PluginCapability.Events)), FeaturePlugin::class))
            val stats = commands.invoke(PluginCommandInvocation("tech.kelma.feature.stats", emptyMap(), PluginCommandContext("decks")))
            assertEquals(
                PluginValue.ObjectValue(
                    mapOf(
                        "cards" to PluginValue.NumberValue(3.0),
                        "tags" to PluginValue.ListValue(listOf(PluginValue.StringValue("a"), PluginValue.StringValue("b"))),
                        "done" to PluginValue.BooleanValue(true),
                    ),
                ),
                stats,
            )
            // A value that isn't JSON-compatible fails the command, attributed to the plugin.
            val failure = kotlin.runCatching {
                commands.invoke(PluginCommandInvocation("tech.kelma.feature.bad", emptyMap(), PluginCommandContext("decks")))
            }.exceptionOrNull()
            assertIs<IllegalArgumentException>(failure)
            val plugin = f.store.listInstalledPlugins().single()
            assertEquals(PluginStatus.Failed, plugin.status)
            assertTrue(plugin.errorMessage.orEmpty().contains("JSON-compatible"))
        } finally {
            host.close()
            f.driver.close()
        }
    }

    @Test
    fun assignedRendererTransformsCompleteQuestionAndAnswerDocuments() = runTest {
        val f = fixture()
        val renderers = PluginRendererRegistry()
        val host = f.store.createPluginHost(PluginCommandRegistry(), PluginEventRegistry(), renderers)
        try {
            host.install(pluginJar(manifest("tech.kelma.renderer", WrapRendererPlugin::class, setOf(PluginCapability.Ui)), WrapRendererPlugin::class))
            val card = ReviewCard(
                id = 1,
                front = "front",
                back = "back",
                frontHtml = "<b>front</b>",
                fullAnswerHtml = "<b>front</b><hr id=answer><i>back</i>",
                cardCss = ".card{}",
            )
            val collection = SyncedCollection(
                notes = mapOf("note" to SyncNote("note", notetypeId = 100)),
                cards = mapOf(1L to SyncCard(1, "note", "Deck")),
            )
            val assignments = PluginRendererAssignmentState(
                listOf(PluginRendererAssignment(PluginRendererScope.Deck, "Deck", "tech.kelma.renderer.wrap")),
            )
            val batch = renderAssignedReviewCards(
                host, renderers, assignments, collection, listOf(card),
                runtimeGeneration = host.state().runtimeGeneration,
            )
            val rendered = batch.cards.getValue(1L).rendered
            assertEquals("<section><b>front</b></section>", rendered.frontHtml)
            assertTrue(rendered.fullAnswerHtml.orEmpty().contains("<hr id=answer>"))
            assertTrue(rendered.cardCss.contains(".wrapped"))
            assertTrue(rendered.answerCardCss.orEmpty().contains(".wrapped"))
            val cached = renderAssignedReviewCards(
                host, renderers, assignments, collection, listOf(card),
                runtimeGeneration = host.state().runtimeGeneration,
                existing = batch.cards,
            )
            assertSame(batch.cards.getValue(1L), cached.cards.getValue(1L))
        } finally {
            host.close()
            f.driver.close()
        }
    }

    @Test
    fun failingAndCapabilityViolatingPluginsAreAttributed() = runTest {
        val f = fixture()
        val host = f.store.createPluginHost(PluginCommandRegistry(), PluginEventRegistry(), PluginRendererRegistry())
        try {
            host.install(pluginJar(manifest("tech.kelma.violation", ViolationPlugin::class, capabilities = emptySet()), ViolationPlugin::class))
            val state = host.install(pluginJar(manifest("tech.kelma.throwing", ThrowingPlugin::class), ThrowingPlugin::class))
            val byId = state.installed.associateBy { it.manifest.id }
            assertEquals(PluginStatus.Failed, byId.getValue("tech.kelma.violation").status)
            assertTrue(byId.getValue("tech.kelma.violation").errorMessage.orEmpty().contains("Declare the Commands capability"))
            assertEquals(PluginStatus.Failed, byId.getValue("tech.kelma.throwing").status)
            assertTrue(byId.getValue("tech.kelma.throwing").errorMessage.orEmpty().contains("boom at start"))
            assertTrue(state.running.isEmpty())
        } finally {
            host.close()
            f.driver.close()
        }
    }

    @Test
    fun aLuaPluginInstalledBeforeIsKeptButNoLongerRuns() = runTest {
        val f = fixture()
        val commands = PluginCommandRegistry()
        val host = f.store.createPluginHost(commands, PluginEventRegistry(), PluginRendererRegistry())
        try {
            f.store.installPluginManifest(
                PluginManifest("org.example.old", "Old", "1.0.0", KelmaPluginApiVersion, "plugin/init.lua", runtime = "lua54"),
            )
            host.install(pluginJar(manifest("tech.kelma.feature", FeaturePlugin::class, setOf(PluginCapability.Commands, PluginCapability.Events)), FeaturePlugin::class))
            val state = host.reload()
            val old = state.installed.single { it.manifest.id == "org.example.old" }
            assertEquals(PluginStatus.Failed, old.status)
            assertEquals(LegacyPluginMessage, old.errorMessage)
            assertEquals(listOf("tech.kelma.feature"), state.running.map(RunningPlugin::pluginId))
            assertEquals(3, commands.list().size)
        } finally {
            host.close()
            f.driver.close()
        }
    }

    @Test
    fun theInstallerChecksTheJarAndItsManifest() {
        fun failure(document: InterchangeDocument) =
            kotlin.runCatching { decodePluginPackage(document, json) }.exceptionOrNull()?.message.orEmpty()

        val noManifest = InterchangeDocument("plain.jar", writeStoredZip(listOf(classEntry(BasePlugin::class))))
        assertTrue(failure(noManifest).contains("kelma-plugin.json"))

        val missingClass = InterchangeDocument(
            "missing.jar",
            writeStoredZip(listOf(manifestEntry(manifest("tech.kelma.missing", BasePlugin::class)))),
        )
        assertTrue(failure(missingClass).contains("entrypoint class"))

        val lua = manifest("tech.kelma.lua", BasePlugin::class).copy(runtime = "lua54", entrypoint = "plugin/init.lua")
        assertTrue(failure(InterchangeDocument("lua.jar", writeStoredZip(listOf(manifestEntry(lua))))).contains("desktop JAR plugins"))

        assertTrue(failure(InterchangeDocument("x.kelmaplugin", "not a zip".encodeToByteArray())).contains("not a plugin .jar"))

        val good = decodePluginPackage(pluginJar(manifest("tech.kelma.base", BasePlugin::class), BasePlugin::class), json)
        assertEquals(setOf(PluginJarFile), good.files.keys)
        assertEquals("tech.kelma.app.testplugins.BasePlugin", good.manifest.entrypoint)
    }

    @Test
    fun anEntrypointThatIsntAKelmaPluginFailsToStart() = runTest {
        val f = fixture()
        val host = f.store.createPluginHost(PluginCommandRegistry(), PluginEventRegistry(), PluginRendererRegistry())
        try {
            val state = host.install(pluginJar(manifest("tech.kelma.notaplugin", NotAPlugin::class), NotAPlugin::class))
            val plugin = state.installed.single()
            assertEquals(PluginStatus.Failed, plugin.status)
            assertTrue(plugin.errorMessage.orEmpty().contains("does not implement tech.kelma.plugin.KelmaPlugin"))
        } finally {
            host.close()
            f.driver.close()
        }
    }

    private fun manifest(
        id: String,
        entry: KClass<*>,
        capabilities: Set<PluginCapability> = setOf(PluginCapability.Commands),
        dependencies: List<PluginDependency> = emptyList(),
    ) = PluginManifest(
        id = id,
        name = id.substringAfterLast('.'),
        version = "1.0.0",
        apiVersion = KelmaPluginApiVersion,
        entrypoint = requireNotNull(entry.qualifiedName),
        capabilities = capabilities,
        dependencies = dependencies,
    )

    private fun manifestEntry(manifest: PluginManifest) =
        StoredZipEntry(PluginManifestFile, json.encodeToString(manifest).encodeToByteArray())

    private fun classEntry(type: KClass<*>): StoredZipEntry {
        val path = type.java.name.replace('.', '/') + ".class"
        val bytes = requireNotNull(type.java.classLoader.getResourceAsStream(path)) { "no class file for $path" }.use { it.readBytes() }
        return StoredZipEntry(path, bytes)
    }

    private fun pluginJar(manifest: PluginManifest, vararg classes: KClass<*>): InterchangeDocument =
        InterchangeDocument("${manifest.id}.jar", writeStoredZip(listOf(manifestEntry(manifest)) + classes.map(::classEntry)))
}
