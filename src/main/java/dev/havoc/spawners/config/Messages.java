package dev.havoc.spawners.config;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads lang/&lt;language&gt;.yml and renders MiniMessage strings. */
public final class Messages {

    private final HavocSpawners plugin;
    private YamlConfiguration lang;
    private YamlConfiguration defaults;
    private String prefix = "<gradient:#b14dff:#6c1fd4><bold>Havoc</bold></gradient> <dark_gray>»</dark_gray> ";

    public Messages(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    public void reload(String language) {
        File dir = new File(plugin.getDataFolder(), "lang");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create the lang folder.");
        }
        File file = new File(dir, language + ".yml");
        if (!file.exists()) {
            plugin.saveResource("lang/en_US.yml", false);
            File english = new File(dir, "en_US.yml");
            if (!file.exists() && english.exists()) {
                file = english;
            }
        }
        this.lang = YamlConfiguration.loadConfiguration(file);
        try (InputStream stream = plugin.getResource("lang/en_US.yml")) {
            if (stream != null) {
                this.defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                this.lang.setDefaults(this.defaults);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not read the bundled language file: " + ex.getMessage());
        }
        this.prefix = raw("prefix", this.prefix);
    }

    public String raw(String key, String fallback) {
        String value = lang == null ? null : lang.getString(key);
        if (value == null && defaults != null) {
            value = defaults.getString(key);
        }
        return value == null ? fallback : value;
    }

    public String raw(String key) {
        return raw(key, "<red>Missing message: " + key + "</red>");
    }

    public Component get(String key, Map<String, String> placeholders) {
        return Text.mm(raw(key), placeholders);
    }

    public Component get(String key) {
        return Text.mm(raw(key));
    }

    /** Prefixed chat line. */
    public Component chat(String key, Map<String, String> placeholders) {
        return Text.mm(prefix + raw(key), placeholders);
    }

    public void send(CommandSender target, String key) {
        send(target, key, Map.of());
    }

    public void send(CommandSender target, String key, Map<String, String> placeholders) {
        target.sendMessage(chat(key, placeholders));
    }

    public String prefix() {
        return prefix;
    }

    /** Convenience placeholder builder: {@code Messages.of("amount", "12", "item", "Iron Ingot")}. */
    public static Map<String, String> of(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
