package dev.havoc.spawners.listener;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.config.Messages;
import dev.havoc.spawners.config.Settings;
import dev.havoc.spawners.spawner.BlockKey;
import dev.havoc.spawners.spawner.ItemSig;
import dev.havoc.spawners.spawner.SpawnerBlocks;
import dev.havoc.spawners.spawner.SpawnerData;
import dev.havoc.spawners.storage.InventoryCodec;
import dev.havoc.spawners.util.ItemThrow;
import dev.havoc.spawners.util.Numbers;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Placement, breaking and explosion handling.
 * <p>
 * Every handler runs at {@code HIGHEST} with {@code ignoreCancelled}, so any claim or region plugin
 * that cancels the event first is automatically respected without a per-plugin integration.
 */
public final class BlockListener implements Listener {

    private final HavocSpawners plugin;

    public BlockListener(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.SPAWNER) {
            return;
        }
        Player player = event.getPlayer();
        BlockKey key = BlockKey.of(block);

        if (!plugin.items().isHavocSpawner(item)) {
            // A vanilla spawner item: adopt it so the block is never left unmanaged.
            EntityType type = readBlockType(block);
            SpawnerData adopted = plugin.spawners().create(key, type, null, player, 1);
            syncBlock(block, adopted);
            plugin.messages().send(player, "spawner.placed", Messages.of(
                    "type", adopted.displayType(), "stack", "1"));
            return;
        }

        int stackSize = plugin.items().readStackSize(item);
        int level = plugin.items().readLevel(item);
        SpawnerData spawner = plugin.spawners().create(key,
                plugin.items().readEntityType(item), plugin.items().readItemMaterial(item), player, stackSize);
        spawner.level(level);
        spawner.recompute(plugin.settings(), plugin.upgrades());

        // Contents the item was carrying come straight back, bypassing the capacity check so a
        // downgraded config can never delete what a player already had.
        long restored = 0L;
        for (Map.Entry<ItemSig, Long> entry : plugin.items().readStorage(item).entrySet()) {
            spawner.storage().addUnchecked(entry.getKey(), entry.getValue());
            restored += entry.getValue();
        }
        long carriedExp = plugin.items().readStoredExp(item);
        if (carriedExp > 0L) {
            spawner.storedExp(carriedExp);
        }
        plugin.storage().queueSave(spawner);
        syncBlock(block, spawner);

