package dev.havoc.spawners.ui;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.config.Messages;
import dev.havoc.spawners.econ.SellResult;
import dev.havoc.spawners.feature.UpgradeTier;
import dev.havoc.spawners.spawner.ItemSig;
import dev.havoc.spawners.spawner.SpawnerData;
import dev.havoc.spawners.spawner.SpawnerItems;
import dev.havoc.spawners.util.Numbers;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The same screens as {@link SpawnerUi}, rendered as native Bedrock forms.
 * <p>
 * Every action routes back through the shared {@link SpawnerUi} helpers, so Java and Bedrock players
 * get identical behaviour - only the presentation differs. Bedrock forms understand the 16 legacy
 * colour codes rather than hex, so the theme is mapped down via the {@code bedrock.colors} config.
 */
public final class BedrockUi {

    private static final int TYPES_PER_PAGE = 8;

    private final HavocSpawners plugin;

    public BedrockUi(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- palette

    private String accent() {
        return "§" + plugin.settings().bedrockAccent;
    }

    private String good() {
        return "§" + plugin.settings().bedrockGood;
    }

    private String warn() {
        return "§" + plugin.settings().bedrockWarn;
    }

    private String bad() {
        return "§" + plugin.settings().bedrockBad;
    }

    private String ink() {
        return "§" + plugin.settings().bedrockInk;
    }

    private String faint() {
        return "§" + plugin.settings().bedrockFaint;
    }

    private SpawnerUi java() {
        return plugin.spawnerUi();
    }

    // ---------------------------------------------------------------- main

    public void openMain(Player player, SpawnerData spawner) {
        UpgradeTier tier = plugin.upgrades().tier(spawner.level());
        long used = spawner.storage().usedSlots();

        StringBuilder content = new StringBuilder();
        content.append(accent()).append(spawner.displayType())
                .append(faint()).append("  x").append(Numbers.plain(spawner.stackSize())).append('\n');
        content.append(faint()).append("Tier: ").append(ink()).append(tier.name())
                .append(faint()).append(" (level ").append(spawner.level()).append(")\n");
        content.append(faint()).append("Storage: ").append(ink())
                .append(Numbers.compact(used)).append(" / ").append(Numbers.compact(spawner.maxSlots()))
                .append(" slots\n");
        content.append(faint()).append("Items: ").append(ink())
                .append(Numbers.plain(spawner.storage().totalItems()))
                .append(faint()).append(" across ").append(Numbers.plain(spawner.storage().pageCount()))
                .append(" pages\n");
        content.append(faint()).append("XP: ").append(ink()).append(Numbers.plain(spawner.storedExp()))
                .append(" / ").append(Numbers.plain(spawner.maxStoredExp())).append('\n');
        content.append(faint()).append("Status: ").append(status(spawner));

        var form = plugin.bedrock().simple(player, accent() + "Havoc Spawner", content.toString());
        form.button(accent() + "Storage\n" + faint() + "Withdraw what it made",
                p -> openStorage(p, spawner, 0));
        form.button(good() + "Claim XP\n" + faint() + Numbers.plain(spawner.storedExp()) + " stored",
                p -> java().claimExp(p, spawner));
        form.button(good() + "Sell all", p -> openSell(p, spawner));
        form.button(accent() + "Stack", p -> openStack(p, spawner));
        if (plugin.settings().upgradesEnabled) {
            form.button(warn() + "Upgrade", p -> openUpgrade(p, spawner));
        }
        if (plugin.settings().automationEnabled) {
            form.button(accent() + "Automation", p -> openAutomation(p, spawner));
        }
        if (plugin.settings().networksEnabled) {
            form.button(accent() + "Network", p -> openNetwork(p, spawner));
        }
        if (plugin.settings().analyticsEnabled) {
            form.button(ink() + "Analytics", p -> openAnalytics(p, spawner));
        }
        form.send();
    }

    private String status(SpawnerData spawner) {
        if (spawner.stopped()) {
            return bad() + "Stopped";
        }
        if (spawner.atCapacity()) {
            return warn() + "Full";
        }
        return spawner.active() ? good() + "Running" : faint() + "Idle - nobody in range";
    }

    // ---------------------------------------------------------------- storage

    public void openStorage(Player player, SpawnerData spawner, int page) {
        List<Map.Entry<ItemSig, Long>> entries = spawner.storage().orderedEntries();
        int pages = Math.max(1, (entries.size() + TYPES_PER_PAGE - 1) / TYPES_PER_PAGE);
        int current = Numbers.clamp(page, 0, pages - 1);
        long used = spawner.storage().usedSlots();

        String content = faint() + Numbers.compact(used) + " / " + Numbers.compact(spawner.maxSlots())
                + " slots  -  " + Numbers.plain(spawner.storage().pageCount()) + " pages"
                + (entries.isEmpty() ? "\n" + faint() + "Nothing stored yet." : "");

        var form = plugin.bedrock().simple(player,
                accent() + "Storage " + faint() + "(" + (current + 1) + "/" + pages + ")", content);

        int start = current * TYPES_PER_PAGE;
        int end = Math.min(entries.size(), start + TYPES_PER_PAGE);
        for (int i = start; i < end; i++) {
            Map.Entry<ItemSig, Long> entry = entries.get(i);
            ItemSig sig = entry.getKey();
            long amount = entry.getValue();
            form.button(ink() + SpawnerItems.pretty(sig.material().name())
                            + "\n" + faint() + Numbers.plain(amount),
                    p -> openItemActions(p, spawner, sig));
        }
        if (current > 0) {
            form.button(accent() + "< Previous", p -> openStorage(p, spawner, current - 1));
        }
        if (current < pages - 1) {
            form.button(accent() + "Next >", p -> openStorage(p, spawner, current + 1));
        }
        form.button(warn() + "Drop a page\n" + faint() + "45 stacks, thrown out",
                p -> java().dropOnePage(p, spawner, current));
        form.button(warn() + "Bulk withdraw", p -> openBulkDrop(p, spawner));
        form.button(good() + "Sell everything", p -> openSell(p, spawner));
        form.button(ink() + "Filters", p -> openFilters(p, spawner, 0));
        form.button(faint() + "Back", p -> openMain(p, spawner));
        form.send();
    }

    public void openItemActions(Player player, SpawnerData spawner, ItemSig sig) {
        long amount = spawner.storage().countOf(sig);
        double unit = plugin.prices().priceOf(sig.template(), plugin.settings());
        String name = SpawnerItems.pretty(sig.material().name());
        boolean filtered = spawner.filtered().contains(sig.material());

        String content = faint() + "Stored: " + ink() + Numbers.plain(amount) + "\n"
                + faint() + "Unit price: " + ink()
                + (unit > 0.0D ? plugin.economy().format(unit) : bad() + "not sellable") + "\n"
                + faint() + "Total value: " + ink()
                + (unit > 0.0D ? plugin.economy().format(unit * amount) : "-") + "\n"
                + faint() + "Filtered: " + (filtered ? bad() + "yes" : good() + "no");

        var form = plugin.bedrock().simple(player, accent() + name, content);
        form.button(accent() + "Take one stack", p -> java().withdraw(p, spawner, sig, sig.maxStack()));
        form.button(accent() + "Fill my inventory",
                p -> java().withdraw(p, spawner, sig, (long) sig.maxStack() * 36));
        form.button(warn() + "Drop all on ground", p -> {
            if (plugin.dropService().isRunning(spawner)) {
                plugin.messages().send(p, "bulk-drop.busy");
                return;
            }
            if (!plugin.dropService().dropItemToGround(p, spawner, sig,
                    () -> java().reopenStorage(p, spawner, 0))) {
                plugin.messages().send(p, "bulk-drop.nothing");
            }
        });
        if (unit > 0.0D && plugin.settings().economyEnabled) {
            form.button(good() + "Sell all " + name, p -> java().sellOne(p, spawner, sig));
        }
        form.button(filtered ? good() + "Stop filtering" : bad() + "Filter out", p -> {
            if (!spawner.filtered().remove(sig.material())) {
                spawner.filtered().add(sig.material());
            }
            spawner.markDirty();
            plugin.storage().queueSave(spawner);
            openItemActions(p, spawner, sig);
        });
        form.button(ink() + "Sort to top", p -> {
            spawner.preferredSort(sig.material());
            spawner.storage().sortPreferring(sig.material());
            plugin.storage().queueSave(spawner);
            openStorage(p, spawner, 0);
        });
        form.button(faint() + "Back", p -> openStorage(p, spawner, 0));
        form.send();
    }

    // ---------------------------------------------------------------- bulk drop

    public void openBulkDrop(Player player, SpawnerData spawner) {
        if (!player.hasPermission("havocspawners.bulkdrop")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        int pages = spawner.storage().pageCount();
        long items = spawner.storage().totalItems();

        if (pages <= 1) {
            plugin.bedrock().modal(player, accent() + "Bulk withdraw",
                            faint() + "This spawner holds " + ink() + Numbers.plain(items)
                                    + faint() + " items in a single page.")
                    .yes(good() + "Withdraw it", p -> {
                        if (!plugin.dropService().dropAll(p, spawner, false)) {
                            plugin.messages().send(p, "bulk-drop.nothing");
                        }
                    })
                    .no(faint() + "Cancel", p -> openStorage(p, spawner, 0))
                    .send();
            return;
        }

        var form = plugin.bedrock().custom(player, accent() + "Bulk withdraw");
        form.label(faint() + "Pages: " + ink() + Numbers.plain(pages) + faint()
                + "   Items: " + ink() + Numbers.plain(items) + "\n"
                + faint() + "Stacks are handed over a few per tick, so the server never stalls.");
        int from = form.slider(ink() + "First page", 1.0F, pages, 1.0F, 1.0F);
        int to = form.slider(ink() + "Last page", 1.0F, pages, 1.0F, pages);
        int inv = form.toggle(ink() + "Into my inventory instead of the ground",
                plugin.settings().preferPlayerInventory);
        form.onSubmit((p, answers) -> {
            int first = Math.round(answers.slider(from, 1.0F));
            int lastPage = Math.round(answers.slider(to, pages));
            boolean toInventory = answers.toggle(inv, plugin.settings().preferPlayerInventory);
            java().runBulk(p, spawner, first - 1, lastPage - 1, toInventory);
        });
        form.send();
    }

    // ---------------------------------------------------------------- sell

    public void openSell(Player player, SpawnerData spawner) {
        if (!player.hasPermission("havocspawners.sell")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (!plugin.settings().economyEnabled || !plugin.economy().available()) {
            plugin.messages().send(player, "economy.unavailable");
            return;
        }
        SellResult preview = plugin.sell().preview(spawner);
        StringBuilder content = new StringBuilder();
        content.append(faint()).append("Items: ").append(ink())
                .append(Numbers.plain(preview.itemsSold())).append('\n');
        content.append(faint()).append("Gross: ").append(ink())
                .append(plugin.economy().format(preview.gross())).append('\n');
        if (plugin.settings().taxPercent > 0.0D) {
            content.append(faint()).append("Tax: ").append(ink())
                    .append(plugin.economy().format(preview.tax())).append('\n');
        }
        content.append(faint()).append("You receive: ").append(good())
                .append(plugin.economy().format(preview.net()));
        if (preview.unsellableItems() > 0L) {
            content.append('\n').append(faint()).append(Numbers.plain(preview.unsellableItems()))
                    .append(" items have no price and stay put.");
        }

        plugin.bedrock().modal(player, accent() + "Confirm sale", content.toString())
                .yes(good() + "Sell for " + plugin.economy().format(preview.net()), p -> {
                    SellResult result = plugin.sell().sellAll(spawner, p.getUniqueId());
                    if (result.isEmpty()) {
                        plugin.messages().send(p, "sell.nothing");
                    } else {
                        plugin.messages().send(p, "sell.success", Messages.of(
                                "items", Numbers.plain(result.itemsSold()),
                                "money", plugin.economy().format(result.net())));
                    }
                    openMain(p, spawner);
                })
                .no(faint() + "Cancel", p -> openMain(p, spawner))
                .send();
    }

    // ---------------------------------------------------------------- stack

    public void openStack(Player player, SpawnerData spawner) {
        if (!player.hasPermission("havocspawners.stack")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        int inHand = java().countMatchingInInventory(player, spawner);
        int max = Math.max(1, Math.min(64, Math.max(inHand, spawner.stackSize())));

        var form = plugin.bedrock().custom(player, accent() + "Stack manager");
        form.label(faint() + "Stacked: " + ink() + Numbers.plain(spawner.stackSize())
                + faint() + " / " + Numbers.plain(spawner.maxStackSize()) + "\n"
                + faint() + "In your inventory: " + ink() + Numbers.plain(inHand));
        int amount = form.slider(ink() + "Amount", 1.0F, max, 1.0F, 1.0F);
        int remove = form.toggle(ink() + "Remove instead of add", false);
        form.onSubmit((p, answers) -> {
            int value = Math.round(answers.slider(amount, 1.0F));
            java().changeStack(p, spawner, answers.toggle(remove, false) ? -value : value);
        });
        form.send();
    }

    // ---------------------------------------------------------------- upgrades

    public void openUpgrade(Player player, SpawnerData spawner) {
        if (!player.hasPermission("havocspawners.upgrade")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        UpgradeTier current = plugin.upgrades().tier(spawner.level());
        UpgradeTier next = plugin.upgrades().next(spawner.level());

        String header = faint() + "Current: " + ink() + current.name()
                + faint() + " (level " + spawner.level() + ")\n"
                + faint() + "Cycle: " + ink() + Numbers.duration(spawner.spawnDelayTicks() * 50L) + "\n"
                + faint() + "Loot multiplier: " + ink() + "x" + current.lootMultiplier() + "\n"
                + faint() + "Storage: " + ink() + Numbers.plain(spawner.maxPages()) + " pages";

        if (next == null) {
            plugin.bedrock().simple(player, accent() + "Upgrades",
                            header + "\n\n" + good() + "This spawner is fully upgraded.")
                    .button(faint() + "Back", p -> openMain(p, spawner))
                    .send();
            return;
        }

        String content = header + "\n\n" + warn() + "Next: " + next.name() + "\n"
                + faint() + "Speed: " + ink() + "x" + next.delayMultiplier() + " cycle time\n"
                + faint() + "Loot: " + ink() + "x" + next.lootMultiplier() + "\n"
                + faint() + "Extra pages: " + ink() + "+" + next.bonusPages() + "\n"
                + faint() + "Cost: " + ink() + plugin.economy().format(next.cost()) + "\n"
                + faint() + "Your balance: " + ink()
                + plugin.economy().format(plugin.economy().balance(player.getUniqueId()));

        plugin.bedrock().modal(player, accent() + "Upgrades", content)
                .yes(good() + "Upgrade to " + next.name(), p -> {
                    if (!plugin.economy().available()) {
                        plugin.messages().send(p, "economy.unavailable");
                        return;
                    }
                    if (!plugin.economy().withdraw(p.getUniqueId(), next.cost())) {
                        plugin.messages().send(p, "upgrade.too-poor", Messages.of(
                                "cost", plugin.economy().format(next.cost())));
                        return;
                    }
                    spawner.level(next.level());
                    spawner.recompute(plugin.settings(), plugin.upgrades());
                    plugin.storage().queueSave(spawner);
                    plugin.messages().send(p, "upgrade.success", Messages.of(
                            "tier", next.name(), "level", String.valueOf(next.level())));
                    openUpgrade(p, spawner);
                })
                .no(faint() + "Back", p -> openMain(p, spawner))
                .send();
    }

    // ---------------------------------------------------------------- automation

    public void openAutomation(Player player, SpawnerData spawner) {
        if (!player.hasPermission("havocspawners.automation")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        String content = faint() + "Runs every " + plugin.settings().automationIntervalSeconds
                + "s, even while you are offline.\n"
                + faint() + "Auto-sell: " + (spawner.autoSell() ? good() + "on" : faint() + "off") + "\n"
                + faint() + "Auto-collect: " + (spawner.autoCollect() ? good() + "on" : faint() + "off") + "\n"
                + faint() + "Hopper below: "
                + (dev.havoc.spawners.feature.AutomationService.hasHopper(spawner)
                        ? good() + "found" : bad() + "missing") + "\n"
                + faint() + "Earned: " + ink() + plugin.economy().format(spawner.earnedMoney());

        var form = plugin.bedrock().simple(player, accent() + "Automation", content);
        form.button((spawner.autoSell() ? bad() + "Disable" : good() + "Enable") + " auto-sell", p -> {
            spawner.autoSell(!spawner.autoSell());
            plugin.storage().queueSave(spawner);
            openAutomation(p, spawner);
        });
        form.button((spawner.autoCollect() ? bad() + "Disable" : good() + "Enable") + " auto-collect", p -> {
            if (!spawner.autoCollect()
                    && !dev.havoc.spawners.feature.AutomationService.hasHopper(spawner)) {
                plugin.messages().send(p, "automation.needs-hopper");
                return;
            }
            spawner.autoCollect(!spawner.autoCollect());
            plugin.storage().queueSave(spawner);
            openAutomation(p, spawner);
        });
        form.button(faint() + "Back", p -> openMain(p, spawner));
        form.send();
    }

    // ---------------------------------------------------------------- networks

    public void openNetwork(Player player, SpawnerData spawner) {
        if (!player.hasPermission("havocspawners.network")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        List<String> names = plugin.networks().namesFor(player.getUniqueId());
        String content = faint() + "This spawner: " + ink()
                + (spawner.network() == null ? "unassigned" : spawner.network()) + "\n"
                + faint() + "Your networks: " + ink() + (names.isEmpty() ? "none" : String.join(", ", names));

        var form = plugin.bedrock().simple(player, accent() + "Networks", content);
        form.button(good() + "Create a network", p -> createNetwork(p, spawner));
        for (String name : names) {
            form.button(accent() + name + "\n" + faint() + "Assign / open / delete",
                    p -> networkActions(p, spawner, name));
        }
        if (spawner.network() != null) {
            form.button(warn() + "Remove from network", p -> {
                plugin.networks().assign(spawner, p.getUniqueId(), null);
                openNetwork(p, spawner);
            });
        }
        form.button(faint() + "Back", p -> openMain(p, spawner));
        form.send();
    }

    private void createNetwork(Player player, SpawnerData spawner) {
        var form = plugin.bedrock().custom(player, accent() + "Create network");
        int name = form.input(ink() + "Network name", "my farm", "");
        form.onSubmit((p, answers) -> {
            String created = plugin.networks().create(p.getUniqueId(), answers.input(name, ""));
            if (created == null) {
                plugin.messages().send(p, "network.create-failed");
            } else {
                plugin.messages().send(p, "network.created", Messages.of("name", created));
            }
            openNetwork(p, spawner);
        });
        form.send();
    }

    private void networkActions(Player player, SpawnerData spawner, String network) {
        plugin.bedrock().simple(player, accent() + network, faint() + "What would you like to do?")
                .button(accent() + "Assign this spawner", p -> {
                    if (!plugin.networks().assign(spawner, p.getUniqueId(), network)) {
                        plugin.messages().send(p, "network.assign-failed");
                    } else {
                        plugin.messages().send(p, "network.assigned", Messages.of("name", network));
                    }
                    openNetwork(p, spawner);
                })
                .button(ink() + "Open network", p -> openNetworkOverview(p, network, spawner))
                .button(bad() + "Delete network", p -> {
                    plugin.networks().delete(p.getUniqueId(), network);
                    plugin.messages().send(p, "network.deleted", Messages.of("name", network));
                    openNetwork(p, spawner);
                })
                .button(faint() + "Back", p -> openNetwork(p, spawner))
                .send();
    }

    public void openNetworkOverview(Player player, String network, SpawnerData origin) {
        List<SpawnerData> members = plugin.networks().members(player.getUniqueId(), network);
        long items = 0L;
        long exp = 0L;
        double value = 0.0D;
        for (SpawnerData member : members) {
            items += member.storage().totalItems();
            exp += member.storedExp();
            value += plugin.sell().preview(member).net();
        }
        final double totalValue = value;

        String content = faint() + "Spawners: " + ink() + Numbers.plain(members.size()) + "\n"
                + faint() + "Items held: " + ink() + Numbers.plain(items) + "\n"
                + faint() + "Stored XP: " + ink() + Numbers.plain(exp) + "\n"
                + faint() + "Sell value: " + good() + plugin.economy().format(totalValue);

        var form = plugin.bedrock().simple(player, accent() + "Network - " + network, content);
        form.button(good() + "Sell whole network", p -> {
            long sold = 0L;
            double earned = 0.0D;
            for (SpawnerData member : members) {
                SellResult result = plugin.sell().sellAll(member, p.getUniqueId());
                sold += result.itemsSold();
                earned += result.net();
            }
            plugin.messages().send(p, "sell.success", Messages.of(
                    "items", Numbers.plain(sold), "money", plugin.economy().format(earned)));
            openNetworkOverview(p, network, origin);
        });
        form.button(good() + "Claim all XP", p -> {
            long claimed = 0L;
            for (SpawnerData member : members) {
                claimed += member.storedExp();
                member.storedExp(0L);
                plugin.storage().queueSave(member);
            }
            if (claimed > 0L) {
                p.giveExp((int) Math.min(Integer.MAX_VALUE, claimed), plugin.settings().allowExpMending);
            }
            plugin.messages().send(p, "exp.claimed", Messages.of("exp", Numbers.plain(claimed)));
            openNetworkOverview(p, network, origin);
        });
        form.button(warn() + "Drain everything to me", p -> {
            int started = 0;
            for (SpawnerData member : members) {
                if (plugin.dropService().dropAll(p, member)) {
                    started++;
                }
            }
            plugin.messages().send(p, "network.drain-started", Messages.of(
                    "count", Numbers.plain(started)));
        });
        if (origin != null) {
            form.button(faint() + "Back", p -> openNetwork(p, origin));
        }
        form.send();
    }

    // ---------------------------------------------------------------- analytics

    public void openAnalytics(Player player, SpawnerData spawner) {
        int hours = plugin.settings().analyticsHistoryHours;
        String content = faint() + "Last " + hours + " hours\n"
                + faint() + "Items produced: " + ink()
                + Numbers.plain(plugin.analytics().itemsInWindow(spawner))
                + faint() + " (" + Numbers.compact((long) plugin.analytics().itemsPerHour(spawner))
                + "/h)\n"
                + faint() + "Earnings: " + ink()
                + plugin.economy().format(plugin.analytics().moneyInWindow(spawner))
                + faint() + " (" + plugin.economy().format(plugin.analytics().moneyPerHour(spawner))
                + "/h)\n\n"
                + faint() + "Lifetime items: " + ink() + Numbers.plain(spawner.producedItems()) + "\n"
                + faint() + "Lifetime XP: " + ink() + Numbers.plain(spawner.producedExp()) + "\n"
                + faint() + "Lifetime earnings: " + ink()
                + plugin.economy().format(spawner.earnedMoney()) + "\n"
                + faint() + "Owner: " + ink()
                + (spawner.ownerName() == null ? "unknown" : spawner.ownerName()) + "\n"
                + faint() + "Location: " + ink() + spawner.position();

        plugin.bedrock().simple(player, accent() + "Analytics", content)
                .button(ink() + "Your top spawners", p -> openLeaderboard(p, p.getUniqueId()))
                .button(faint() + "Back", p -> openMain(p, spawner))
                .send();
    }

    // ---------------------------------------------------------------- filters

    public void openFilters(Player player, SpawnerData spawner, int page) {
        List<Material> candidates = new ArrayList<>();
        for (Map.Entry<ItemSig, Long> entry : spawner.storage().orderedEntries()) {
            if (!candidates.contains(entry.getKey().material())) {
                candidates.add(entry.getKey().material());
            }
        }
        for (Material material : spawner.filtered()) {
            if (!candidates.contains(material)) {
                candidates.add(material);
            }
        }
        for (var entry : plugin.loot().tableFor(spawner).entries()) {
            if (!candidates.contains(entry.material())) {
                candidates.add(entry.material());
            }
        }

        int perPage = 10;
        int pages = Math.max(1, (candidates.size() + perPage - 1) / perPage);
        int current = Numbers.clamp(page, 0, pages - 1);

        var form = plugin.bedrock().simple(player, accent() + "Drop filters",
                faint() + "Filtered drops are discarded the moment they are made.");
        int start = current * perPage;
        int end = Math.min(candidates.size(), start + perPage);
        for (int i = start; i < end; i++) {
            Material material = candidates.get(i);
            boolean filtered = spawner.filtered().contains(material);
            form.button((filtered ? bad() + "[x] " : good() + "[v] ")
                    + SpawnerItems.pretty(material.name()), p -> {
                if (!spawner.filtered().remove(material)) {
                    spawner.filtered().add(material);
                }
                spawner.markDirty();
                plugin.storage().queueSave(spawner);
                openFilters(p, spawner, current);
            });
        }
        if (current > 0) {
            form.button(accent() + "< Previous", p -> openFilters(p, spawner, current - 1));
        }
        if (current < pages - 1) {
            form.button(accent() + "Next >", p -> openFilters(p, spawner, current + 1));
        }
        if (!spawner.filtered().isEmpty()) {
            form.button(good() + "Clear all filters", p -> {
                spawner.filtered().clear();
                spawner.markDirty();
                plugin.storage().queueSave(spawner);
                openFilters(p, spawner, 0);
            });
        }
        form.button(faint() + "Back", p -> openStorage(p, spawner, 0));
        form.send();
    }

    // ---------------------------------------------------------------- admin

    public void openList(Player player, int page, UUID ownerFilter) {
        List<SpawnerData> spawners = new ArrayList<>(
                ownerFilter == null ? plugin.spawners().all() : plugin.spawners().ownedBy(ownerFilter));
        spawners.sort((a, b) -> Long.compare(b.storage().totalItems(), a.storage().totalItems()));

        int perPage = 10;
        int pages = Math.max(1, (spawners.size() + perPage - 1) / perPage);
        int current = Numbers.clamp(page, 0, pages - 1);

        var form = plugin.bedrock().simple(player,
                accent() + "Spawner browser " + faint() + "(" + (current + 1) + "/" + pages + ")",
                faint() + "Tracked spawners: " + ink() + Numbers.plain(spawners.size()));

        int start = current * perPage;
        int end = Math.min(spawners.size(), start + perPage);
        for (int i = start; i < end; i++) {
            SpawnerData spawner = spawners.get(i);
            form.button(ink() + spawner.displayType() + faint() + " x" + spawner.stackSize()
                            + "\n" + faint() + spawner.position(),
                    p -> {
                        var location = spawner.position().toLocation();
                        if (location == null) {
                            plugin.messages().send(p, "list.world-missing");
                            return;
                        }
                        location.setYaw(p.getLocation().getYaw());
                        location.setPitch(p.getLocation().getPitch());
                        p.teleportAsync(location.add(0.0D, 1.0D, 0.0D));
                        plugin.messages().send(p, "list.teleported");
                    });
        }
        if (current > 0) {
            form.button(accent() + "< Previous", p -> openList(p, current - 1, ownerFilter));
        }
        if (current < pages - 1) {
            form.button(accent() + "Next >", p -> openList(p, current + 1, ownerFilter));
        }
        form.button(ink() + "Leaderboard", p -> openLeaderboard(p, null));
        form.send();
    }

    public void openLeaderboard(Player player, UUID ownerFilter) {
        List<SpawnerData> top = plugin.analytics()
                .topEarners(ownerFilter, plugin.settings().leaderboardSize);
        StringBuilder content = new StringBuilder();
        content.append(faint()).append("Last ").append(plugin.settings().analyticsHistoryHours)
                .append(" hours\n");
        if (top.isEmpty()) {
            content.append(faint()).append("No production recorded yet.");
        }
        int rank = 1;
        for (SpawnerData spawner : top) {
            content.append(ink()).append(rank).append(". ").append(spawner.displayType())
                    .append(faint()).append(" x").append(spawner.stackSize())
                    .append("  ").append(good())
                    .append(plugin.economy().format(plugin.analytics().moneyInWindow(spawner)))
                    .append('\n');
            rank++;
        }

        plugin.bedrock().simple(player, accent() + "Top spawners", content.toString())
                .button(ink() + "Only mine", p -> openLeaderboard(p, p.getUniqueId()))
                .button(ink() + "Whole server", p -> openLeaderboard(p, null))
                .send();
    }

    public void openPrices(Player player, int page) {
        List<Map.Entry<Material, Double>> prices =
                new ArrayList<>(plugin.prices().customPrices().entrySet());
        prices.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        int perPage = 20;
        int pages = Math.max(1, (prices.size() + perPage - 1) / perPage);
        int current = Numbers.clamp(page, 0, pages - 1);

        StringBuilder content = new StringBuilder();
        int start = current * perPage;
        int end = Math.min(prices.size(), start + perPage);
        for (int i = start; i < end; i++) {
            Map.Entry<Material, Double> entry = prices.get(i);
            content.append(ink()).append(SpawnerItems.pretty(entry.getKey().name()))
                    .append(faint()).append(" - ").append(good())
                    .append(plugin.economy().format(entry.getValue())).append('\n');
        }

        var form = plugin.bedrock().simple(player,
                accent() + "Prices " + faint() + "(" + (current + 1) + "/" + pages + ")",
                content.toString());
        if (current > 0) {
            form.button(accent() + "< Previous", p -> openPrices(p, current - 1));
        }
        if (current < pages - 1) {
            form.button(accent() + "Next >", p -> openPrices(p, current + 1));
        }
        form.send();
    }
}
