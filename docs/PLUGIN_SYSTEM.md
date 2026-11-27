# Plugin system

## Principle

Plugins are trusted third-party code. Installing one is equivalent to installing software: a plugin
runs inside Kelma Review with the same access to files and network as the app itself. Kelma provides
capability declarations, safe mode, enable/disable, per-plugin failure attribution, and logs, but no
sandbox. Plugin Manager says so before every install.

Plugins are **desktop JARs**. Kelma Review for desktop loads them; the Android and iOS apps have no
plugins. (Earlier versions embedded a Lua 5.4 runtime on every platform; it was removed. A Lua plugin
installed before stays listed, marked as no longer running, until the learner uninstalls it.)

## Package

A plugin is an ordinary `.jar` with a `kelma-plugin.json` manifest at its root:

```json
{
  "id": "org.example.hello",
  "name": "Hello",
  "version": "1.0.0",
  "apiVersion": 1,
  "runtime": "jvm",
  "entrypoint": "org.example.hello.HelloPlugin",
  "capabilities": ["Commands", "Events", "Ui"],
  "dependencies": []
}
```

- `id`: reverse-domain, lower case. Command and renderer ids must start with `<id>.`.
- `version` and dependency `minimumVersion`: semantic `major.minor.patch`.
- `runtime` must be `jvm`; `entrypoint` names a public class implementing
  `tech.kelma.plugin.KelmaPlugin` with a public no-argument constructor, and the JAR must contain it.
- JARs are limited to 16 MiB (64 MiB expanded, 20,000 entries).

Install from **Options → Plugins → Install plugin (.jar)**. Kelma previews the manifest's
capabilities before installing and stores the JAR in its application database.

## API (`tech.kelma.plugin`, API generation 1)

```kotlin
interface KelmaPlugin {
    fun start(context: KelmaPluginContext)
    fun stop() {}
}

interface KelmaPluginContext {
    val pluginId: String
    fun registerCommand(id: String, title: String, handler: KelmaCommandHandler) // Commands
    fun subscribe(event: String, handler: KelmaEventHandler)                     // Events
    fun registerRenderer(id: String, renderer: KelmaCardRenderer)                // Ui
    fun log(level: String, message: String)  // debug, info, warn, error
}

fun interface KelmaCommandHandler { fun invoke(arguments: Map<String, Any?>): Any? }
fun interface KelmaEventHandler { fun onEvent(name: String, attributes: Map<String, Any?>) }
fun interface KelmaCardRenderer { fun render(card: KelmaCard): KelmaCard }
data class KelmaCard(val html: String, val css: String)
```

Plugins register everything in `start()`; registering requires the matching capability in the
manifest. Values crossing the boundary are JSON-compatible (`null`, `Boolean`, `Number`, `String`,
`List`, `Map<String, *>`); anything else fails the call, attributed to the plugin. Compile against the
Kelma Review desktop JAR as `compileOnly` (the API classes come from the app at run time). Java and
Kotlin plugins both work; see `examples/hello-plugin`.

- **Commands** appear in Cmd/Ctrl+K and Plugin Manager. Every command receives
  `arguments["kelma_context"]` with the current `screen` and, when a deck is open, `deck_name`
  (no note content). Screen values: `sign-in`, `deck-overview`, `review`, `decks`, `add`,
  `browse`, `options`, `plugins`, `stats`, `sync`.
- **Events**: `app.started`, and content-free `review.completed` (with `rating`).
- **Renderers** are pure HTML/CSS transforms of a card side's complete question or answer.
  Learners assign them by deck or note type in Plugin Manager; the nearest assigned deck or parent
  deck wins. Kelma validates 2 MiB HTML and 512 KiB CSS limits, caches results for the active queue,
  and falls back to the unmodified card on failure. WebViews, media, CSP, and navigation stay Kelma's.

## Runtime

Each plugin loads in a class loader of its own whose parent provides the public API. Plugins load in
dependency order (missing, too-old, and cyclic dependencies are blocked with a reason). Kelma calls
`start()` when the plugin loads (startup, install, enable, leaving safe mode) and `stop()` before
unloading it; reloading closes the class loader. A plugin that fails to start, registers without the
capability, or throws from a command, event, or renderer is marked Failed with the reason and logged;
the rest of Kelma keeps running.

## Operational requirements

- Start without plugins (safe mode).
- Attribute errors and startup time to a plugin.
- Disable a plugin without deleting its data; keep renderer assignments while it is unavailable.
- Detect dependency cycles.
- Provide logs and command inspection in Plugin Manager.

## Compatibility

The API is versioned (`apiVersion`) and evolves additively. Plugins must not depend on Kelma's
internal classes: only `tech.kelma.plugin` is public, and the rest may change in any release.
Restart-after-update is acceptable; class unloading on reload is best-effort.

## Platform limits

- Desktop (macOS, Windows, Linux): JAR plugins.
- Android and iOS: no plugins (the plugin runtime, Plugin Manager, and plugin commands are absent).
