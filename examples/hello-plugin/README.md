# Kelma Hello plugin

A Kelma Review desktop plugin in Java. It registers one command, two lifecycle observers, and one
pure HTML/CSS card renderer.

Build it from this directory (needs a JDK 17+):

```bash
(cd ../.. && ./gradlew :shared:jvmJar)   # the Kelma Review plugin API, once
./build.sh                               # → build/kelma-hello.jar
```

Then open **Options → Plugins → Install plugin (.jar)** in Kelma Review for desktop. Kelma previews
the requested capabilities before installing. Run **Greet from a plugin** from Cmd/Ctrl+K, and
assign the border renderer to a deck or note type from Plugin Manager.

A plugin is a `.jar` with `kelma-plugin.json` at its root and a class implementing
`tech.kelma.plugin.KelmaPlugin`; see `docs/PLUGIN_SYSTEM.md`. Plugins are trusted code with the
app's access to files and network. The mobile apps have no plugins.

The example source is released under the same Apache License 2.0 as Kelma Review.
