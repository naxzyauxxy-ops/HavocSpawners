package dev.havoc.spawners.ui.layout;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A chest screen's button layout, read from a {@code gui_layouts/*.yml} file.
 * <p>
 * The format is the one the old plugin used - {@code slot_N} sections with a material, an action and
 * optional {@code if:} overrides - so an existing layout file can be dropped straight in and keeps
 * working. Slot numbers are 1-based, exactly as the comments in those files describe them.
 */
public final class GuiLayout {

    /** Which set of overrides applies right now. */
    public enum Condition {SELL_INTEGRATION, NO_SELL_INTEGRATION}

    private final Map<Integer, GuiButton> plain = new LinkedHashMap<>();
    private final Map<Integer, GuiButton> withSell = new LinkedHashMap<>();
    private final Map<Integer, GuiButton> withoutSell = new LinkedHashMap<>();
    private boolean skipSellConfirmation;

    /** Every configured slot for the current condition, 1-based. */
    public Map<Integer, GuiButton> slots(Condition condition) {
        Map<Integer, GuiButton> overrides =
                condition == Condition.SELL_INTEGRATION ? withSell : withoutSell;
        if (overrides.isEmpty()) {
            return plain;
        }
        Map<Integer, GuiButton> merged = new LinkedHashMap<>(plain);
        merged.putAll(overrides);
        return merged;
    }

    public boolean skipSellConfirmation() {
        return skipSellConfirmation;
    }

    public boolean isEmpty() {
        return plain.isEmpty() && withSell.isEmpty() && withoutSell.isEmpty();
    }

    /** Parses a layout file. A malformed slot is skipped rather than failing the whole screen. */
    public static GuiLayout read(YamlConfiguration config) {
        GuiLayout layout = new GuiLayout();
        layout.skipSellConfirmation = config.getBoolean("skip_sell_confirmation", false);

        for (String key : config.getKeys(false)) {
            if (!key.toLowerCase(Locale.ROOT).startsWith("slot_")) {
                continue;
            }
            int slot;
            try {
                slot = Integer.parseInt(key.substring("slot_".length()).trim());
            } catch (NumberFormatException ex) {
                continue;
            }
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            GuiButton base = button(section, null);
            if (base == null) {
                continue;
            }
            layout.plain.put(slot, base);

            ConfigurationSection conditions = section.getConfigurationSection("if");
            if (conditions == null) {
                continue;
            }
            GuiButton sell = button(conditions.getConfigurationSection("sell_integration"), base);
            if (sell != null) {
                layout.withSell.put(slot, sell);
            }
            GuiButton noSell = button(conditions.getConfigurationSection("no_sell_integration"), base);
            if (noSell != null) {
                layout.withoutSell.put(slot, noSell);
            }
        }
        return layout;
    }

    /**
     * Builds one button.
     * <p>
     * A conditional block usually only overrides part of the slot - often just the action - so
     * anything it leaves out is inherited from the unconditional definition.
     */
    private static GuiButton button(ConfigurationSection section, GuiButton inherit) {
        if (section == null) {
            return null;
        }
        Material material = Material.matchMaterial(
                section.getString("material", inherit == null ? "STONE" : inherit.material().name())
                        .toUpperCase(Locale.ROOT));
        if (material == null) {
            material = inherit == null ? Material.STONE : inherit.material();
        }
        boolean enabled = section.getBoolean("enabled", inherit == null || inherit.enabled());
        boolean info = section.getBoolean("info_button", inherit != null && inherit.infoButton());
        String click = section.getString("click", inherit == null ? null : inherit.click());
        String left = section.getString("left_click", inherit == null ? null : inherit.leftClick());
        String right = section.getString("right_click", inherit == null ? null : inherit.rightClick());
        return new GuiButton(material, enabled, info, click, left, right);
    }
}
