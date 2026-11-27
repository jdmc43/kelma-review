package tech.kelma.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import tech.kelma.plugin.KelmaCard
import tech.kelma.plugin.KelmaCardRenderer
import tech.kelma.plugin.KelmaCommandHandler
import tech.kelma.plugin.KelmaEventHandler
import tech.kelma.plugin.KelmaPlugin
import tech.kelma.plugin.KelmaPluginContext
import java.io.File
import java.net.URLClassLoader

internal actual fun createPlatformPluginRuntime(
    pluginId: String,
    capabilities: Set<PluginCapability>,
    files: Map<String, ByteArray>,
    entrypoint: String,
): PluginRuntime = JarPluginRuntime.load(pluginId, capabilities, files, entrypoint)

/**
 * A desktop plugin: its JAR in a class loader of its own (the public API, `tech.kelma.plugin`,
 * comes from the app), started once, with calls in and out as JSON-compatible values.
 */
internal class JarPluginRuntime private constructor(
    private val pluginId: String,
    private val capabilities: Set<PluginCapability>,
    private val jarFile: File,
    private val classLoader: URLClassLoader,
) : PluginRuntime {
    private val commandHandlers = linkedMapOf<String, Pair<String, KelmaCommandHandler>>()
    private val eventHandlers = linkedMapOf<String, MutableList<KelmaEventHandler>>()
    private val renderers = linkedMapOf<String, KelmaCardRenderer>()
    private val logs = mutableListOf<PluginRuntimeLog>()
    private var plugin: KelmaPlugin? = null
    private var starting = true

    override val commands: List<PluginRuntimeCommand>
        get() = commandHandlers.map { (id, entry) -> PluginRuntimeCommand(id, entry.first) }
    override val eventNames: Set<String> get() = eventHandlers.keys.toSet()
    override val rendererIds: Set<String> get() = renderers.keys.toSet()

    private val context = object : KelmaPluginContext {
        override val pluginId: String get() = this@JarPluginRuntime.pluginId

        override fun registerCommand(id: String, title: String, handler: KelmaCommandHandler) {
            requireRegistering(PluginCapability.Commands, "commands")
            require(PluginLocalId.matches(id)) { "Command id is invalid: $id" }
            require(title.isNotBlank() && title.length <= 120) { "Command title is invalid" }
            require(id !in commandHandlers) { "Command $id is already registered" }
            commandHandlers[id] = title to handler
        }

        override fun subscribe(event: String, handler: KelmaEventHandler) {
            requireRegistering(PluginCapability.Events, "events")
            require(event.isNotBlank() && event.length <= 120) { "Event name is invalid" }
            eventHandlers.getOrPut(event) { mutableListOf() } += handler
        }

        override fun registerRenderer(id: String, renderer: KelmaCardRenderer) {
            requireRegistering(PluginCapability.Ui, "renderers")
            require(PluginLocalId.matches(id)) { "Renderer id is invalid: $id" }
            require(id !in renderers) { "Renderer $id is already registered" }
            renderers[id] = renderer
        }

        override fun log(level: String, message: String) {
            val normalized = level.lowercase().takeIf { it in PluginLogLevels } ?: "info"
            synchronized(logs) { logs += PluginRuntimeLog(normalized, message.take(MaximumLogMessage)) }
        }
    }

    private fun requireRegistering(capability: PluginCapability, what: String) {
        check(starting) { "Plugins register $what in start()" }
        require(capability in capabilities) { "Declare the ${capability.name} capability to register $what" }
    }

    private fun start(entrypoint: String) {
        val type = Class.forName(entrypoint, true, classLoader)
        require(KelmaPlugin::class.java.isAssignableFrom(type)) {
            "$entrypoint does not implement tech.kelma.plugin.KelmaPlugin"
        }
        val instance = type.getDeclaredConstructor().newInstance() as KelmaPlugin
        plugin = instance
        try {
            instance.start(context)
        } finally {
            starting = false
        }
    }

    override fun invoke(commandId: String, argumentsJson: String): String {
        val handler = commandHandlers[commandId]?.second ?: error("Unknown command $commandId")
        @Suppress("UNCHECKED_CAST")
        val arguments = (BoundaryJson.parseToJsonElement(argumentsJson).toJavaValue() as? Map<String, Any?>).orEmpty()
        val result = handler.invoke(arguments)
        return BoundaryJson.encodeToString(JsonElement.serializer(), result.toBoundaryElement())
    }

    override fun publish(eventName: String, attributesJson: String) {
        val handlers = eventHandlers[eventName].orEmpty()
        if (handlers.isEmpty()) return
        @Suppress("UNCHECKED_CAST")
        val attributes = (BoundaryJson.parseToJsonElement(attributesJson).toJavaValue() as? Map<String, Any?>).orEmpty()
        handlers.forEach { it.onEvent(eventName, attributes) }
    }

    override fun render(rendererId: String, html: String, css: String): PluginRenderResult {
        val renderer = renderers[rendererId] ?: error("Unknown renderer $rendererId")
        val rendered = renderer.render(KelmaCard(html, css))
        return PluginRenderResult(rendered.html, rendered.css)
    }

    override fun drainLogs(): List<PluginRuntimeLog> = synchronized(logs) {
        logs.toList().also { logs.clear() }
    }

    override fun close() {
        try {
            plugin?.stop()
        } catch (_: Throwable) {
            // A plugin failing to stop must not keep Kelma from unloading it.
        } finally {
            plugin = null
            classLoader.close()
            jarFile.delete()
        }
    }

    companion object {
        fun load(
            pluginId: String,
            capabilities: Set<PluginCapability>,
            files: Map<String, ByteArray>,
            entrypoint: String,
        ): JarPluginRuntime {
            val jar = requireNotNull(files[PluginJarFile]) {
                "This plugin isn't a desktop JAR plugin (Lua plugins no longer run); install its JAR version"
            }
            val jarFile = File.createTempFile("kelma-plugin-$pluginId-", ".jar").apply {
                deleteOnExit()
                writeBytes(jar)
            }
            val loader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), KelmaPlugin::class.java.classLoader)
            val runtime = JarPluginRuntime(pluginId, capabilities, jarFile, loader)
            try {
                runtime.start(entrypoint)
            } catch (failure: Throwable) {
                runtime.close()
                val cause = (failure as? java.lang.reflect.InvocationTargetException)?.targetException ?: failure
                throw IllegalStateException(cause.message ?: cause::class.java.name, cause)
            }
            return runtime
        }
    }
}

