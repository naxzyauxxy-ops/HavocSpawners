package dev.havoc.spawners.feature;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.spawner.SpawnerData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rolling per-spawner production history.
 * <p>
 * Each spawner keeps a ring of hourly buckets, so "items/hour" and "earnings/hour" are real
 * measurements rather than lifetime averages, and the memory cost stays fixed no matter how long
 * the server runs.
 */
public final class Analytics {

    private static final class Ring {
        final long[] items;
        final double[] money;
        long hourEpoch;
        int cursor;

        Ring(int size, long hourEpoch) {
            this.items = new long[size];
            this.money = new double[size];
            this.hourEpoch = hourEpoch;
        }

        void advanceTo(long targetHour) {
            long steps = targetHour - hourEpoch;
            if (steps <= 0) {
                return;
            }
            if (steps >= items.length) {
                java.util.Arrays.fill(items, 0L);
                java.util.Arrays.fill(money, 0.0D);
                cursor = 0;
            } else {
                for (long i = 0; i < steps; i++) {
                    cursor = (cursor + 1) % items.length;
                    items[cursor] = 0L;
                    money[cursor] = 0.0D;
                }
            }
            hourEpoch = targetHour;
        }

        long totalItems() {
            long total = 0L;
            for (long value : items) {
                total += value;
            }
            return total;
        }

        double totalMoney() {
            double total = 0.0D;
            for (double value : money) {
                total += value;
            }
            return total;
        }
    }

    private final HavocSpawners plugin;
    private final Map<String, Ring> rings = new ConcurrentHashMap<>();

    public Analytics(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    private static long currentHour() {
        return System.currentTimeMillis() / 3_600_000L;
    }

    private Ring ring(String id) {
        int size = Math.max(1, plugin.settings().analyticsHistoryHours);
        Ring ring = rings.computeIfAbsent(id, key -> new Ring(size, currentHour()));
        if (ring.items.length != size) {
            Ring replacement = new Ring(size, currentHour());
            rings.put(id, replacement);
            return replacement;
        }
        ring.advanceTo(currentHour());
        return ring;
    }

    public void recordItems(SpawnerData spawner, long amount) {
        if (!plugin.settings().analyticsEnabled || amount <= 0L) {
            return;
        }
        Ring ring = ring(spawner.id());
        ring.items[ring.cursor] += amount;
    }

    public void recordEarnings(SpawnerData spawner, double amount) {
        if (!plugin.settings().analyticsEnabled || amount <= 0.0D) {
            return;
        }
        Ring ring = ring(spawner.id());
        ring.money[ring.cursor] += amount;
    }

    public long itemsInWindow(SpawnerData spawner) {
        Ring ring = rings.get(spawner.id());
        return ring == null ? 0L : ring.totalItems();
    }

    public double moneyInWindow(SpawnerData spawner) {
        Ring ring = rings.get(spawner.id());
        return ring == null ? 0.0D : ring.totalMoney();
    }

    public double itemsPerHour(SpawnerData spawner) {
        int hours = Math.max(1, plugin.settings().analyticsHistoryHours);
        return itemsInWindow(spawner) / (double) hours;
    }

    public double moneyPerHour(SpawnerData spawner) {
        int hours = Math.max(1, plugin.settings().analyticsHistoryHours);
        return moneyInWindow(spawner) / (double) hours;
    }

    public void forget(String spawnerId) {
        rings.remove(spawnerId);
    }

    /** Top earning spawners over the analytics window. */
    public List<SpawnerData> topEarners(UUID owner, int limit) {
        List<SpawnerData> candidates = new ArrayList<>();
        for (SpawnerData spawner : plugin.spawners().all()) {
            if (owner != null && !owner.equals(spawner.owner())) {
                continue;
            }
            candidates.add(spawner);
        }
        candidates.sort(Comparator.comparingDouble((SpawnerData s) -> -moneyInWindow(s))
                .thenComparingLong(s -> -itemsInWindow(s)));
        return candidates.subList(0, Math.min(limit, candidates.size()));
    }
}
