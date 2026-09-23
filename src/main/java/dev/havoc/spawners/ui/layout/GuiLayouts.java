package dev.havoc.spawners.ui.layout;

import dev.havoc.spawners.HavocSpawners;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/** Loads and holds the chest-GUI layout files. */
public final class GuiLayouts {

    private GuiLayout storage = new GuiLayout();
    private GuiLayout sellConfirm = new GuiLayout();

    public void reload(HavocSpawners plugin) {
        storage = load(plugin, "gui_layouts/storage_gui.yml");
        sellConfirm = load(plugin, "gui_layouts/sell_confirm_gui.yml");
    }

    private GuiLayout load(HavocSpawners plugin, String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            plugin.saveResource(path, false);
        }
        try {
            GuiLayout layout = GuiLayout.read(YamlConfiguration.loadConfiguration(file));
            if (layout.isEmpty()) {
                plugin.getLogger().warning(path + " has no slot_N entries - using the built-in layout.");
                return builtIn(plugin, path);
            }
            return layout;
        } catch (Throwable ex) {
            plugin.getLogger().warning("Could not read " + path + " (" + ex.getMessage()
                    + ") - using the built-in layout.");
            return builtIn(plugin, path);
        }
    }

    /**
     * Falls back to the copy inside the jar.
     * <p>
     * A typo in a layout file should cost the admin a warning, not a screen with no buttons on it.
     */
    private GuiLayout builtIn(HavocSpawners plugin, String path) {
        try (var stream = plugin.getResource(path)) {
            if (stream == null) {
                return new GuiLayout();
            }
            return GuiLayout.read(YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Throwable ex) {
            return new GuiLayout();
        }
    }

    public GuiLayout storage() {
        return storage;
    }

    public GuiLayout sellConfirm() {
        return sellConfirm;
    }
}
