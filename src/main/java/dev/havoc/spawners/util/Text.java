package dev.havoc.spawners.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** MiniMessage helpers. Every user facing string in this plugin goes through here. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component mm(String input) {
        if (input == null) {
            return Component.empty();
        }
        return MM.deserialize(input).decoration(TextDecoration.ITALIC, false);
    }

    public static Component mm(String input, Map<String, String> placeholders) {
        if (input == null) {
            return Component.empty();
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return mm(input);
        }
        List<TagResolver> resolvers = new ArrayList<>(placeholders.size());
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolvers.add(Placeholder.parsed(entry.getKey(), entry.getValue() == null ? "" : entry.getValue()));
        }
        return MM.deserialize(input, TagResolver.resolver(resolvers)).decoration(TextDecoration.ITALIC, false);
    }

    public static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Strips MiniMessage tags without rendering, used for logs and console output. */
    public static String strip(String input) {
        return input == null ? "" : MM.stripTags(input);
    }
}