private val BoundaryJson = Json { encodeDefaults = true }
private val PluginLocalId = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")
private val PluginLogLevels = setOf("debug", "info", "warn", "error")
private const val MaximumLogMessage = 2000

/** JSON → JSON-compatible Java values (Map, List, String, Long/Double, Boolean, null). */
internal fun JsonElement.toJavaValue(): Any? = when (this) {
    JsonNull -> null
    is JsonObject -> mapValues { it.value.toJavaValue() }
    is JsonArray -> map { it.toJavaValue() }
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        else -> doubleOrNull ?: error("Invalid JSON number")
    }
}

/** A plugin's JSON-compatible value → JSON; anything else is an error. */
internal fun Any?.toBoundaryElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Boolean -> JsonPrimitive(this)
    is Number -> {
        require(this !is Double || isFinite()) { "Plugin numbers must be finite" }
        require(this !is Float || isFinite()) { "Plugin numbers must be finite" }
        JsonPrimitive(this)
    }
    is CharSequence -> JsonPrimitive(toString())
    is Map<*, *> -> JsonObject(entries.associate { (key, value) ->
        require(key is String) { "Plugin object keys must be strings" }
        key to value.toBoundaryElement()
    })
    is Iterable<*> -> JsonArray(map { it.toBoundaryElement() })
    is Array<*> -> JsonArray(map { it.toBoundaryElement() })
    else -> throw IllegalArgumentException("Plugins return JSON-compatible values, not ${this::class.java.name}")
}
