package dev.havoc.spawners.econ;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.spawner.ItemSig;
import dev.havoc.spawners.spawner.SpawnerData;

import java.util.Map;
import java.util.UUID;

/**
 * Sells spawner storage.
 * <p>
 * Pricing walks the distinct item signatures, never the individual items, so selling four million
 * bone blocks costs the same as selling four.
 */
public final class SellService {

    private final HavocSpawners plugin;

    public SellService(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    /** Values the storage without changing it. */
    public SellResult preview(SpawnerData spawner) {
        SellResult result = new SellResult();
        Map<ItemSig, Long> snapshot = spawner.storage().snapshot();
        for (Map.Entry<ItemSig, Long> entry : snapshot.entrySet()) {
            ItemSig sig = entry.getKey();
            long amount = entry.getValue();
            double unit = plugin.prices().priceOf(sig.template(), plugin.settings());
            if (unit <= 0.0D) {
                result.addUnsellable(amount);
                continue;
            }
            result.add(sig.material(), amount, unit * amount);
        }
        result.applyTax(plugin.settings().taxPercent);
        return result;
    }

    /**
     * Sells everything priced above zero and pays {@code recipient}.
     * Unsellable items stay in storage.
     */
    public SellResult sellAll(SpawnerData spawner, UUID recipient) {
        SellResult result = new SellResult();
        if (!plugin.settings().economyEnabled || !plugin.economy().available()) {
            return result;
        }
        Map<ItemSig, Long> snapshot = spawner.storage().snapshot();
        for (Map.Entry<ItemSig, Long> entry : snapshot.entrySet()) {
            ItemSig sig = entry.getKey();
            long amount = entry.getValue();
            double unit = plugin.prices().priceOf(sig.template(), plugin.settings());
            if (unit <= 0.0D) {
                result.addUnsellable(amount);
                continue;
            }
            long removed = spawner.storage().remove(sig, amount);
            if (removed > 0L) {
                result.add(sig.material(), removed, unit * removed);
            }
        }
        result.applyTax(plugin.settings().taxPercent);
        if (!result.isEmpty()) {
            plugin.economy().deposit(recipient, result.net());
            spawner.addEarnedMoney(result.net());
            spawner.markDirty();
            plugin.analytics().recordEarnings(spawner, result.net());
        }
        return result;
    }
}
