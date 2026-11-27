package tech.kelma.plugin

/**
 * The public API of a Kelma Review desktop plugin.
 *
 * A plugin is a `.jar` with a `kelma-plugin.json` manifest at its root whose `entrypoint` names
 * a public class implementing this interface with a public no-argument constructor. Kelma calls
 * [start] once when the plugin loads (at startup, after install, after enabling) and [stop]
 * before unloading it. Plugins are trusted code that runs in the app's process.
 *
 * Values crossing the boundary are JSON-compatible: `null`, `Boolean`, `Number`, `String`,
 * `List` of these, and `Map<String, *>` of these (Java sees argument maps as `Map<String, Object>`).
 */
interface KelmaPlugin {
    fun start(context: KelmaPluginContext)

    fun stop() {}
}

/** What a plugin may register. Each needs the matching capability in its manifest. */
interface KelmaPluginContext {
    /** The plugin's manifest id. */
    val pluginId: String

    /** A command in Cmd/Ctrl+K and Plugin Manager (capability `Commands`). */
    fun registerCommand(id: String, title: String, handler: KelmaCommandHandler)

    /** Kelma's events, such as `app.started` and `review.completed` (capability `Events`). */
    fun subscribe(event: String, handler: KelmaEventHandler)

    /** A card renderer the learner can assign to decks or note types (capability `Ui`). */
    fun registerRenderer(id: String, renderer: KelmaCardRenderer)

    /** A diagnostic shown in Plugin Manager: level is debug, info, warn, or error. */
    fun log(level: String, message: String)
}

fun interface KelmaCommandHandler {
    /**
     * Runs the command. [arguments] includes `kelma_context` (`screen`, and `deck_name` when a
     * deck is open). The result must be JSON-compatible.
     */
    fun invoke(arguments: @JvmSuppressWildcards Map<String, Any?>): Any?
}

fun interface KelmaEventHandler {
    fun onEvent(name: String, attributes: @JvmSuppressWildcards Map<String, Any?>)
}

fun interface KelmaCardRenderer {
    /** A pure transform of the card's complete HTML and CSS (question or answer side). */
    fun render(card: KelmaCard): KelmaCard
}

/** A card side as HTML and CSS. */
data class KelmaCard(val html: String, val css: String)
