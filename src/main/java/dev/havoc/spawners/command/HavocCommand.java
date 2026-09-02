package dev.havoc.spawners.command;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.config.Messages;
import dev.havoc.spawners.migrate.ImportReport;
import dev.havoc.spawners.spawner.BlockKey;
import dev.havoc.spawners.spawner.SpawnerData;
import dev.havoc.spawners.ui.Ui;
import dev.havoc.spawners.util.Numbers;
import dev.havoc.spawners.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** {@code /havocspawners} - one command with every subcommand behind its own permission. */
public final class HavocCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "reload", "give", "list", "near", "prices", "top", "import", "info", "clearghosts", "stats");

    private final HavocSpawners plugin;

    public HavocCommand(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> reload(sender);
            case "give" -> give(sender, args);
            case "list" -> list(sender);
            case "near" -> near(sender, args);
            case "prices" -> prices(sender);
            case "top" -> top(sender);
            case "import" -> runImport(sender, args);
            case "info" -> info(sender);
            case "clearghosts" -> clearGhosts(sender);
            case "stats" -> stats(sender);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Text.mm("<gradient:" + Ui.ACCENT + ":" + Ui.ACCENT_DIM + "><bold>HavocSpawners</bold></gradient> "
                + "<dark_gray>v" + plugin.getPluginMeta().getVersion() + "</dark_gray>"));
        line(sender, "/hs info", "Details about the spawner you are looking at");
        line(sender, "/hs list", "Open the spawner browser");
        line(sender, "/hs near [radius]", "Find spawners around you");
        line(sender, "/hs top", "Top earning spawners");
        line(sender, "/hs prices", "Show sell prices");
        if (sender.hasPermission("havocspawners.command.give")) {
            line(sender, "/hs give [player] mob [TYPE] [amount] [stack]", "Give a mob spawner");
            line(sender, "/hs give [player] item [MATERIAL] [amount] [stack]", "Give an item spawner");
        }
        if (sender.hasPermission("havocspawners.command.import")) {
            line(sender, "/hs import (yaml | sqlite | mysql)", "Import a SmartSpawner database");
        }
        if (sender.hasPermission("havocspawners.command.reload")) {
            line(sender, "/hs reload", "Reload configuration");
            line(sender, "/hs clearghosts", "Remove spawners whose world is gone");
            line(sender, "/hs stats", "Plugin runtime statistics");
        }
    }

    private void line(CommandSender sender, String usage, String description) {
        sender.sendMessage(Text.mm("<color:" + Ui.FAINT + ">│</color> <color:" + Ui.ACCENT + ">" + usage
                + "</color> <color:" + Ui.FAINT + ">- " + description + "</color>"));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("havocspawners.command.reload")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        plugin.reloadEverything();
        plugin.messages().send(sender, "reloaded");
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("havocspawners.command.give")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 4) {
            plugin.messages().send(sender, "give.usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", Messages.of("player", args[1]));
            return;
        }
        String kind = args[2].toLowerCase(Locale.ROOT);
        int amount = args.length > 4 ? parseInt(args[4], 1) : 1;
        int stack = args.length > 5 ? parseInt(args[5], 1) : 1;
        int level = args.length > 6 ? parseInt(args[6], 1) : 1;

        ItemStack item;
        String typeName;
        if (kind.equals("item")) {
            Material material = Material.matchMaterial(args[3].toUpperCase(Locale.ROOT));
            if (material == null) {
                plugin.messages().send(sender, "give.bad-material", Messages.of("value", args[3]));
                return;
            }
            item = plugin.items().create(null, material, stack, level, amount);
            typeName = material.name();
        } else {
            EntityType type;
            try {
                type = EntityType.valueOf(args[3].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.messages().send(sender, "give.bad-type", Messages.of("value", args[3]));
                return;
            }
            item = plugin.items().create(type, null, stack, level, amount);
            typeName = type.name();
        }
        target.getInventory().addItem(item).values()
                .forEach(left -> target.getWorld().dropItem(target.getLocation(), left));
        plugin.messages().send(sender, "give.success", Messages.of(
                "player", target.getName(), "type", typeName, "amount", String.valueOf(amount)));
    }

    private void list(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        if (!player.hasPermission("havocspawners.command.list")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        plugin.adminUi().openList(player, 0, null);
    }

    private void near(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        if (!player.hasPermission("havocspawners.command.near")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        int radius = Numbers.clamp(args.length > 1 ? parseInt(args[1], 32) : 32, 1, 256);
        BlockKey origin = BlockKey.of(player.getLocation());
        List<SpawnerData> found = new ArrayList<>();
        for (SpawnerData spawner : plugin.spawners().all()) {
            BlockKey key = spawner.position();
            if (!key.world().equals(origin.world())) {
                continue;
            }
            long dx = key.x() - origin.x();
            long dy = key.y() - origin.y();
            long dz = key.z() - origin.z();
            if (dx * dx + dy * dy + dz * dz <= (long) radius * radius) {
                found.add(spawner);
            }
        }
        plugin.messages().send(player, "near.header", Messages.of(
                "count", String.valueOf(found.size()), "radius", String.valueOf(radius)));
        for (SpawnerData spawner : found) {
            player.sendMessage(Text.mm("<color:" + Ui.FAINT + ">│</color> <color:" + Ui.ACCENT + ">"
                    + spawner.displayType() + "</color> <color:" + Ui.INK + ">×" + spawner.stackSize()
                    + "</color> <color:" + Ui.FAINT + ">at " + spawner.position() + " · "
                    + Numbers.compact(spawner.storage().totalItems()) + " items</color>"));
        }
    }

    private void prices(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        plugin.adminUi().openPrices(player, 0);
    }

    private void top(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        plugin.adminUi().openLeaderboard(player, null);
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        var block = player.getTargetBlockExact(8);
        SpawnerData spawner = block == null ? null : plugin.spawners().at(BlockKey.of(block));
        if (spawner == null) {
            plugin.messages().send(player, "info.none");
            return;
        }
        plugin.spawnerUi().openMain(player, spawner);
    }

    private void clearGhosts(CommandSender sender) {
        if (!sender.hasPermission("havocspawners.command.reload")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        int removed = plugin.spawners().purgeGhosts();
        plugin.messages().send(sender, "clearghosts.done", Messages.of("count", String.valueOf(removed)));
    }

    private void stats(CommandSender sender) {
        if (!sender.hasPermission("havocspawners.command.reload")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        long items = 0L;
        for (SpawnerData spawner : plugin.spawners().all()) {
            items += spawner.storage().totalItems();
        }
        sender.sendMessage(Text.mm("<color:" + Ui.ACCENT + ">Spawners:</color> <white>"
                + Numbers.plain(plugin.spawners().size()) + "</white>"));
        sender.sendMessage(Text.mm("<color:" + Ui.ACCENT + ">Stored items:</color> <white>"
                + Numbers.plain(items) + "</white>"));
        sender.sendMessage(Text.mm("<color:" + Ui.ACCENT + ">Pending writes:</color> <white>"
                + plugin.storage().pending() + "</white>"));
        sender.sendMessage(Text.mm("<color:" + Ui.ACCENT + ">Bulk jobs running:</color> <white>"
                + plugin.dropService().activeJobs() + "</white>"));
        sender.sendMessage(Text.mm("<color:" + Ui.ACCENT + ">Storage backend:</color> <white>"
                + plugin.settings().storageMode + "</white>"));
    }

    private void runImport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("havocspawners.command.import")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "import.usage");
            return;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        plugin.messages().send(sender, "import.started", Messages.of("mode", mode));

        plugin.sched().async(() -> {
            ImportReport report;
            File folder = plugin.importer().folder();
            switch (mode) {
                case "yaml" -> report = plugin.importer().importYaml(new File(folder, "spawners_data.yml"));
                case "sqlite" -> report = plugin.importer().importSqlite(
                        new File(folder, "spawners.db"), plugin.settings().importSourceServer);
                case "mysql" -> report = plugin.importer().importMySql(
                        plugin.settings().importHost, plugin.settings().importPort,
                        plugin.settings().importDatabase, plugin.settings().importUser,
                        plugin.settings().importPassword, plugin.settings().importSourceServer);
                default -> {
                    plugin.messages().send(sender, "import.usage");
                    return;
                }
            }
            plugin.sched().global(() -> {
                plugin.messages().send(sender, "import.done", Messages.of(
                        "imported", String.valueOf(report.imported()),
                        "skipped", String.valueOf(report.skipped()),
                        "failed", String.valueOf(report.failed()),
                        "items", Numbers.plain(report.itemsMoved())));
                for (String warning : report.warnings()) {
                    sender.sendMessage(Text.mm("<color:" + Ui.WARN + ">! " + warning + "</color>"));
                }
                plugin.storage().flushSoon();
            });
        });
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("import") && args.length == 2) {
            return filter(List.of("yaml", "sqlite", "mysql"), args[1]);
        }
        if (sub.equals("give")) {
            if (args.length == 2) {
                return null;
            }
            if (args.length == 3) {
                return filter(List.of("mob", "item"), args[2]);
            }
            if (args.length == 4) {
                if (args[2].equalsIgnoreCase("item")) {
                    List<String> materials = new ArrayList<>();
                    for (Material material : Material.values()) {
                        if (material.isItem()) {
                            materials.add(material.name());
                        }
                    }
                    return filter(materials, args[3]);
                }
                List<String> types = new ArrayList<>();
                for (EntityType type : EntityType.values()) {
                    types.add(type.name());
                }
                return filter(types, args[3]);
            }
        }
        if (sub.equals("near") && args.length == 2) {
            return filter(Arrays.asList("16", "32", "64", "128"), args[1]);
        }
        return out;
    }

    private static List<String> filter(List<String> source, String prefix) {
        String needle = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : source) {
            if (value.toLowerCase(Locale.ROOT).startsWith(needle)) {
                out.add(value);
            }
            if (out.size() >= 60) {
                break;
            }
        }
        return out;
    }
}
