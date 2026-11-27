package org.example.hello;

import java.util.Map;
import tech.kelma.plugin.KelmaCard;
import tech.kelma.plugin.KelmaPlugin;
import tech.kelma.plugin.KelmaPluginContext;

/** A Kelma Review desktop plugin: one command, two event observers, one card renderer. */
public final class HelloPlugin implements KelmaPlugin {
    @Override
    public void start(KelmaPluginContext context) {
        context.registerCommand("org.example.hello.greet", "Greet from a plugin", arguments -> {
            Object name = arguments.getOrDefault("name", "Kelma");
            Map<?, ?> kelma = (Map<?, ?>) arguments.get("kelma_context");
            return Map.of("message", "Hello, " + name + "!", "screen", String.valueOf(kelma.get("screen")));
        });
        context.subscribe("app.started", (event, attributes) -> context.log("info", "Received " + event));
        context.subscribe("review.completed",
            (event, attributes) -> context.log("info", "Review rating: " + attributes.get("rating")));
        context.registerRenderer("org.example.hello.border", card -> new KelmaCard(
            "<div class=\"hello-plugin\">" + card.getHtml() + "</div>",
            card.getCss() + ".hello-plugin{border:1px solid #999;border-radius:8px;padding:12px}"));
    }
}
