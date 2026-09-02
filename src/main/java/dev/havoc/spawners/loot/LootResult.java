package dev.havoc.spawners.loot;

import dev.havoc.spawners.spawner.ItemSig;

import java.util.LinkedHashMap;
import java.util.Map;

/** Aggregated output of one or more spawn cycles. */
public final class LootResult {

    private final Map<ItemSig, Long> items = new LinkedHashMap<>();
    private long exp;

    public void add(ItemSig sig, long amount) {
        if (amount > 0L) {
            items.merge(sig, amount, Long::sum);
        }
    }

    public void addExp(long amount) {
        this.exp += Math.max(0L, amount);
    }

    public Map<ItemSig, Long> items() {
        return items;
    }

    public long exp() {
        return exp;
    }

    public long totalItems() {
        long total = 0L;
        for (long value : items.values()) {
            total += value;
        }
        return total;
    }

    public boolean isEmpty() {
        return items.isEmpty() && exp <= 0L;
    }
}
