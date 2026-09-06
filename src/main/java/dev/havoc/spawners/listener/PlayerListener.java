package dev.havoc.spawners.listener;

import dev.havoc.spawners.HavocSpawners;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Feeds the proximity cache used by the spawner tick. */
public final class PlayerListener implements Listener {

    private final HavocSpawners plugin;

    public PlayerListener(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Only whole-block movement matters for activation range.
        if (!event.hasChangedBlock()) {
            return;
        }
        plugin.spawners().tracker().update(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.spawners().tracker().update(event.getPlayer());
        convertOldItems(event.getPlayer());
    }

    /**
     * Rewrites any old spawner items the player is carrying, once, on login.
     * <p>
     * The inventory of an offline player is not reachable through the API, so sweeping online players
     * alone would always miss most of a server's stock. Doing it at login instead covers every player
     * the next time they play, without touching player data files.
     * <p>
     * Delayed a second so it lands after anything else that restores an inventory on join, and run on
     * the player's own region so it stays safe under Folia.
     */
    private void convertOldItems(org.bukkit.entity.Player player) {
        if (!plugin.settings().legacyConvertOnJoin) {
            return;
        }
        plugin.sched().regionLater(player.getLocation(), () -> {
            if (!player.isOnline()) {
                return;
            }
            var result = plugin.itemMigrator().convert(player);
            if (result.converted() > 0) {
                plugin.messages().send(player, "fixitems.updated", dev.havoc.spawners.config.Messages.of(
                        "converted", String.valueOf(result.converted())));
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.spawners().tracker().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        plugin.sched().globalLater(() -> plugin.spawners().tracker().update(event.getPlayer()), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        plugin.spawners().tracker().update(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.sched().globalLater(() -> plugin.spawners().tracker().update(event.getPlayer()), 1L);
    }
}
