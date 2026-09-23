package dev.havoc.spawners.ui.layout;

import org.bukkit.Material;

/**
 * One configured slot from a {@code gui_layouts/*.yml} file.
 *
 * @param material    what to show. {@code PLAYER_HEAD} means "use the spawner's own icon", which is
 *                    what the old plugin's mob-head button did
 * @param enabled     false hides the slot entirely
 * @param infoButton  a display-only tile carrying the spawner's stats and loot list
 * @param click       the action for any click, unless a more specific one applies
 * @param leftClick   overrides {@code click} for a left click
 * @param rightClick  overrides {@code click} for a right click
 */
public record GuiButton(Material material, boolean enabled, boolean infoButton,
                        String click, String leftClick, String rightClick) {

    /** The action for a given click, falling back to the general one. */
    public String actionFor(boolean right) {
        String specific = right ? rightClick : leftClick;
        if (specific != null && !specific.isBlank()) {
            return specific;
        }
        return click == null || click.isBlank() ? "none" : click;
    }

    /** True when this slot uses the spawner's own icon rather than a fixed material. */
    public boolean usesSpawnerIcon() {
        return material == Material.PLAYER_HEAD;
    }
}
