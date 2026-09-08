package dev.havoc.spawners.ui;

import dev.havoc.spawners.HavocSpawners;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which presentation each player chose.
 * <p>
 * Kept in its own small YAML file rather than the spawner database on purpose: it is per-player, not
 * per-spawner, it is trivially small, and it must be readable even if the database is down - a
 * player should never be locked out of the menus because MySQL blinked.
 * <p>
 * A player with no entry simply follows {@code ui.mode} from config.yml, so a server that never
 * touches this feature has nothing to maintain and no file full of dead UUIDs.
 */
public final class UiPreferences {

    private final HavocSpawners plugin;
    private final Map<UUID, UiMode> chosen = new ConcurrentHashMap<>();
    private final File file;
    private volatile boolean dirty;

    public UiPreferences(HavocSpawners plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ui_modes.yml");
        load();
    }

    /** The mode this player should get: their own choice, else the server default. */
    public UiMode modeFor(Player player) {
        UiMode override = player == null ? null : chosen.get(player.getUniqueId());
        if (override != null && plugin.settings().uiAllowPlayerChoice) {
            return override;
        }
        return plugin.settings().uiMode;
    }

    /** True when this player has picked something other than the server default. */
    public boolean hasChoice(Player player) {
        return player != null && chosen.containsKey(player.getUniqueId());
    }

    public void choose(Player player, UiMode mode) {
        if (player == null) {
            return;
        }
        if (mode == null) {
            chosen.remove(player.getUniqueId());
        } else {
            chosen.put(player.getUniqueId(), mode);
        }
        dirty = true;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (String key : config.getKeys(false)) {
                try {
                    chosen.put(UUID.fromString(key), UiMode.of(config.getString(key), UiMode.DIALOG));
                } catch (IllegalArgumentException ignored) {
                    // Not a UUID - somebody hand-edited the file. Skip the row, keep the rest.
                }
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Could not read ui_modes.yml: " + ex.getMessage());
        }
    }

    /** Writes the file only when something actually changed. Safe to call on a timer or on disable. */
    public void save() {
        if (!dirty) {
            return;
        }
        dirty = false;
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, UiMode> entry : chosen.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue().name());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create the plugin folder for ui_modes.yml");
                return;
            }
            config.save(file);
        } catch (IOException ex) {
            dirty = true;
            plugin.getLogger().warning("Could not save ui_modes.yml: " + ex.getMessage());
        }
    }
}
