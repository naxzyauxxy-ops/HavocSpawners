package dev.havoc.spawners.ui;

import dev.havoc.spawners.config.Settings;

/**
 * The shared palette, and the one drawing primitive that is not an item.
 * <p>
 * Every colour the plugin draws - menu titles, button names, tooltips and chat - comes from here,
 * loaded from the {@code theme:} block of config.yml on enable and on every reload, so re-skinning
 * the plugin never needs a rebuild. The defaults are the red/white house theme.
 */
public final class Ui {

    public static String ACCENT = "#ff2b3d";
    public static String ACCENT_DIM = "#8f0f1c";
    public static String GOOD = "#ffffff";
    public static String WARN = "#ff8a95";
    public static String BAD = "#ff2b3d";
    public static String INK = "#e8e8ea";
    public static String FAINT = "#9b9ba1";

    private Ui() {
    }

    public static void applyTheme(Settings settings) {
        ACCENT = settings.themeAccent;
        ACCENT_DIM = settings.themeAccentDim;
        GOOD = settings.themeGood;
        WARN = settings.themeWarn;
        BAD = settings.themeBad;
        INK = settings.themeInk;
        FAINT = settings.themeFaint;
    }

    /**
     * A proportion bar, as MiniMessage.
     * <p>
     * Filled segments take the given colour, the remainder is drawn faint, so a tooltip can show
     * "how full is this" at a glance rather than as two numbers to compare.
     */
    public static String bar(double ratio, int width, String colour) {
        int filled = (int) Math.round(Math.max(0.0D, Math.min(1.0D, ratio)) * width);
        StringBuilder builder = new StringBuilder("<color:").append(colour).append('>');
        for (int i = 0; i < width; i++) {
            if (i == filled) {
                builder.append("</color><color:").append(FAINT).append('>');
            }
            builder.append('|');
        }
        return builder.append("</color>").toString();
    }
}
