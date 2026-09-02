package dev.havoc.spawners.feature;

/** One rung of the spawner upgrade ladder. */
public record UpgradeTier(int level,
                          String name,
                          double cost,
                          double delayMultiplier,
                          double lootMultiplier,
                          int bonusPages,
                          long bonusExpCapacity,
                          int bonusRange) {

    public static UpgradeTier base() {
        return new UpgradeTier(1, "Stock", 0.0D, 1.0D, 1.0D, 0, 0L, 0);
    }
}
