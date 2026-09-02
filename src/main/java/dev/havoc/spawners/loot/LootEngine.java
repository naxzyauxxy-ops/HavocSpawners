package dev.havoc.spawners.loot;

import dev.havoc.spawners.spawner.ItemSig;
import dev.havoc.spawners.spawner.SpawnerData;
import org.bukkit.Material;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Turns "N simulated mobs" into items.
 * <p>
 * A stacked spawner can simulate tens of thousands of mobs per cycle. Rolling each mob individually
 * would burn CPU for no gameplay benefit, so above {@link #EXACT_ROLL_LIMIT} the engine switches to a
 * normal approximation of the binomial distribution. The distribution of results is the same; the cost
 * stops scaling with stack size.
 */
public final class LootEngine {

    private static final int EXACT_ROLL_LIMIT = 96;

    private final LootRegistry registry;

    public LootEngine(LootRegistry registry) {
        this.registry = registry;
    }

    public LootResult generate(SpawnerData spawner, int cycles) {
        LootResult result = new LootResult();
        if (cycles <= 0) {
            return result;
        }
        LootTable table = registry.tableFor(spawner);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        long mobs = 0L;
        for (int i = 0; i < cycles; i++) {
            int min = spawner.minMobs();
            int max = spawner.maxMobs();
            mobs += max <= min ? min : random.nextInt(min, max + 1);
        }
        if (mobs <= 0L) {
            return result;
        }

        double multiplier = Math.max(0.0D, spawner.lootMultiplier());
        result.addExp(Math.round(mobs * (double) table.exp() * multiplier));

        for (LootEntry entry : table.entries()) {
            if (entry.material() == null || entry.material().isAir()) {
                continue;
            }
            long amount = rollAmount(mobs, entry, random);
            if (multiplier != 1.0D) {
                amount = Math.round(amount * multiplier);
            }
            if (amount > 0L) {
                result.add(ItemSig.of(entry.material()), amount);
            }
        }
        return result;
    }

    private static long rollAmount(long mobs, LootEntry entry, ThreadLocalRandom random) {
        double p = Math.max(0.0D, Math.min(1.0D, entry.chance() / 100.0D));
        if (p <= 0.0D) {
            return 0L;
        }
        if (mobs <= EXACT_ROLL_LIMIT) {
            long total = 0L;
            for (long i = 0; i < mobs; i++) {
                if (p >= 1.0D || random.nextDouble() < p) {
                    total += entry.max() <= entry.min()
                            ? entry.min()
                            : random.nextInt(entry.min(), entry.max() + 1);
                }
            }
            return total;
        }
        // Normal approximation: successes ~ N(np, np(1-p)), amount ~ successes * mean(min..max).
        double mean = mobs * p;
        double variance = mobs * p * (1.0D - p);
        double successes = mean + random.nextGaussian() * Math.sqrt(Math.max(0.0D, variance));
        long count = Math.max(0L, Math.round(successes));
        if (count == 0L) {
            return 0L;
        }
        double avg = entry.averageAmount();
        double spread = Math.max(0.0D, entry.max() - entry.min()) / 2.0D;
        double jitter = spread <= 0.0D ? 0.0D
                : random.nextGaussian() * spread * Math.sqrt(count) / Math.sqrt(3.0D);
        return Math.max(0L, Math.round(count * avg + jitter));
    }

    /** How many whole cycles have elapsed since the spawner last produced. */
    public static int cyclesSince(SpawnerData spawner, long nowMillis, long maxCatchupTicks) {
        long delayMillis = spawner.spawnDelayTicks() * 50L;
        if (delayMillis <= 0L) {
            return 0;
        }
        long elapsed = nowMillis - spawner.lastSpawnMillis();
        if (elapsed < delayMillis) {
            return 0;
        }
        long capped = maxCatchupTicks > 0L ? Math.min(elapsed, maxCatchupTicks * 50L) : delayMillis;
        long cycles = capped / delayMillis;
        return (int) Math.max(1L, Math.min(cycles, 4096L));
    }

    public Material iconFor(SpawnerData spawner) {
        LootTable table = registry.tableFor(spawner);
        Material icon = table.headIcon();
        if (icon != null && !icon.isAir()) {
            return icon;
        }
        return spawner.isItemSpawner() && spawner.itemMaterial() != null
                ? spawner.itemMaterial()
                : Material.SPAWNER;
    }
}
