package dev.havoc.spawners.feature;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.econ.SellResult;
import dev.havoc.spawners.spawner.BlockKey;
import dev.havoc.spawners.spawner.ItemSig;
import dev.havoc.spawners.spawner.SpawnerData;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Material;
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
            if (collectOn && spawner.autoCollect()) {
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

    /** The block a spawner's hopper must occupy: directly underneath it. */
    public static BlockKey hopperKey(SpawnerData spawner) {
        BlockKey key = spawner.position();
        return new BlockKey(key.world(), key.x(), key.y() - 1, key.z());
    }

    /**
     * Whether a hopper is sitting under this spawner right now.
     * <p>
     * Only safe to call from the region that owns the spawner - which is always true from a menu the
     * player opened by clicking it.
     */
    public static boolean hasHopper(SpawnerData spawner) {
        BlockKey below = hopperKey(spawner);
        if (!below.isLoaded()) {
            return false;
        }
        Location location = below.toBlockLocation();
        return location != null && location.getBlock().getType() == Material.HOPPER;
    }

    /**
     * Pushes stored items into the hopper under the spawner.
     * <p>
     * A hopper directly below the spawner is the only accepted target - no linking, no other
     * container types, no search radius. Remove the hopper and collection simply stops.
     */
    private void collect(SpawnerData spawner) {
        BlockKey below = hopperKey(spawner);
        Location location = below.toBlockLocation();
        if (location == null || !below.isLoaded()) {
            return;
        }
        plugin.sched().region(location, () -> {
            Block block = location.getBlock();
            if (block.getType() != Material.HOPPER) {
                // No hopper: leave the toggle alone so rebuilding one resumes collection.
                return;
            }
            BlockState state = block.getState(false);
            if (!(state instanceof Container container)) {
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
