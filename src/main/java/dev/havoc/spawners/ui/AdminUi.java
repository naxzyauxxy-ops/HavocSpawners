package dev.havoc.spawners.ui;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.spawner.BlockKey;
import dev.havoc.spawners.spawner.SpawnerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Server-wide screens: spawner browser, leaderboard and price list. */
public final class AdminUi {

    private final HavocSpawners plugin;

    public AdminUi(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    private boolean bedrock(Player player) {
        return plugin.bedrock().useForms(player);
    }

    public void openList(Player player, int page, UUID ownerFilter) {
        if (bedrock(player)) {
            plugin.bedrockUi().openList(player, page, ownerFilter);
            return;
        }
        plugin.chestUi().openList(player, page, ownerFilter);
    }

    public void openLeaderboard(Player player, UUID ownerFilter) {
        if (bedrock(player)) {
            plugin.bedrockUi().openLeaderboard(player, ownerFilter);
            return;
        }
        plugin.chestUi().openLeaderboard(player, ownerFilter);
    }

    public void openPrices(Player player, int page) {
        if (bedrock(player)) {
            plugin.bedrockUi().openPrices(player, page);
            return;
        }
        plugin.chestUi().openPrices(player, page);
    }

    /** Sends a player to a spawner, closing whatever screen they were on. */
    void teleport(Player player, SpawnerData spawner) {
        BlockKey key = spawner.position();
        Location location = key.toLocation();
        if (location == null) {
            plugin.messages().send(player, "list.world-missing");
            return;
        }
        player.closeInventory();
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(player.getLocation().getPitch());
        player.teleportAsync(location.add(0.0D, 1.0D, 0.0D));
        plugin.messages().send(player, "list.teleported");
    }
}
