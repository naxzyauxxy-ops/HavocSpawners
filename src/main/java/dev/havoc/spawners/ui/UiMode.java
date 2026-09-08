package dev.havoc.spawners.ui;

import java.util.Locale;

/** The two presentations a Java player can be given. */
public enum UiMode {

    /** Paper's server-side dialogs: no inventory, no click-slot maths, sliders and text inputs. */
    DIALOG,

    /** A classic chest GUI, for players and servers that prefer the familiar shape. */
    MODERN;

    public static UiMode of(String raw, UiMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String token = raw.trim().toUpperCase(Locale.ROOT);
        // "chest" and "gui" are what people actually type.
        if (token.equals("CHEST") || token.equals("GUI") || token.equals("CHEST_GUI")) {
            return MODERN;
        }
        if (token.equals("DIALOGS")) {
            return DIALOG;
        }
        try {
            return valueOf(token);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public String display() {
        return this == MODERN ? "Modern (chest GUI)" : "Dialog";
    }
}
