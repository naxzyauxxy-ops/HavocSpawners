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
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.spawners().tracker().remove(event.getPlayer().getUniqueId());
        plugin.clearLinking(event.getPlayer());
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
