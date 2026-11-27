package tech.kelma.app.testplugins

import tech.kelma.plugin.KelmaCard
import tech.kelma.plugin.KelmaPlugin
import tech.kelma.plugin.KelmaPluginContext

// Plugins the tests pack into JARs (each a public class with a no-argument constructor).

class BasePlugin : KelmaPlugin {
    override fun start(context: KelmaPluginContext) {
        context.log("info", "base started")
    }
}

class FeaturePlugin : KelmaPlugin {
    override fun start(context: KelmaPluginContext) {
        context.registerCommand("tech.kelma.feature.hello", "Hello") { arguments ->
            val kelma = arguments["kelma_context"] as Map<*, *>
            "${arguments["name"]} from a JAR:${kelma["screen"]}:${kelma["deck_name"]}"
        }
        context.registerCommand("tech.kelma.feature.stats") { mapOf("cards" to 3, "tags" to listOf("a", "b"), "done" to true) }
        context.registerCommand("tech.kelma.feature.bad", "Bad") { Any() }
        context.subscribe("review.completed") { _, attributes -> context.log("info", "rated:${attributes["rating"]}") }
    }

    private fun KelmaPluginContext.registerCommand(id: String, handler: (Map<String, Any?>) -> Any?) =
        registerCommand(id, id.substringAfterLast('.')) { handler(it) }
}

class WrapRendererPlugin : KelmaPlugin {
    override fun start(context: KelmaPluginContext) {
        context.registerRenderer("tech.kelma.renderer.wrap") { card ->
            KelmaCard("<section>${card.html}</section>", card.css + ".wrapped{display:block}")
        }
    }
}

class ViolationPlugin : KelmaPlugin {
    override fun start(context: KelmaPluginContext) {
        context.registerCommand("tech.kelma.violation.command", "Denied") { null }
    }
}

class ThrowingPlugin : KelmaPlugin {
    override fun start(context: KelmaPluginContext): Unit = throw IllegalStateException("boom at start")
}

class NotAPlugin
