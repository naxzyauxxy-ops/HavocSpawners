package dev.havoc.spawners.spawner;

import java.util.Locale;

/** How a spawner produces. */
public enum SpawnMode {

    /**
     * No mob ever exists. The drops a mob <em>would</em> have made are calculated and banked in the
     * spawner's virtual storage. This is what makes a ×26,000 stack cost nothing.
     */
    SIMULATED,

    /**
     * Real mobs are spawned into the world, exactly as a vanilla spawner would, and players kill
     * them for their own drops. Nothing is banked.
     * <p>
     * Hard-capped per cycle and by nearby entity count, because a stacked spawner asked to spawn its
     * full simulated mob count for real would take the server down.
     */
    REAL;

    public static SpawnMode of(String raw, SpawnMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String token = raw.trim().toUpperCase(Locale.ROOT);
        if (token.equals("VANILLA") || token.equals("MOBS") || token.equals("LIVE")) {
            return REAL;
        }
        if (token.equals("VIRTUAL") || token.equals("SIM")) {
            return SIMULATED;
        }
        try {
            return valueOf(token);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public String display() {
        return this == REAL ? "Real mobs" : "Simulated";
    }
}
