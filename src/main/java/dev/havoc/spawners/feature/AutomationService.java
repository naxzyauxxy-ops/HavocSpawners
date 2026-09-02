package dev.havoc.spawners.feature;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.econ.SellResult;
import dev.havoc.spawners.spawner.BlockKey;
import dev.havoc.spawners.spawner.ItemSig;
import dev.havoc.spawners.spawner.SpawnerData;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-spawner automation: auto-sell into the owner's balance and auto-collect into a linked container.
 * <p>
 * Both run on a shared interval instead of per-spawner timers, and auto-collect only ever moves a
 * bounded number of stacks per pass so a full double chest never stalls a region thread.
 */
public final class AutomationService {

    private final HavocSpawners plugin;
    private ScheduledTask task;

    public AutomationService(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.settings().automationEnabled) {
            return;
        }
        long period = Math.max(20L, plugin.settings().automationIntervalSeconds * 20L);
        task = plugin.sched().globalTimer(this::run, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void run() {
        boolean sellOn = plugin.settings().autoSellEnabled && plugin.settings().economyEnabled;
        boolean collectOn = plugin.settings().autoCollectEnabled;
        for (SpawnerData spawner : plugin.spawners().all()) {
            if (spawner.isBusy()) {
                continue;
            }
            if (collectOn && spawner.autoCollect() && spawner.linkedContainer() != null) {
                collect(spawner);
            }
            if (sellOn && spawner.autoSell() && spawner.owner() != null) {
                sell(spawner);
            }
        }
    }

    private void sell(SpawnerData spawner) {
        if (!plugin.economy().available()) {
            return;
        }
        SellResult preview = plugin.sell().preview(spawner);
        if (preview.isEmpty() || preview.net() < plugin.settings().autoSellMinValue) {
            return;
        }
        plugin.sell().sellAll(spawner, spawner.owner());
    }

    private void collect(SpawnerData spawner) {
        BlockKey key = spawner.linkedContainer();
        Location location = key.toBlockLocation();
        if (location == null || !key.isLoaded()) {
            return;
        }
        plugin.sched().region(location, () -> {
            Block block = location.getBlock();
            BlockState state = block.getState(false);
            if (!(state instanceof Container container)) {
                // The chest is gone; stop pretending the link still exists.
                spawner.linkedContainer(null);
                spawner.autoCollect(false);
                plugin.storage().queueSave(spawner);
                return;
            }
            Inventory inventory = container.getInventory();
            List<ItemStack> batch = spawner.storage().takeStacks(plugin.settings().autoCollectStacks);
            if (batch.isEmpty()) {
                return;
            }
            Map<Integer, ItemStack> leftovers = inventory.addItem(batch.toArray(new ItemStack[0]));
            if (!leftovers.isEmpty()) {
                Map<ItemSig, Long> returned = new HashMap<>();
                for (ItemStack leftover : leftovers.values()) {
                    if (leftover == null || leftover.getAmount() <= 0) {
                        continue;
                    }
                    returned.merge(ItemSig.of(leftover), (long) leftover.getAmount(), Long::sum);
                }
                for (Map.Entry<ItemSig, Long> entry : returned.entrySet()) {
                    spawner.storage().addUnchecked(entry.getKey(), entry.getValue());
                }
            }
            plugin.storage().queueSave(spawner);
        });
    }
}
