package dev.havoc.spawners.ui;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.config.Messages;
import dev.havoc.spawners.econ.SellResult;
import dev.havoc.spawners.feature.AutomationService;
import dev.havoc.spawners.feature.UpgradeTier;
import dev.havoc.spawners.spawner.ItemSig;
import dev.havoc.spawners.spawner.SpawnerData;
import dev.havoc.spawners.spawner.SpawnerItems;
import dev.havoc.spawners.util.Numbers;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The "modern" presentation: every spawner screen as a chest GUI.
 * <p>
 * This is a second skin, not a second plugin. Every button here calls the same package-private
 * action on {@link SpawnerUi} that the dialog button calls, so the two presentations cannot drift:
 * a fix to withdrawing, selling or stacking lands in both at once. Only the layout differs.
 * <p>
 * Where a chest genuinely cannot do what a dialog does - free text, and a slider for an arbitrary
 * number - it is replaced rather than dropped: preset amount buttons instead of a slider, and a
 * chat prompt instead of a text field.
 */
public final class ChestUi {

    /** Storage rows available for item icons: 5 rows of 9, leaving the bottom row for controls. */
    private static final int ITEMS_PER_PAGE = 45;

    private final HavocSpawners plugin;

    public ChestUi(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    private SpawnerUi dialogs() {
        return plugin.spawnerUi();
    }

    // ------------------------------------------------------------------ main

    public void openMain(Player player, SpawnerData spawner) {
        Menu menu = new Menu("<color:" + Ui.ACCENT + ">Havoc Spawner</color> <color:" + Ui.FAINT + ">· "
                + spawner.displayType() + "</color>", 5, true);

        UpgradeTier tier = plugin.upgrades().tier(spawner.level());
        long used = spawner.storage().usedSlots();
        menu.set(4, Menu.decorate(dialogs().iconStack(spawner),
                "<color:" + Ui.ACCENT + "><bold>" + spawner.displayType() + "</bold></color> "
                        + "<color:" + Ui.FAINT + ">×" + Numbers.plain(spawner.stackSize()) + "</color>",
                stat("Tier", tier.name() + " (level " + spawner.level() + ")"),
                stat("Storage", Numbers.compact(used) + " / " + Numbers.compact(spawner.maxSlots()) + " slots"),
                stat("Items held", Numbers.plain(spawner.storage().totalItems())
                        + " across " + Numbers.plain(spawner.storage().pageCount()) + " pages"),
                stat("Experience", Numbers.plain(spawner.storedExp()) + " / "
                        + Numbers.plain(spawner.maxStoredExp())),
                stat("Cycle", Numbers.duration(spawner.spawnDelayTicks() * 50L)
                        + " · " + spawner.minMobs() + "-" + spawner.maxMobs() + " per cycle"),
                stat("Status", dialogs().statusLine(spawner))));

        menu.set(19, Menu.icon(Material.CHEST, "<color:" + Ui.ACCENT + ">Storage</color>",
                        hint("Browse and withdraw what this spawner produced")),
                p -> openStorage(p, spawner, 0));
        menu.set(20, Menu.icon(Material.EXPERIENCE_BOTTLE, "<color:" + Ui.GOOD + ">Claim XP</color>",
                        stat("Stored", Numbers.plain(spawner.storedExp())), hint("Click to take it")),
                p -> dialogs().claimExp(p, spawner));
        menu.set(21, Menu.icon(Material.GOLD_INGOT, "<color:" + Ui.GOOD + ">Sell all</color>",
                        hint("Sell everything in storage")),
                p -> openSell(p, spawner));
        menu.set(22, Menu.icon(Material.SPAWNER, "<color:" + Ui.ACCENT + ">Stack</color>",
                        stat("Stacked", Numbers.plain(spawner.stackSize()) + " / "
                                + Numbers.plain(spawner.maxStackSize())),
                        hint("Add or remove stacked spawners")),
                p -> openStack(p, spawner));

        int slot = 23;
        if (plugin.settings().upgradesEnabled) {
            menu.set(slot++, Menu.icon(Material.ANVIL, "<color:" + Ui.WARN + ">Upgrade</color>",
                            hint("Spend money to make this spawner better")),
                    p -> openUpgrade(p, spawner));
        }
        if (plugin.settings().automationEnabled) {
            menu.set(slot++, Menu.icon(Material.HOPPER, "<color:" + Ui.ACCENT + ">Automation</color>",
                            stat("Auto-sell", onOff(spawner.autoSell())),
                            stat("Auto-collect", onOff(spawner.autoCollect()))),
                    p -> openAutomation(p, spawner));
        }
        if (plugin.settings().networksEnabled) {
            menu.set(slot++, Menu.icon(Material.CHAIN, "<color:" + Ui.ACCENT + ">Network</color>",
                            stat("This spawner", spawner.network() == null ? "unassigned" : spawner.network())),
                    p -> openNetwork(p, spawner));
        }
        if (plugin.settings().analyticsEnabled) {
            menu.set(slot, Menu.icon(Material.PAPER, "<color:" + Ui.INK + ">Analytics</color>",
                            stat("Items/h", Numbers.compact((long) plugin.analytics().itemsPerHour(spawner))),
                            stat("Earnings/h", plugin.economy().format(
                                    plugin.analytics().moneyPerHour(spawner)))),
                    p -> openAnalytics(p, spawner));
        }

        menu.set(37, Menu.icon(spawner.stopped() ? Material.LIME_DYE : Material.REDSTONE_TORCH,
                        spawner.stopped()
                                ? "<color:" + Ui.GOOD + ">Turn on</color>"
                                : "<color:" + Ui.BAD + ">Turn off</color>",
                        stat("Currently", spawner.stopped() ? "stopped" : "running"),
                        hint(spawner.stopped()
                                ? "Start producing again - nothing stored is lost"
                                : "Stop producing. Storage is kept either way")),
                p -> {
                    dialogs().toggleRunning(p, spawner);
                    openMain(p, spawner);
                });
        if (plugin.settings().uiAllowPlayerChoice) {
            menu.set(43, Menu.icon(Material.ITEM_FRAME, "<color:" + Ui.FAINT + ">Menu style</color>",
                            stat("Currently", plugin.uiModes().modeFor(player).display()),
                            hint("Click to switch to the dialog menus")),
                    p -> dialogs().switchMode(p, spawner));
        }
        menu.set(40, close(), Player::closeInventory);
        menu.open(player);
    }

    // ------------------------------------------------------------------ storage

    public void openStorage(Player player, SpawnerData spawner, int page) {
        List<Map.Entry<ItemSig, Long>> entries = spawner.storage().orderedEntries();
        int pages = Math.max(1, (entries.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        int current = Numbers.clamp(page, 0, pages - 1);
        long used = spawner.storage().usedSlots();

        Menu menu = new Menu("<color:" + Ui.ACCENT + ">Storage</color> <color:" + Ui.FAINT + ">· page "
                + (current + 1) + "/" + pages + "</color>", 6, false);

        int start = current * ITEMS_PER_PAGE;
        int end = Math.min(entries.size(), start + ITEMS_PER_PAGE);
        for (int i = start; i < end; i++) {
            Map.Entry<ItemSig, Long> entry = entries.get(i);
            ItemSig sig = entry.getKey();
            long amount = entry.getValue();
            long stacks = (amount + sig.maxStack() - 1L) / sig.maxStack();
            double share = used <= 0L ? 0.0D : (double) stacks / (double) used;
            double unit = plugin.prices().priceOf(sig.template(), plugin.settings());

            // The icon is the real item, so it keeps its enchantments, name and model.
            ItemStack display = sig.copy(Math.min(sig.maxStack(), (int) Math.min(amount, 64)));
            menu.set(i - start, Menu.decorate(display,
                            "<color:" + Ui.INK + ">" + SpawnerItems.pretty(sig.material().name())
                                    + "</color> <color:" + Ui.FAINT + ">×</color> <color:" + Ui.ACCENT + ">"
                                    + Numbers.plain(amount) + "</color>",
                            stat("Stacks", Numbers.plain(stacks) + " · " + Numbers.percent(share) + " of storage"),
                            stat("Value", unit > 0.0D
                                    ? plugin.economy().format(unit * amount) : "unsellable"),
                            spawner.filtered().contains(sig.material())
                                    ? "<color:" + Ui.BAD + ">Filtered - new drops discarded</color>" : null,
                            hint("Click for withdraw, sell and filter")),
                    p -> openItemActions(p, spawner, sig));
        }

        menu.set(45, Menu.icon(Material.BOOK, "<color:" + Ui.ACCENT + ">Storage</color>",
                stat("Used", Numbers.compact(used) + " / " + Numbers.compact(spawner.maxSlots()) + " slots"),
                stat("Items", Numbers.plain(spawner.storage().totalItems())),
                stat("Pages", Numbers.plain(spawner.storage().pageCount()))));

        if (current > 0) {
            menu.set(46, Menu.icon(Material.ARROW, "<color:" + Ui.ACCENT + ">Previous page</color>"),
                    p -> openStorage(p, spawner, current - 1));
        }
        if (current < pages - 1) {
            menu.set(47, Menu.icon(Material.ARROW, "<color:" + Ui.ACCENT + ">Next page</color>"),
                    p -> openStorage(p, spawner, current + 1));
        }

        menu.set(49, Menu.icon(Material.DROPPER, "<color:" + Ui.WARN + ">Drop a page</color>",
                        hint("Throws 45 stacks where you are looking"),
                        hint("The screen stays open, so you can keep going")),
                p -> dialogs().dropOnePage(p, spawner, current));
        menu.set(50, Menu.icon(Material.MINECART, "<color:" + Ui.WARN + ">Bulk withdraw</color>",
                        hint("Empty many storage pages at once")),
                p -> openBulkDrop(p, spawner));
        menu.set(51, Menu.icon(Material.GOLD_INGOT, "<color:" + Ui.GOOD + ">Sell everything</color>"),
                p -> openSell(p, spawner));
        menu.set(52, Menu.icon(Material.HOPPER, "<color:" + Ui.INK + ">Filters</color>",
                        stat("Filtered", spawner.filtered().isEmpty()
                                ? "nothing" : Numbers.plain(spawner.filtered().size()) + " materials")),
                p -> openFilters(p, spawner, 0));
        menu.set(53, back(), p -> openMain(p, spawner));
        menu.open(player);
    }

    public void openItemActions(Player player, SpawnerData spawner, ItemSig sig) {
        long amount = spawner.storage().countOf(sig);
        double unit = plugin.prices().priceOf(sig.template(), plugin.settings());
        String name = SpawnerItems.pretty(sig.material().name());
        boolean filtered = spawner.filtered().contains(sig.material());

        Menu menu = new Menu("<color:" + Ui.ACCENT + ">" + name + "</color>", 4, true);
        menu.set(4, Menu.decorate(sig.copy(Math.min(sig.maxStack(), 64)),
                "<color:" + Ui.ACCENT + "><bold>" + name + "</bold></color>",
                stat("Stored", Numbers.plain(amount)),
                stat("Unit price", unit > 0.0D ? plugin.economy().format(unit) : "not sellable"),
                unit > 0.0D ? stat("Total value", plugin.economy().format(unit * amount)) : null));

        menu.set(19, Menu.icon(Material.CHEST_MINECART, "<color:" + Ui.ACCENT + ">Take one stack</color>",
                        stat("Amount", Numbers.plain(Math.min(amount, sig.maxStack())))),
                p -> dialogs().withdraw(p, spawner, sig, sig.maxStack()));
        menu.set(20, Menu.icon(Material.CHEST, "<color:" + Ui.ACCENT + ">Fill my inventory</color>"),
                p -> dialogs().withdraw(p, spawner, sig, (long) sig.maxStack() * 36));
        menu.set(21, Menu.icon(Material.DROPPER, "<color:" + Ui.WARN + ">Drop all on ground</color>",
                        hint("Throws every " + name + " where you are looking")),
                p -> dialogs().dropItemOnGround(p, spawner, sig, () -> openStorage(p, spawner, 0)));
        if (unit > 0.0D && plugin.settings().economyEnabled) {
            menu.set(22, Menu.icon(Material.GOLD_INGOT, "<color:" + Ui.GOOD + ">Sell all " + name + "</color>",
                            stat("You receive", plugin.economy().format(unit * amount))),
                    p -> dialogs().sellOne(p, spawner, sig));
        }
        menu.set(23, Menu.icon(filtered ? Material.LIME_DYE : Material.GRAY_DYE,
                        filtered ? "<color:" + Ui.GOOD + ">Stop filtering</color>"
                                : "<color:" + Ui.BAD + ">Filter out</color>",
                        hint("Filtered drops are never stored")),
                p -> {
                    dialogs().toggleFilter(spawner, sig.material());
                    openItemActions(p, spawner, sig);
                });
        menu.set(24, Menu.icon(Material.COMPARATOR, "<color:" + Ui.INK + ">Sort to top</color>",
                        hint("Show this item first in storage")),
                p -> {
                    dialogs().sortToTop(spawner, sig.material());
                    openStorage(p, spawner, 0);
                });

        menu.set(31, back(), p -> openStorage(p, spawner, 0));
        menu.open(player);
    }

    // ------------------------------------------------------------------ bulk drop

    /**
     * The chest replacement for the slider version.
     * <p>
     * A chest cannot ask for an arbitrary page range, so it offers the ranges players actually use -
     * one page, five, twenty, everything - starting from the first page. Anything larger than the
     * spawner holds is clamped by the drop service, so the buttons never need hiding.
     */
    public void openBulkDrop(Player player, SpawnerData spawner) {
        if (!player.hasPermission("havocspawners.bulkdrop")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        int pages = spawner.storage().pageCount();
        boolean toInventory = plugin.settings().preferPlayerInventory;

        Menu menu = new Menu("<color:" + Ui.WARN + ">Bulk withdraw</color>", 4, true);
        menu.set(4, Menu.icon(Material.MINECART, "<color:" + Ui.WARN + "><bold>Bulk withdraw</bold></color>",
                stat("Pages held", Numbers.plain(pages)),
                stat("Items held", Numbers.plain(spawner.storage().totalItems())),
                stat("Delivery", plugin.settings().stacksPerTick + " stacks/tick"),
                plugin.dropService().isRunning(spawner)
                        ? "<color:" + Ui.BAD + ">A withdrawal is already running</color>" : null));

        int[] counts = {1, 5, 20};
        int[] slots = {19, 20, 21};
        for (int i = 0; i < counts.length; i++) {
            int count = counts[i];
            menu.set(slots[i], Menu.icon(Material.DROPPER,
                            "<color:" + Ui.WARN + ">Withdraw " + count + (count == 1 ? " page" : " pages")
                                    + "</color>",
                            hint("Starts from the first page")),
                    p -> dialogs().runBulk(p, spawner, 0, count - 1, toInventory));
        }
        menu.set(22, Menu.icon(Material.TNT, "<color:" + Ui.BAD + ">Withdraw everything</color>",
                        stat("Items", Numbers.plain(spawner.storage().totalItems()))),
                p -> {
                    p.closeInventory();
                    if (plugin.dropService().isRunning(spawner)) {
                        plugin.messages().send(p, "bulk-drop.busy");
                        return;
                    }
                    if (!plugin.dropService().dropAll(p, spawner, toInventory)) {
                        plugin.messages().send(p, "bulk-drop.nothing");
                        return;
                    }
                    plugin.messages().send(p, "bulk-drop.started",
                            Messages.of("pages", Numbers.plain(pages)));
                });
        menu.set(24, Menu.icon(toInventory ? Material.CHEST : Material.DROPPER,
                        "<color:" + Ui.INK + ">Delivery: "
                                + (toInventory ? "my inventory" : "the ground") + "</color>",
                        hint("Set by prefer-player-inventory in config.yml")));

        menu.set(31, back(), p -> openStorage(p, spawner, 0));
        menu.open(player);
    }

    // ------------------------------------------------------------------ sell

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

        Menu menu = new Menu("<color:" + Ui.GOOD + ">Confirm sale</color>", 3, true);
        List<String> lore = new ArrayList<>();
        lore.add(stat("Items", Numbers.plain(preview.itemsSold())));
        lore.add(stat("Gross", plugin.economy().format(preview.gross())));
        if (plugin.settings().taxPercent > 0.0D) {
            lore.add(stat("Tax", plugin.economy().format(preview.tax())));
        }
        lore.add(stat("You receive", plugin.economy().format(preview.net())));
        if (preview.unsellableItems() > 0L) {
            lore.add(hint(Numbers.plain(preview.unsellableItems()) + " items have no price and stay"));
        }
        menu.set(4, Menu.icon(Material.GOLD_BLOCK, "<color:" + Ui.GOOD + "><bold>Sell storage</bold></color>",
                lore.toArray(new String[0])));

        menu.set(11, Menu.icon(Material.LIME_CONCRETE, "<color:" + Ui.GOOD + ">Sell for "
                        + plugin.economy().format(preview.net()) + "</color>"),
                p -> {
                    dialogs().sellAll(p, spawner);
                    openMain(p, spawner);
                });
        menu.set(15, Menu.icon(Material.RED_CONCRETE, "<color:" + Ui.FAINT + ">Cancel</color>"),
                p -> openMain(p, spawner));
        menu.open(player);
    }

    // ------------------------------------------------------------------ stack

    public void openStack(Player player, SpawnerData spawner) {
        if (!player.hasPermission("havocspawners.stack")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        int inHand = dialogs().countMatchingInInventory(player, spawner);

        Menu menu = new Menu("<color:" + Ui.ACCENT + ">Stack manager</color>", 4, true);
        menu.set(4, Menu.decorate(dialogs().iconStack(spawner),
                "<color:" + Ui.ACCENT + "><bold>" + spawner.displayType() + "</bold></color>",
                stat("Stacked", Numbers.plain(spawner.stackSize()) + " / "
                        + Numbers.plain(spawner.maxStackSize())),
                stat("In your inventory", Numbers.plain(inHand) + " matching spawners"),
                hint("Stacking multiplies simulation and storage")));

        int[] amounts = {1, 8, 64};
        int[] addSlots = {19, 20, 21};
        int[] removeSlots = {23, 24, 25};
        for (int i = 0; i < amounts.length; i++) {
            int amount = amounts[i];
            menu.set(addSlots[i], Menu.icon(Material.LIME_DYE,
                            "<color:" + Ui.GOOD + ">Add " + amount + "</color>",
                            hint("Takes matching spawners from your inventory")),
                    p -> dialogs().changeStack(p, spawner, amount));
            menu.set(removeSlots[i], Menu.icon(Material.RED_DYE,
                            "<color:" + Ui.WARN + ">Remove " + amount + "</color>",
                            hint("Gives them back as items")),
                    p -> dialogs().changeStack(p, spawner, -amount));
        }
        menu.set(22, Menu.icon(Material.SPAWNER, "<color:" + Ui.ACCENT + ">Add everything</color>",
                        stat("Available", Numbers.plain(inHand))),
                p -> dialogs().changeStack(p, spawner,
                        Math.max(1, dialogs().countMatchingInInventory(p, spawner))));

        menu.set(31, back(), p -> openMain(p, spawner));
        menu.open(player);
    }

    // ------------------------------------------------------------------ upgrades

    public void openUpgrade(Player player, SpawnerData spawner) {
        if (!player.hasPermission("havocspawners.upgrade")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        UpgradeTier current = plugin.upgrades().tier(spawner.level());
        UpgradeTier next = plugin.upgrades().next(spawner.level());

        Menu menu = new Menu("<color:" + Ui.WARN + ">Upgrades</color>", 3, true);
        menu.set(2, Menu.decorate(dialogs().iconStack(spawner),
                "<color:" + Ui.ACCENT + "><bold>" + current.name() + "</bold></color>",
                stat("Level", String.valueOf(spawner.level())),
                stat("Cycle time", Numbers.duration(spawner.spawnDelayTicks() * 50L)),
                stat("Loot multiplier", "×" + current.lootMultiplier()),
                stat("Storage", Numbers.plain(spawner.maxPages()) + " pages"),
                stat("XP capacity", Numbers.plain(spawner.maxStoredExp()))));

        if (next == null) {
            menu.set(6, Menu.icon(Material.NETHER_STAR,
                    "<color:" + Ui.GOOD + ">Fully upgraded</color>"));
        } else {
            menu.set(6, Menu.icon(Material.ANVIL, "<color:" + Ui.WARN + ">Next: " + next.name() + "</color>",
                            stat("Speed", "×" + next.delayMultiplier() + " cycle time"),
                            stat("Loot", "×" + next.lootMultiplier()),
                            stat("Extra pages", "+" + next.bonusPages()),
                            stat("Extra XP cap", "+" + Numbers.plain(next.bonusExpCapacity())),
                            stat("Cost", plugin.economy().format(next.cost())),
                            stat("Your balance", plugin.economy().format(
                                    plugin.economy().balance(player.getUniqueId()))),
                            hint("Click to upgrade")),
                    p -> {
                        dialogs().buyUpgrade(p, spawner, next);
                        openUpgrade(p, spawner);
                    });
        }
        menu.set(22, back(), p -> openMain(p, spawner));
        menu.open(player);
    }

    // ------------------------------------------------------------------ automation

    public void openAutomation(Player player, SpawnerData spawner) {
        if (!player.hasPermission("havocspawners.automation")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        boolean hopper = AutomationService.hasHopper(spawner);

        Menu menu = new Menu("<color:" + Ui.ACCENT + ">Automation</color>", 3, true);
        menu.set(4, Menu.icon(Material.REDSTONE, "<color:" + Ui.ACCENT + "><bold>Automation</bold></color>",
                hint("Runs every " + plugin.settings().automationIntervalSeconds
                        + "s, even while you are offline"),
                stat("Earned so far", plugin.economy().format(spawner.earnedMoney()))));

        menu.set(11, Menu.icon(spawner.autoSell() ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                        (spawner.autoSell() ? "<color:" + Ui.BAD + ">Disable" : "<color:" + Ui.GOOD + ">Enable")
                                + " auto-sell</color>",
                        stat("Currently", onOff(spawner.autoSell())),
                        hint("Sells priced drops straight into your balance")),
                p -> {
                    spawner.autoSell(!spawner.autoSell());
                    plugin.storage().queueSave(spawner);
                    openAutomation(p, spawner);
                });
        menu.set(15, Menu.icon(hopper ? Material.HOPPER : Material.BARRIER,
                        (spawner.autoCollect() ? "<color:" + Ui.BAD + ">Disable" : "<color:" + Ui.GOOD + ">Enable")
                                + " auto-collect</color>",
                        stat("Currently", onOff(spawner.autoCollect())),
                        stat("Hopper below", hopper ? "found" : "missing"),
                        hint("Feeds a hopper directly under the spawner - nothing else")),
                p -> {
                    if (!spawner.autoCollect() && !AutomationService.hasHopper(spawner)) {
                        plugin.messages().send(p, "automation.needs-hopper");
                        return;
                    }
                    spawner.autoCollect(!spawner.autoCollect());
                    plugin.storage().queueSave(spawner);
                    openAutomation(p, spawner);
                });
        menu.set(22, back(), p -> openMain(p, spawner));
        menu.open(player);
    }

    // ------------------------------------------------------------------ networks

    public void openNetwork(Player player, SpawnerData spawner) {
        if (!player.hasPermission("havocspawners.network")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        List<String> names = plugin.networks().namesFor(player.getUniqueId());

        Menu menu = new Menu("<color:" + Ui.ACCENT + ">Networks</color>", 4, true);
        menu.set(4, Menu.icon(Material.CHAIN, "<color:" + Ui.ACCENT + "><bold>Spawner networks</bold></color>",
                hint("Group spawners so one button sells or drains all of them"),
                stat("This spawner", spawner.network() == null ? "unassigned" : spawner.network()),
                stat("Your networks", names.isEmpty() ? "none" : String.join(", ", names))));

        int slot = 18;
        for (String name : names) {
            if (slot > 26) {
                break;
            }
            boolean assigned = name.equalsIgnoreCase(spawner.network());
            menu.set(slot++, Menu.icon(assigned ? Material.LIME_BANNER : Material.WHITE_BANNER,
                            "<color:" + Ui.ACCENT + ">" + name + "</color>",
                            stat("Spawners", Numbers.plain(
                                    plugin.networks().members(player.getUniqueId(), name).size())),
                            assigned ? "<color:" + Ui.GOOD + ">This spawner is in it</color>" : null,
                            hint("Left click: open  ·  Right click: assign this spawner")),
                    p -> openNetworkOverview(p, name, spawner));
        }

        // A chest has no text field, so naming a network happens in chat instead.
        menu.set(29, Menu.icon(Material.NAME_TAG, "<color:" + Ui.GOOD + ">Create a network</color>",
                        hint("Closes this menu and asks you to type a name")),
                p -> plugin.chatPrompt().ask(p,
                        "<color:" + Ui.ACCENT + ">Type a name for the new network.</color>",
                        (typer, name) -> {
                            String created = plugin.networks().create(typer.getUniqueId(), name);
                            if (created == null) {
                                plugin.messages().send(typer, "network.create-failed");
                            } else {
                                plugin.messages().send(typer, "network.created",
                                        Messages.of("name", created));
                            }
                            openNetwork(typer, spawner);
                        }));
        if (!names.isEmpty()) {
            menu.set(31, Menu.icon(Material.LEAD, "<color:" + Ui.ACCENT + ">Assign this spawner</color>",
                            hint("Closes this menu and asks which network")),
                    p -> plugin.chatPrompt().ask(p,
                            "<color:" + Ui.ACCENT + ">Type the network name: <color:" + Ui.INK + ">"
                                    + String.join(", ", names) + "</color></color>",
                            (typer, name) -> {
                                if (!plugin.networks().assign(spawner, typer.getUniqueId(), name)) {
                                    plugin.messages().send(typer, "network.assign-failed");
                                } else {
                                    plugin.messages().send(typer, "network.assigned",
                                            Messages.of("name", name));
                                }
                                openNetwork(typer, spawner);
                            }));
        }
        if (spawner.network() != null) {
            menu.set(33, Menu.icon(Material.SHEARS, "<color:" + Ui.WARN + ">Remove from network</color>"),
                    p -> {
                        plugin.networks().assign(spawner, p.getUniqueId(), null);
                        openNetwork(p, spawner);
                    });
        }
        menu.set(35, back(), p -> openMain(p, spawner));
        menu.open(player);
    }

    public void openNetworkOverview(Player player, String network, SpawnerData origin) {
        List<SpawnerData> members = plugin.networks().members(player.getUniqueId(), network);

        long items = 0L;
        long slots = 0L;
        long capacity = 0L;
        long exp = 0L;
        double value = 0.0D;
        for (SpawnerData member : members) {
            items += member.storage().totalItems();
            slots += member.storage().usedSlots();
            capacity += member.maxSlots();
            exp += member.storedExp();
            value += plugin.sell().preview(member).net();
        }

        Menu menu = new Menu("<color:" + Ui.ACCENT + ">Network</color> <color:" + Ui.FAINT + ">· "
                + network + "</color>", 3, true);
        menu.set(4, Menu.icon(Material.CHAIN, "<color:" + Ui.ACCENT + "><bold>" + network + "</bold></color>",
                stat("Spawners", Numbers.plain(members.size())),
                stat("Items held", Numbers.plain(items)),
                stat("Storage", Numbers.compact(slots) + " / " + Numbers.compact(capacity) + " slots"),
                stat("Stored XP", Numbers.plain(exp)),
                stat("Sell value", plugin.economy().format(value))));

        menu.set(10, Menu.icon(Material.GOLD_INGOT, "<color:" + Ui.GOOD + ">Sell whole network</color>",
                        stat("Value", plugin.economy().format(value))),
                p -> {
                    dialogs().sellNetwork(p, members);
                    openNetworkOverview(p, network, origin);
                });
        menu.set(12, Menu.icon(Material.EXPERIENCE_BOTTLE, "<color:" + Ui.GOOD + ">Claim all XP</color>",
                        stat("Stored", Numbers.plain(exp))),
                p -> {
                    dialogs().claimNetworkExp(p, members);
                    openNetworkOverview(p, network, origin);
                });
        menu.set(14, Menu.icon(Material.DROPPER, "<color:" + Ui.WARN + ">Drain everything to me</color>",
                        hint("Metered, so the server stays smooth")),
                p -> {
                    p.closeInventory();
                    dialogs().drainNetwork(p, members);
                });
        menu.set(16, Menu.icon(Material.COMPARATOR, "<color:" + Ui.INK + ">Toggle auto-sell for all</color>"),
                p -> {
                    dialogs().toggleNetworkAutoSell(p, members);
                    openNetworkOverview(p, network, origin);
                });

        if (origin == null) {
            menu.set(22, close(), Player::closeInventory);
        } else {
            menu.set(22, back(), p -> openNetwork(p, origin));
        }
        menu.open(player);
    }

    // ------------------------------------------------------------------ analytics

    public void openAnalytics(Player player, SpawnerData spawner) {
        int hours = plugin.settings().analyticsHistoryHours;

        Menu menu = new Menu("<color:" + Ui.INK + ">Analytics</color>", 3, true);
        menu.set(4, Menu.decorate(dialogs().iconStack(spawner),
                "<color:" + Ui.ACCENT + "><bold>Analytics</bold></color> <color:" + Ui.FAINT + ">last "
                        + hours + "h</color>",
                stat("Items produced", Numbers.plain(plugin.analytics().itemsInWindow(spawner))
                        + " (" + Numbers.compact((long) plugin.analytics().itemsPerHour(spawner)) + "/h)"),
                stat("Earnings", plugin.economy().format(plugin.analytics().moneyInWindow(spawner))
                        + " (" + plugin.economy().format(plugin.analytics().moneyPerHour(spawner)) + "/h)"),
                stat("Lifetime items", Numbers.plain(spawner.producedItems())),
                stat("Lifetime XP", Numbers.plain(spawner.producedExp())),
                stat("Lifetime earnings", plugin.economy().format(spawner.earnedMoney())),
                stat("Placed", Numbers.duration(System.currentTimeMillis() - spawner.createdAt()) + " ago"),
                stat("Owner", spawner.ownerName() == null ? "unknown" : spawner.ownerName()),
                stat("Location", spawner.position().toString())));

        menu.set(13, Menu.icon(Material.GOLDEN_APPLE, "<color:" + Ui.INK + ">Your top spawners</color>"),
                p -> plugin.adminUi().openLeaderboard(p, p.getUniqueId()));
        menu.set(22, back(), p -> openMain(p, spawner));
        menu.open(player);
    }

    // ------------------------------------------------------------------ filters

    public void openFilters(Player player, SpawnerData spawner, int page) {
        List<Material> candidates = dialogs().filterCandidates(spawner);
        int perPage = 45;
        int pages = Math.max(1, (candidates.size() + perPage - 1) / perPage);
        int current = Numbers.clamp(page, 0, pages - 1);

        Menu menu = new Menu("<color:" + Ui.ACCENT + ">Drop filters</color> <color:" + Ui.FAINT + ">· page "
                + (current + 1) + "/" + pages + "</color>", 6, false);

        int start = current * perPage;
        int end = Math.min(candidates.size(), start + perPage);
        for (int i = start; i < end; i++) {
            Material material = candidates.get(i);
            boolean filtered = spawner.filtered().contains(material);
            menu.set(i - start, Menu.icon(material,
                            (filtered ? "<color:" + Ui.BAD + ">✖ " : "<color:" + Ui.GOOD + ">✔ ")
                                    + SpawnerItems.pretty(material.name()) + "</color>",
                            stat("Status", filtered ? "discarded on sight" : "kept"),
                            hint("Click to toggle")),
                    p -> {
                        dialogs().toggleFilter(spawner, material);
                        openFilters(p, spawner, current);
                    });
        }

        menu.set(45, Menu.icon(Material.HOPPER, "<color:" + Ui.ACCENT + ">Drop filters</color>",
                hint("Filtered drops are discarded the moment they are generated"),
                stat("Filtered", spawner.filtered().isEmpty()
                        ? "nothing" : Numbers.plain(spawner.filtered().size()) + " materials")));
        if (current > 0) {
            menu.set(46, Menu.icon(Material.ARROW, "<color:" + Ui.ACCENT + ">Previous page</color>"),
                    p -> openFilters(p, spawner, current - 1));
        }
        if (current < pages - 1) {
            menu.set(47, Menu.icon(Material.ARROW, "<color:" + Ui.ACCENT + ">Next page</color>"),
                    p -> openFilters(p, spawner, current + 1));
        }
        if (!spawner.filtered().isEmpty()) {
            menu.set(49, Menu.icon(Material.WATER_BUCKET, "<color:" + Ui.GOOD + ">Clear all filters</color>"),
                    p -> {
                        dialogs().clearFilters(spawner);
                        openFilters(p, spawner, 0);
                    });
        }
        menu.set(53, back(), p -> openStorage(p, spawner, 0));
        menu.open(player);
    }

    // ------------------------------------------------------------------ server-wide screens

    public void openList(Player player, int page, java.util.UUID ownerFilter) {
        List<SpawnerData> spawners = new ArrayList<>(
                ownerFilter == null ? plugin.spawners().all() : plugin.spawners().ownedBy(ownerFilter));
        spawners.sort(java.util.Comparator.comparingLong((SpawnerData s) -> -s.storage().totalItems()));

        int perPage = 45;
        int pages = Math.max(1, (spawners.size() + perPage - 1) / perPage);
        int current = Numbers.clamp(page, 0, pages - 1);

        Menu menu = new Menu("<color:" + Ui.ACCENT + ">Spawner browser</color> <color:" + Ui.FAINT
                + ">· page " + (current + 1) + "/" + pages + "</color>", 6, false);

        int start = current * perPage;
        int end = Math.min(spawners.size(), start + perPage);
        for (int i = start; i < end; i++) {
            SpawnerData spawner = spawners.get(i);
            Material icon = plugin.lootEngine().iconFor(spawner);
            menu.set(i - start, Menu.icon(icon == null || icon.isAir() ? Material.SPAWNER : icon,
                            "<color:" + Ui.INK + ">" + spawner.displayType() + "</color> <color:"
                                    + Ui.FAINT + ">×" + spawner.stackSize() + "</color>",
                            stat("Items", Numbers.compact(spawner.storage().totalItems())),
                            stat("Owner", spawner.ownerName() == null ? "?" : spawner.ownerName()),
                            stat("Where", spawner.position().toString()),
                            hint("Click to teleport")),
                    p -> plugin.adminUi().teleport(p, spawner));
        }

        menu.set(45, Menu.icon(Material.COMPASS, "<color:" + Ui.ACCENT + ">Spawner browser</color>",
                stat("Tracked", Numbers.plain(spawners.size()))));
        if (current > 0) {
            menu.set(46, Menu.icon(Material.ARROW, "<color:" + Ui.ACCENT + ">Previous page</color>"),
                    p -> openList(p, current - 1, ownerFilter));
        }
        if (current < pages - 1) {
            menu.set(47, Menu.icon(Material.ARROW, "<color:" + Ui.ACCENT + ">Next page</color>"),
                    p -> openList(p, current + 1, ownerFilter));
        }
        menu.set(49, Menu.icon(Material.GOLDEN_APPLE, "<color:" + Ui.INK + ">Leaderboard</color>"),
                p -> openLeaderboard(p, null));
        menu.set(53, close(), Player::closeInventory);
        menu.open(player);
    }

    public void openLeaderboard(Player player, java.util.UUID ownerFilter) {
        List<SpawnerData> top = plugin.analytics()
                .topEarners(ownerFilter, plugin.settings().leaderboardSize);

        Menu menu = new Menu("<color:" + Ui.ACCENT + ">Top earning spawners</color>", 4, true);
        int slot = 9;
        int rank = 1;
        for (SpawnerData spawner : top) {
            if (slot > 26) {
                break;
            }
            Material icon = plugin.lootEngine().iconFor(spawner);
            menu.set(slot++, Menu.icon(icon == null || icon.isAir() ? Material.SPAWNER : icon,
                    "<color:" + Ui.ACCENT + ">#" + rank + "</color> <color:" + Ui.INK + ">"
                            + spawner.displayType() + "</color> <color:" + Ui.FAINT + ">×"
                            + spawner.stackSize() + "</color>",
                    stat("Earned", plugin.economy().format(plugin.analytics().moneyInWindow(spawner))),
                    stat("Items", Numbers.compact(plugin.analytics().itemsInWindow(spawner))),
                    stat("Owner", spawner.ownerName() == null ? "?" : spawner.ownerName())));
            rank++;
        }
        if (top.isEmpty()) {
            menu.set(13, Menu.icon(Material.BARRIER, "<color:" + Ui.FAINT + ">No production recorded yet</color>"));
        }
        menu.set(4, Menu.icon(Material.GOLDEN_APPLE, "<color:" + Ui.ACCENT + "><bold>Leaderboard</bold></color>",
                hint("Last " + plugin.settings().analyticsHistoryHours + " hours")));
        menu.set(30, Menu.icon(Material.PLAYER_HEAD, "<color:" + Ui.INK + ">Only mine</color>"),
                p -> openLeaderboard(p, p.getUniqueId()));
        menu.set(32, Menu.icon(Material.BEACON, "<color:" + Ui.INK + ">Whole server</color>"),
                p -> openLeaderboard(p, null));
        menu.set(35, close(), Player::closeInventory);
        menu.open(player);
    }

    public void openPrices(Player player, int page) {
        List<Map.Entry<Material, Double>> prices =
                new ArrayList<>(plugin.prices().customPrices().entrySet());
        prices.sort(java.util.Comparator.comparingDouble((Map.Entry<Material, Double> e) -> -e.getValue()));

        int perPage = 45;
        int pages = Math.max(1, (prices.size() + perPage - 1) / perPage);
        int current = Numbers.clamp(page, 0, pages - 1);

        Menu menu = new Menu("<color:" + Ui.ACCENT + ">Sell prices</color> <color:" + Ui.FAINT
                + ">· page " + (current + 1) + "/" + pages + "</color>", 6, false);

        int start = current * perPage;
        int end = Math.min(prices.size(), start + perPage);
        for (int i = start; i < end; i++) {
            Map.Entry<Material, Double> entry = prices.get(i);
            menu.set(i - start, Menu.icon(entry.getKey(),
                    "<color:" + Ui.INK + ">" + SpawnerItems.pretty(entry.getKey().name()) + "</color>",
                    stat("Each", plugin.economy().format(entry.getValue())),
                    stat("Per stack", plugin.economy().format(entry.getValue() * 64))));
        }

        menu.set(45, Menu.icon(Material.GOLD_INGOT, "<color:" + Ui.ACCENT + ">Sell prices</color>",
                stat("Priced items", Numbers.plain(prices.size())),
                "none".equals(plugin.prices().shopName())
                        ? null : stat("Shop", plugin.prices().shopName())));
        if (current > 0) {
            menu.set(46, Menu.icon(Material.ARROW, "<color:" + Ui.ACCENT + ">Previous page</color>"),
                    p -> openPrices(p, current - 1));
        }
        if (current < pages - 1) {
            menu.set(47, Menu.icon(Material.ARROW, "<color:" + Ui.ACCENT + ">Next page</color>"),
                    p -> openPrices(p, current + 1));
        }
        menu.set(53, close(), Player::closeInventory);
        menu.open(player);
    }

    // ------------------------------------------------------------------ helpers

    private static String stat(String label, String value) {
        return "<color:" + Ui.FAINT + ">" + label + "</color> <color:" + Ui.INK + ">" + value + "</color>";
    }

    private static String hint(String text) {
        return "<color:" + Ui.FAINT + "><italic>" + text + "</italic></color>";
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    private static ItemStack back() {
        return Menu.icon(Material.ARROW, "<color:" + Ui.FAINT + ">← Back</color>");
    }

    private static ItemStack close() {
        return Menu.icon(Material.BARRIER, "<color:" + Ui.FAINT + ">Close</color>");
    }
}
