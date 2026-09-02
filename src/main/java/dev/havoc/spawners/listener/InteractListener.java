package dev.havoc.spawners.listener;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.config.Messages;
import dev.havoc.spawners.spawner.BlockKey;
import dev.havoc.spawners.spawner.SpawnerData;
import dev.havoc.spawners.util.Numbers;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/** Right-click behaviour: open the dialog, stack, change type, or finish a container link. */
public final class InteractListener implements Listener {

    private final HavocSpawners plugin;

    public InteractListener(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();

        if (plugin.pendingLink(player) != null) {
            handleLink(event, player, block);
            return;
        }
        if (block.getType() != Material.SPAWNER) {
            return;
        }
        SpawnerData spawner = plugin.spawners().at(BlockKey.of(block));
        if (spawner == null) {
            return;
        }

        ItemStack hand = event.getItem();
        if (hand != null && plugin.items().isHavocSpawner(hand)) {
            event.setCancelled(true);
            stackFromHand(player, spawner, hand);
            return;
        }
        if (hand != null && hand.getType().name().endsWith("_SPAWN_EGG")) {
            event.setCancelled(true);
            changeType(player, spawner, hand);
            return;
        }
        if (!player.hasPermission("havocspawners.use")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        event.setCancelled(true);
        plugin.spawnerUi().openMain(player, spawner);
    }

    private void handleLink(PlayerInteractEvent event, Player player, Block block) {
        SpawnerData spawner = plugin.pendingLink(player);
        plugin.clearLinking(player);
        BlockState state = block.getState(false);
        if (!(state instanceof Container)) {
            plugin.messages().send(player, "automation.link-not-container");
            return;
        }
        if (spawner == null) {
            return;
        }
        BlockKey target = BlockKey.of(block);
        if (!target.world().equals(spawner.position().world())
                || distance(target, spawner.position()) > plugin.settings().autoCollectRadius) {
            plugin.messages().send(player, "automation.link-too-far", Messages.of(
                    "radius", String.valueOf(plugin.settings().autoCollectRadius)));
            return;
        }
        event.setCancelled(true);
        spawner.linkedContainer(target);
        spawner.autoCollect(true);
        plugin.storage().queueSave(spawner);
        plugin.messages().send(player, "automation.linked", Messages.of("location", target.toString()));
        plugin.spawnerUi().openAutomation(player, spawner);
    }

    private static double distance(BlockKey a, BlockKey b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void stackFromHand(Player player, SpawnerData spawner, ItemStack hand) {
        if (!player.hasPermission("havocspawners.stack")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        boolean sameType = spawner.isItemSpawner()
                ? spawner.itemMaterial() == plugin.items().readItemMaterial(hand)
                : spawner.entityType() == plugin.items().readEntityType(hand);
        if (!sameType) {
            plugin.messages().send(player, "stack.wrong-type");
            return;
        }
        int perItem = Math.max(1, plugin.items().readStackSize(hand));
        int room = spawner.maxStackSize() - spawner.stackSize();
        if (room <= 0) {
            plugin.messages().send(player, "stack.full");
            return;
        }
        int itemsUsable = Math.min(hand.getAmount(), Math.max(1, room / perItem));
        int added = itemsUsable * perItem;
        if (added <= 0) {
            plugin.messages().send(player, "stack.full");
            return;
        }
        hand.setAmount(hand.getAmount() - itemsUsable);
        spawner.stackSize(spawner.stackSize() + added);
        spawner.recompute(plugin.settings(), plugin.upgrades());
        plugin.storage().queueSave(spawner);
        if (plugin.settings().particleStack) {
            player.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
                    spawner.position().toLocation(), 12, 0.4D, 0.4D, 0.4D, 0.0D);
        }
        plugin.messages().send(player, "stack.added", Messages.of(
                "amount", Numbers.plain(added), "total", Numbers.plain(spawner.stackSize())));
    }

    private void changeType(Player player, SpawnerData spawner, ItemStack egg) {
        if (!player.hasPermission("havocspawners.changetype")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        String raw = egg.getType().name().replace("_SPAWN_EGG", "");
        EntityType type;
        try {
            type = EntityType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.messages().send(player, "spawner.bad-egg");
            return;
        }
        if (!spawner.storage().isEmpty()) {
            plugin.messages().send(player, "spawner.empty-first");
            return;
        }
        spawner.entityType(type);
        spawner.recompute(plugin.settings(), plugin.upgrades());
        plugin.storage().queueSave(spawner);
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            egg.setAmount(egg.getAmount() - 1);
        }
        plugin.messages().send(player, "spawner.type-changed", Messages.of("type", spawner.displayType()));
    }
}
