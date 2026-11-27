package tech.kelma.app

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import tech.kelma.db.KelmaDatabase

/**
 * The example plugin (examples/hello-plugin), compiled from its Java source against the app's
 * classes and installed as a JAR: its class exists only inside that JAR, so this is the real
 * isolated loading path, and the example stays buildable.
 */
class ExamplePluginJarTest {
    private val example = File("../examples/hello-plugin")

    @Test
    fun theJavaExamplePluginInstallsAndRunsFromItsOwnJar() = runTest {
        val jar = compileExample()
        assertFailsWith<ClassNotFoundException> { Class.forName("org.example.hello.HelloPlugin") }

        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KelmaDatabase.Schema.create(driver)
        val store = PersistentCollectionStore(KelmaDatabase(driver))
        val commands = PluginCommandRegistry()
        val renderers = PluginRendererRegistry()
        val host = store.createPluginHost(commands, PluginEventRegistry(), renderers)
        try {
            val state = host.install(InterchangeDocument("kelma-hello.jar", jar))
            assertEquals(listOf("org.example.hello"), state.running.map(RunningPlugin::pluginId), state.installed.toString())
            assertEquals("Received app.started", host.logs("org.example.hello").first().message)
            assertEquals(
                PluginValue.ObjectValue(
                    mapOf("message" to PluginValue.StringValue("Hello, Ada!"), "screen" to PluginValue.StringValue("decks")),
                ),
                commands.invoke(
                    PluginCommandInvocation(
                        "org.example.hello.greet",
                        mapOf("name" to PluginValue.StringValue("Ada")),
                        PluginCommandContext("decks"),
                    ),
                ),
            )
            val rendered = host.render(PluginRenderRequest("org.example.hello.border", "<b>Q</b>", ".card{}"))
            assertEquals("<div class=\"hello-plugin\"><b>Q</b></div>", rendered.html)
            assertTrue(rendered.css.startsWith(".card{}.hello-plugin{"))
            host.publish(PluginEvent("review.completed", mapOf("rating" to PluginValue.StringValue("Good"))))
            assertTrue(host.logs("org.example.hello").any { it.message == "Review rating: Good" })
        } finally {
            host.close()
            driver.close()
        }
    }

    private fun compileExample(): ByteArray {
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) { "Tests need a JDK" }
        val out = Files.createTempDirectory("kelma-example-plugin").toFile()
        try {
            val sources = example.resolve("src").walkTopDown().filter { it.extension == "java" }.map(File::getPath).toList()
            val status = compiler.run(
                null, null, null,
                "-classpath", System.getProperty("java.class.path"), "-d", out.path, *sources.toTypedArray(),
            )
            check(status == 0) { "The example plugin doesn't compile" }
            val bytes = ByteArrayOutputStream()
            JarOutputStream(bytes).use { jar ->
                fun add(name: String, content: ByteArray) {
                    jar.putNextEntry(JarEntry(name))
                    jar.write(content)
                    jar.closeEntry()
                }
                add(PluginManifestFile, example.resolve(PluginManifestFile).readBytes())
                out.walkTopDown().filter(File::isFile).forEach { add(it.relativeTo(out).invariantSeparatorsPath, it.readBytes()) }
            }
            return bytes.toByteArray()
        } finally {
            out.deleteRecursively()
        }
    }

}