        plugin.messages().send(player, restored > 0L ? "spawner.placed-restored" : "spawner.placed",
                Messages.of("type", spawner.displayType(),
                        "stack", Numbers.plain(stackSize),
                        "items", Numbers.plain(restored)));
    }

    /**
     * Stamps the spawner's real type onto the block.
     * <p>
     * Applied immediately and again a tick later, because vanilla writes the item's own block-entity
     * data around placement time and we want ours to be the version that sticks.
     */
    private void syncBlock(Block block, SpawnerData spawner) {
        SpawnerBlocks.apply(block, spawner);
        plugin.sched().regionLater(block.getLocation(), () -> SpawnerBlocks.apply(block, spawner), 1L);
    }

    private EntityType readBlockType(Block block) {
        BlockState state = block.getState(false);
        if (state instanceof CreatureSpawner creatureSpawner) {
            EntityType type = creatureSpawner.getSpawnedType();
            if (type != null) {
                return type;
            }
        }
        return EntityType.PIG;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) {
            return;
        }
        Player player = event.getPlayer();
        SpawnerData spawner = plugin.spawners().at(BlockKey.of(block));

        if (spawner == null) {
            handleNatural(event, player);
            return;
        }
        if (!plugin.settings().breakEnabled) {
            event.setCancelled(true);
            plugin.messages().send(player, "spawner.break-disabled");
            return;
        }
        if (!player.hasPermission("havocspawners.break")) {
            event.setCancelled(true);
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (plugin.dropService().isRunning(spawner) || spawner.isBusy()) {
            event.setCancelled(true);
            plugin.messages().send(player, "spawner.busy");
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!toolAllowed(tool)) {
            event.setCancelled(true);
            plugin.messages().send(player, "spawner.wrong-tool");
            return;
        }
        if (plugin.settings().silkRequired
                && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) < plugin.settings().silkLevel) {
            event.setCancelled(true);
            plugin.messages().send(player, "spawner.needs-silk");
            return;
        }

        event.setDropItems(false);
        event.setExpToDrop(0);
        damageTool(player, tool);

        long items = spawner.storage().totalItems();
        long storedExp = spawner.storedExp();
        Settings.BreakStorage mode = plugin.settings().breakStorage;

        // KEEP_IN_ITEM is the default because it is the only option that is both instant and
        // lossless: the counters ride along inside the spawner item instead of becoming thousands
        // of dropped entities.
        String payload = null;
        if (mode == Settings.BreakStorage.KEEP_IN_ITEM && items > 0) {
            payload = InventoryCodec.encode(spawner.storage().snapshot());
        }

        ItemStack give = plugin.items().create(spawner.entityType(), spawner.itemMaterial(),
                spawner.stackSize(), spawner.level(), 1, payload, 0L);
        deliver(player, List.of(give));

        if (storedExp > 0) {
            player.giveExp((int) Math.min(Integer.MAX_VALUE, storedExp), plugin.settings().allowExpMending);
        }

        boolean handingOff = false;
        if (mode == Settings.BreakStorage.DROP && items > 0) {
            // Legacy behaviour. Still metered, but this is the setting that costs TPS.
            spawner.stopped(true);
            plugin.spawners().detachPosition(spawner);
            handingOff = plugin.dropService().dropAllThen(player, spawner, () -> {
                plugin.spawners().remove(spawner);
                plugin.analytics().forget(spawner.id());
            });
        }
        if (!handingOff) {
            plugin.spawners().remove(spawner);
            plugin.analytics().forget(spawner.id());
        }

        String key = payload != null ? "spawner.broken-carried" : "spawner.broken";
        plugin.messages().send(player, key, Messages.of(
                "type", spawner.displayType(),
                "stack", Numbers.plain(spawner.stackSize()),
                "items", Numbers.plain(items)));
    }

    private void handleNatural(BlockBreakEvent event, Player player) {
        if (plugin.settings().naturalBreakable) {
            if (plugin.settings().naturalConvert) {
                event.setDropItems(false);
                EntityType type = readBlockType(event.getBlock());
                deliver(player, List.of(plugin.items().create(type, null, 1, 1, 1)));
            }
            return;
        }
        event.setCancelled(true);
        plugin.messages().send(player, "spawner.natural-protected");
    }

    private boolean toolAllowed(ItemStack tool) {
        if (plugin.settings().requiredTools.isEmpty()) {
            return true;
        }
        return tool != null && plugin.settings().requiredTools.contains(tool.getType());
    }

    private void damageTool(Player player, ItemStack tool) {
        int loss = plugin.settings().durabilityLoss;
        if (loss <= 0 || tool == null || tool.getType().getMaxDurability() <= 0) {
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        ItemMeta meta = tool.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(damageable.getDamage() + loss);
            tool.setItemMeta(meta);
        }
    }

    private void deliver(Player player, List<ItemStack> items) {
        if (plugin.settings().directToInventory) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.toArray(new ItemStack[0]));
            for (ItemStack leftover : leftovers.values()) {
                if (leftover != null) {
                    ItemThrow.deliver(player, leftover, plugin.settings());
                }
            }
            return;
        }
        for (ItemStack item : items) {
            ItemThrow.deliver(player, item, plugin.settings());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filterExplosion(event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterExplosion(event.blockList().iterator());
    }

    private void filterExplosion(Iterator<Block> iterator) {
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (block.getType() != Material.SPAWNER) {
                continue;
            }
            SpawnerData spawner = plugin.spawners().at(BlockKey.of(block));
            boolean protect = spawner == null
                    ? plugin.settings().naturalProtect
                    : plugin.settings().protectFromExplosions;
            if (protect) {
                iterator.remove();
            } else if (spawner != null) {
                plugin.spawners().remove(spawner);
                plugin.analytics().forget(spawner.id());
            }
        }
    }

    /** Stops vanilla mobs coming out of a managed spawner - the loot is virtual instead. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner state = event.getSpawner();
        if (state == null) {
            return;
        }
        SpawnerData spawner = plugin.spawners().at(BlockKey.of(state.getLocation()));
        if (spawner != null || !plugin.settings().naturalSpawnMobs) {
            event.setCancelled(true);
        }
    }
}
