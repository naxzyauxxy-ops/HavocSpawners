package dev.havoc.spawners.ui;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.spawner.BlockKey;
import dev.havoc.spawners.spawner.SpawnerData;
import dev.havoc.spawners.spawner.SpawnerItems;
import dev.havoc.spawners.util.Numbers;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-wide dialogs: spawner browser, leaderboard and price list. */
public final class AdminUi {

    private static final int PER_PAGE = 7;

    private final HavocSpawners plugin;

    public AdminUi(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    public void openList(Player player, int page, UUID ownerFilter) {
        List<SpawnerData> spawners = new ArrayList<>(
                ownerFilter == null ? plugin.spawners().all() : plugin.spawners().ownedBy(ownerFilter));
        spawners.sort(Comparator.comparingLong((SpawnerData s) -> -s.storage().totalItems()));

        int pages = Math.max(1, (spawners.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Numbers.clamp(page, 0, pages - 1);

        List<DialogBody> body = new ArrayList<>();
        body.add(Ui.text("<color:" + Ui.ACCENT + "><bold>Spawner browser</bold></color> <color:"
                + Ui.FAINT + ">page " + (current + 1) + "/" + pages + "</color>"));
        body.add(Ui.stat("Tracked spawners", Numbers.plain(spawners.size())));

        List<ActionButton> buttons = new ArrayList<>();
        int start = current * PER_PAGE;
        int end = Math.min(spawners.size(), start + PER_PAGE);
        for (int i = start; i < end; i++) {
            SpawnerData spawner = spawners.get(i);
            Material icon = plugin.lootEngine().iconFor(spawner);
            body.add(Ui.item(new ItemStack(icon == null || icon.isAir() ? Material.SPAWNER : icon),
                    "<color:" + Ui.INK + ">" + spawner.displayType() + "</color> <color:" + Ui.FAINT
                            + ">×" + spawner.stackSize() + " · " + spawner.position() + "</color>"
                            + "\n<color:" + Ui.FAINT + ">" + Numbers.compact(spawner.storage().totalItems())
                            + " items · owner " + (spawner.ownerName() == null ? "?" : spawner.ownerName())
                            + "</color>"));
            buttons.add(Ui.button("<color:" + Ui.ACCENT + ">➜ " + spawner.displayType() + "</color> "
                            + "<color:" + Ui.FAINT + ">" + spawner.position().x() + ","
                            + spawner.position().y() + "," + spawner.position().z() + "</color>",
                    "Teleport to this spawner", 190, p -> teleport(p, spawner)));
        }

        if (pages > 1) {
            buttons.add(Ui.button("<color:" + Ui.ACCENT + ">◀ Previous</color>", null, 95,
                    p -> openList(p, current - 1, ownerFilter)));
            buttons.add(Ui.button("<color:" + Ui.ACCENT + ">Next ▶</color>", null, 95,
                    p -> openList(p, current + 1, ownerFilter)));
        }
        buttons.add(Ui.button("<color:" + Ui.INK + ">🏆 Leaderboard</color>", null, 190,
                p -> openLeaderboard(p, null)));

        DialogBase base = Ui.base("Spawner browser", body, List.of(), true);
        player.showDialog(Ui.multi(base, buttons,
                Ui.button("<color:" + Ui.FAINT + ">Close</color>", null, 90, Player::closeDialog), 2));
    }

    private void teleport(Player player, SpawnerData spawner) {
        BlockKey key = spawner.position();
        Location location = key.toLocation();
        if (location == null) {
            plugin.messages().send(player, "list.world-missing");
            return;
        }
        player.closeDialog();
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(player.getLocation().getPitch());
        player.teleportAsync(location.add(0.0D, 1.0D, 0.0D));
        plugin.messages().send(player, "list.teleported");
    }

    public void openLeaderboard(Player player, UUID ownerFilter) {
        List<SpawnerData> top = plugin.analytics()
                .topEarners(ownerFilter, plugin.settings().leaderboardSize);

        List<DialogBody> body = new ArrayList<>();
        body.add(Ui.text("<color:" + Ui.ACCENT + "><bold>Top earning spawners</bold></color> <color:"
                + Ui.FAINT + ">last " + plugin.settings().analyticsHistoryHours + "h</color>"));
        if (top.isEmpty()) {
            body.add(Ui.text("<color:" + Ui.FAINT + ">No production recorded yet.</color>"));
        }
        int rank = 1;
        for (SpawnerData spawner : top) {
            String medal = switch (rank) {
                case 1 -> "<color:" + Ui.GOOD + "><bold>①</bold></color>";
                case 2 -> "<color:" + Ui.INK + ">②</color>";
                case 3 -> "<color:" + Ui.WARN + ">③</color>";
                default -> "<color:" + Ui.FAINT + ">" + rank + "</color>";
            };
            body.add(Ui.text(medal + " <color:" + Ui.INK + ">" + spawner.displayType() + "</color> "
                    + "<color:" + Ui.FAINT + ">×" + spawner.stackSize() + "</color>  "
                    + "<color:" + Ui.GOOD + ">"
                    + plugin.economy().format(plugin.analytics().moneyInWindow(spawner)) + "</color>"
                    + "  <color:" + Ui.FAINT + ">"
                    + Numbers.compact(plugin.analytics().itemsInWindow(spawner)) + " items</color>"));
            rank++;
        }

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(Ui.button("<color:" + Ui.INK + ">Only mine</color>", null, 150,
                p -> openLeaderboard(p, p.getUniqueId())));
        buttons.add(Ui.button("<color:" + Ui.INK + ">Whole server</color>", null, 150,
                p -> openLeaderboard(p, null)));

        DialogBase base = Ui.base("Leaderboard", body, List.of(), true);
        player.showDialog(Ui.multi(base, buttons,
                Ui.button("<color:" + Ui.FAINT + ">Close</color>", null, 90, Player::closeDialog), 2));
    }

    public void openPrices(Player player, int page) {
        List<Map.Entry<Material, Double>> prices = new ArrayList<>(plugin.prices().customPrices().entrySet());
        prices.sort(Comparator.comparingDouble((Map.Entry<Material, Double> e) -> -e.getValue()));

        int perPage = 10;
        int pages = Math.max(1, (prices.size() + perPage - 1) / perPage);
        int current = Numbers.clamp(page, 0, pages - 1);

        List<DialogBody> body = new ArrayList<>();
        body.add(Ui.text("<color:" + Ui.ACCENT + "><bold>Sell prices</bold></color> <color:" + Ui.FAINT
                + ">page " + (current + 1) + "/" + pages + "</color>"));
        if (!"none".equals(plugin.prices().shopName())) {
            body.add(Ui.text("<color:" + Ui.FAINT + ">Shop integration: " + plugin.prices().shopName()
                    + "</color>"));
        }
        int start = current * perPage;
        int end = Math.min(prices.size(), start + perPage);
        for (int i = start; i < end; i++) {
            Map.Entry<Material, Double> entry = prices.get(i);
            body.add(Ui.text("<color:" + Ui.INK + ">" + SpawnerItems.pretty(entry.getKey().name())
                    + "</color> <color:" + Ui.FAINT + ">·</color> <color:" + Ui.GOOD + ">"
                    + plugin.economy().format(entry.getValue()) + "</color>"));
        }

        List<ActionButton> buttons = new ArrayList<>();
        if (pages > 1) {
            buttons.add(Ui.button("<color:" + Ui.ACCENT + ">◀ Previous</color>", null, 95,
                    p -> openPrices(p, current - 1)));
            buttons.add(Ui.button("<color:" + Ui.ACCENT + ">Next ▶</color>", null, 95,
                    p -> openPrices(p, current + 1)));
        }

        DialogBase base = Ui.base("Prices", body, List.of(), true);
        player.showDialog(Ui.multi(base, buttons,
                Ui.button("<color:" + Ui.FAINT + ">Close</color>", null, 90, Player::closeDialog), 2));
    }
}
