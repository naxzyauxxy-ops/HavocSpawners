package dev.havoc.spawners.ui;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.config.Messages;
import dev.havoc.spawners.econ.SellResult;
import dev.havoc.spawners.feature.AutomationService;
import dev.havoc.spawners.feature.UpgradeTier;
import dev.havoc.spawners.spawner.ItemSig;
import dev.havoc.spawners.spawner.SpawnMode;
import dev.havoc.spawners.spawner.SpawnerData;
import dev.havoc.spawners.spawner.SpawnerItems;
import dev.havoc.spawners.spawner.SpawnerManager;
import dev.havoc.spawners.ui.layout.GuiButton;
import dev.havoc.spawners.ui.layout.GuiLayout;
import dev.havoc.spawners.util.Numbers;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Every player-facing screen, as a chest GUI.
 * <p>
 * All fifteen screens share one visual language so the plugin reads as a single thing rather than as
 * a pile of chest inventories: an accent frame, a dark interior, a centred hero tile carrying the
 * numbers, buttons on the interior rows, and Back and Close always in the same place on the bottom
 * row. Lore uses one label/value grid throughout, with a bar wherever something is a proportion.
 */
public final class ChestUi {

    /** Storage rows available for item icons: 5 rows of 9, leaving the bottom row for controls. */
    private static final int ITEMS_PER_PAGE = 45;

    private final HavocSpawners plugin;

    public ChestUi(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    private SpawnerUi actions() {
        return plugin.spawnerUi();
    }

    // ------------------------------------------------------------------ main

    public void openMain(Player player, SpawnerData spawner) {
        Menu menu = Menu.panel(title(spawner.displayType()), 6);
        menu.set(4, hero(spawner));

        menu.set(10, button(Material.CHEST, Ui.ACCENT, "Storage",
                        kv("Holding", Numbers.plain(spawner.storage().totalItems()) + " items"),
                        kv("Pages", Numbers.plain(spawner.storage().pageCount())),
                        tip("Browse and withdraw")),
                p -> openStorage(p, spawner, 0));

        ItemStack xp = button(Material.EXPERIENCE_BOTTLE, Ui.GOOD, "Claim experience",
                kv("Stored", Numbers.plain(spawner.storedExp()) + " / "
                        + Numbers.plain(spawner.maxStoredExp())),
                bar(ratio(spawner.storedExp(), spawner.maxStoredExp())),
                tip("Take it all"));
        menu.set(12, spawner.storedExp() > 0 ? Menu.glow(xp) : xp, p -> actions().claimExp(p, spawner));

        double value = plugin.sell().preview(spawner).net();
        menu.set(14, button(Material.GOLD_INGOT, Ui.GOOD, "Sell everything",
                        kv("Worth", plugin.economy().format(value)),
                        tip("Sell the whole storage")),
                p -> openSell(p, spawner));

        menu.set(16, button(Material.SPAWNER, Ui.ACCENT, "Stack",
                        kv("Stacked", "×" + Numbers.plain(spawner.stackSize())),
                        kv("Limit", Numbers.plain(spawner.maxStackSize())),
                        bar(ratio(spawner.stackSize(), spawner.maxStackSize())),
                        tip("Add or remove spawners")),
                p -> openStack(p, spawner));

        if (plugin.settings().upgradesEnabled) {
            UpgradeTier tier = plugin.upgrades().tier(spawner.level());
            UpgradeTier next = plugin.upgrades().next(spawner.level());
            menu.set(20, button(Material.ANVIL, Ui.WARN, "Upgrades",
                            kv("Tier", tier.name() + " · level " + spawner.level()),
                            next == null ? kv("Next", "fully upgraded")
                                    : kv("Next", next.name() + " · " + plugin.economy().format(next.cost())),
                            tip(next == null ? "Nothing left to buy" : "Spend money on this spawner")),
                    p -> openUpgrade(p, spawner));
        }
        if (plugin.settings().automationEnabled) {
            boolean on = spawner.autoSell() || spawner.autoCollect();
            ItemStack icon = button(Material.HOPPER, Ui.ACCENT, "Automation",
                    kv("Auto-sell", onOff(spawner.autoSell())),
                    kv("Auto-collect", onOff(spawner.autoCollect())),
                    kv("Earned", plugin.economy().format(spawner.earnedMoney())),
                    tip("Run it while you are offline"));
            menu.set(22, on ? Menu.glow(icon) : icon, p -> openAutomation(p, spawner));
        }
        if (plugin.settings().networksEnabled) {
            ItemStack icon = button(Material.CHAIN, Ui.ACCENT, "Network",
                    kv("This spawner", spawner.network() == null ? "unassigned" : spawner.network()),
                    tip("Control many spawners at once"));
            menu.set(24, spawner.network() != null ? Menu.glow(icon) : icon,
                    p -> openNetwork(p, spawner));
        }
        if (plugin.settings().analyticsEnabled) {
            menu.set(29, button(Material.CLOCK, Ui.INK, "Analytics",
                            kv("Items/h", Numbers.compact((long) plugin.analytics().itemsPerHour(spawner))),
                            kv("Earnings/h", plugin.economy().format(
                                    plugin.analytics().moneyPerHour(spawner))),
                            tip("Production history")),
                    p -> openAnalytics(p, spawner));
        }
        menu.set(31, button(Material.COMPARATOR, Ui.INK, "Drop filters",
                        kv("Filtered", spawner.filtered().isEmpty()
                                ? "nothing" : Numbers.plain(spawner.filtered().size()) + " materials"),
                        tip("Throw junk away as it drops")),
                p -> openFilters(p, spawner, 0));
        menu.set(33, button(Material.MINECART, Ui.WARN, "Bulk withdraw",
                        kv("Pages held", Numbers.plain(spawner.storage().pageCount())),
                        tip("Empty many pages at once")),
                p -> openBulkDrop(p, spawner));

        boolean running = !spawner.stopped();
        ItemStack power = button(running ? Material.LIME_DYE : Material.GRAY_DYE,
                running ? Ui.GOOD : Ui.BAD, running ? "Running" : "Turned off",
                kv("Status", actionsStatus(spawner)),
                note(running ? "Producing normally" : "Storage is kept while it is off"),
                tip(running ? "Click to turn off" : "Click to turn on"));
        menu.set(38, running ? Menu.glow(power) : power, p -> {
            actions().toggleRunning(p, spawner);
            openMain(p, spawner);
        });

        if (plugin.settings().allowPlayerSpawnMode) {
            SpawnMode mode = SpawnerManager.effectiveMode(spawner, plugin.settings());
            boolean real = mode == SpawnMode.REAL;
            menu.set(40, button(real ? Material.ZOMBIE_HEAD : Material.REDSTONE, Ui.ACCENT,
                            real ? "Real mobs" : "Simulated",
                            kv("Mode", mode.display()),
                            note(real ? "Mobs spawn for you to kill"
                                    : "Their drops are banked instead"),
                            tip("Click to switch")),
                    p -> {
                        actions().toggleSpawnMode(p, spawner);
                        openMain(p, spawner);
                    });
        }
        menu.set(42, button(Material.PAPER, Ui.FAINT, "Details",
                kv("Owner", spawner.ownerName() == null ? "unknown" : spawner.ownerName()),
                kv("Placed", Numbers.duration(System.currentTimeMillis() - spawner.createdAt()) + " ago"),
                kv("Where", spawner.position().toString()),
                kv("Cycle", Numbers.duration(spawner.spawnDelayTicks() * 50L))));

        menu.set(49, close(), Player::closeInventory);
        menu.open(player);
    }

    /** The centred tile every screen leads with: what this spawner is and how full it is. */
    private ItemStack hero(SpawnerData spawner) {
        long used = spawner.storage().usedSlots();
        double fill = spawner.fillRatio();
        return Menu.decorate(actions().iconStack(spawner),
                "<color:" + Ui.ACCENT + "><bold>" + spawner.displayType().toUpperCase(Locale.ROOT)
                        + "</bold></color> <color:" + Ui.FAINT + ">×"
                        + Numbers.plain(spawner.stackSize()) + "</color>",
                rule(),
                kv("Storage", Numbers.compact(used) + " / " + Numbers.compact(spawner.maxSlots())
                        + " slots"),
                bar(fill),
                kv("Items", Numbers.plain(spawner.storage().totalItems())),
                kv("Experience", Numbers.plain(spawner.storedExp())),
                kv("Per cycle", spawner.minMobs() + "-" + spawner.maxMobs() + " every "
                        + Numbers.duration(spawner.spawnDelayTicks() * 50L)),
                kv("Mode", SpawnerManager.effectiveMode(spawner, plugin.settings()).display()),
                kv("Status", actionsStatus(spawner)));
    }

    private String actionsStatus(SpawnerData spawner) {
        if (spawner.stopped()) {
            return "turned off";
        }
        if (spawner.atCapacity()) {
            return "full";
        }
        return spawner.active() ? "running" : "idle - nobody nearby";
    }

    // ------------------------------------------------------------------ storage

    public void openStorage(Player player, SpawnerData spawner, int page) {
        List<Map.Entry<ItemSig, Long>> entries = spawner.storage().orderedEntries();
        int pages = Math.max(1, (entries.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        int current = Numbers.clamp(page, 0, pages - 1);
        long used = spawner.storage().usedSlots();

        Menu menu = Menu.grid(title("Storage") + " <color:" + Ui.FAINT + ">"
                + (current + 1) + "/" + pages + "</color>", 6);

        int start = current * ITEMS_PER_PAGE;
        int end = Math.min(entries.size(), start + ITEMS_PER_PAGE);
        for (int i = start; i < end; i++) {
            Map.Entry<ItemSig, Long> entry = entries.get(i);
            ItemSig sig = entry.getKey();
            long amount = entry.getValue();
            long stacks = (amount + sig.maxStack() - 1L) / sig.maxStack();
            double share = used <= 0L ? 0.0D : (double) stacks / (double) used;
            double unit = plugin.prices().priceOf(sig.template(), plugin.settings());
            boolean filtered = spawner.filtered().contains(sig.material());

            // The icon is the real item, so it keeps its enchantments, name and model, and the
            // stack count doubles as a readable badge.
            ItemStack display = Menu.badge(sig.copy(1), stacks);
            menu.set(i - start, Menu.decorate(display,
                            "<color:" + Ui.INK + ">" + SpawnerItems.pretty(sig.material().name())
                                    + "</color>  <color:" + Ui.ACCENT + "><bold>×"
                                    + Numbers.plain(amount) + "</bold></color>",
                            rule(),
                            kv("Stacks", Numbers.plain(stacks)),
                            kv("Share", Numbers.percent(share)),
                            bar(share),
                            kv("Worth", unit > 0.0D
                                    ? plugin.economy().format(unit * amount) : "no sell price"),
                            filtered ? "<color:" + Ui.BAD + ">✖ Filtered - new drops discarded</color>"
                                    : null,
                            tip("Withdraw, sell or filter")),
                    p -> openItemActions(p, spawner, sig));
        }

        // The control row is laid out by gui_layouts/storage_gui.yml: slot_1..slot_9 map onto the
        // last row, which is what that file's own comments call inventory slots 46-54.
        GuiLayout layout = plugin.guiLayouts().storage();
        GuiLayout.Condition condition = sellAvailable()
                ? GuiLayout.Condition.SELL_INTEGRATION : GuiLayout.Condition.NO_SELL_INTEGRATION;
        for (Map.Entry<Integer, GuiButton> entry : layout.slots(condition).entrySet()) {
            int configured = entry.getKey();
            GuiButton definition = entry.getValue();
            if (!definition.enabled() || configured < 1 || configured > 9) {
                continue;
            }
            int slot = 45 + (configured - 1);
            if (definition.infoButton()) {
                menu.set(slot, hero(spawner));
                continue;
            }
            String action = definition.actionFor(false);
            menu.set(slot, layoutIcon(definition, spawner, action, current, pages),
                    p -> runStorageAction(p, spawner, action, current));
        }
        menu.fillEmpty(45, 53, Material.BLACK_STAINED_GLASS_PANE);

        if (entries.isEmpty()) {
            menu.set(22, Menu.icon(Material.BARRIER, "<color:" + Ui.FAINT + ">Nothing stored yet</color>",
                    note("Drops appear here as they are produced")));
        }
        menu.open(player);
    }

    public void openItemActions(Player player, SpawnerData spawner, ItemSig sig) {
        long amount = spawner.storage().countOf(sig);
        double unit = plugin.prices().priceOf(sig.template(), plugin.settings());
        String name = SpawnerItems.pretty(sig.material().name());
        boolean filtered = spawner.filtered().contains(sig.material());

        Menu menu = Menu.panel(title(name), 5);
        menu.set(4, Menu.decorate(Menu.badge(sig.copy(1), amount),
                "<color:" + Ui.ACCENT + "><bold>" + name.toUpperCase(Locale.ROOT) + "</bold></color>",
                rule(),
                kv("Stored", Numbers.plain(amount)),
                kv("Unit price", unit > 0.0D ? plugin.economy().format(unit) : "no sell price"),
                unit > 0.0D ? kv("Total value", plugin.economy().format(unit * amount)) : null,
                kv("Filtered", filtered ? "yes" : "no")));

        menu.set(10, button(Material.CHEST_MINECART, Ui.ACCENT, "Take one stack",
                        kv("Amount", Numbers.plain(Math.min(amount, sig.maxStack()))),
                        tip("Straight into your inventory")),
                p -> actions().withdraw(p, spawner, sig, sig.maxStack()));
        menu.set(12, button(Material.CHEST, Ui.ACCENT, "Fill my inventory",
                        kv("Up to", Numbers.plain(Math.min(amount, (long) sig.maxStack() * 36))),
                        tip("As much as will fit")),
                p -> actions().withdraw(p, spawner, sig, (long) sig.maxStack() * 36));
        menu.set(14, button(Material.DROPPER, Ui.WARN, "Drop all on the ground",
                        kv("Amount", Numbers.plain(amount)),
                        note("Thrown where you are looking"),
                        tip("Metered, so the server stays smooth")),
                p -> actions().dropItemOnGround(p, spawner, sig, () -> openStorage(p, spawner, 0)));
        if (unit > 0.0D && plugin.settings().economyEnabled) {
            menu.set(16, button(Material.GOLD_INGOT, Ui.GOOD, "Sell all " + name,
                            kv("You receive", plugin.economy().format(unit * amount)),
                            tip("Sells only this item")),
                    p -> actions().sellOne(p, spawner, sig));
        }

        ItemStack filter = button(filtered ? Material.LIME_DYE : Material.REDSTONE_TORCH,
                filtered ? Ui.GOOD : Ui.BAD, filtered ? "Stop filtering" : "Filter this out",
                kv("Currently", filtered ? "discarded on sight" : "kept"),
                note("Filtered drops are never stored"),
                tip("Click to toggle"));
        menu.set(21, filtered ? Menu.glow(filter) : filter, p -> {
            actions().toggleFilter(spawner, sig.material());
            openItemActions(p, spawner, sig);
        });
        menu.set(23, button(Material.COMPARATOR, Ui.INK, "Sort to top",
                        note("Show this item first in storage"),
                        tip("Click to pin it")),
                p -> {
                    actions().sortToTop(spawner, sig.material());
                    openStorage(p, spawner, 0);
                });

        footer(menu, p -> openStorage(p, spawner, 0));
        menu.open(player);
    }

    // ------------------------------------------------------------------ bulk drop

    public void openBulkDrop(Player player, SpawnerData spawner) {
        if (!player.hasPermission("havocspawners.bulkdrop")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        int pages = spawner.storage().pageCount();
        boolean toInventory = plugin.settings().preferPlayerInventory;

        Menu menu = Menu.panel(title("Bulk withdraw"), 5);
        menu.set(4, Menu.icon(Material.MINECART,
                "<color:" + Ui.WARN + "><bold>BULK WITHDRAW</bold></color>",
                rule(),
                kv("Pages held", Numbers.plain(pages)),
                kv("Items held", Numbers.plain(spawner.storage().totalItems())),
                kv("Delivery", plugin.settings().stacksPerTick + " stacks per tick"),
                kv("Going to", toInventory ? "your inventory" : "the ground"),
                plugin.dropService().isRunning(spawner)
                        ? "<color:" + Ui.BAD + ">A withdrawal is already running</color>" : null,
                note("Metered, so a four-million-item spawner costs the same as a small one")));

        int[] counts = {1, 5, 20};
        int[] slots = {10, 12, 14};
        for (int i = 0; i < counts.length; i++) {
            int count = counts[i];
            menu.set(slots[i], button(Material.DROPPER, Ui.WARN,
                            "Withdraw " + count + (count == 1 ? " page" : " pages"),
                            kv("About", Numbers.plain(count * 45L) + " stacks"),
                            note("Starts from the first page"),
                            tip("Click to start")),
                    p -> actions().runBulk(p, spawner, 0, count - 1, toInventory));
        }
        menu.set(16, button(Material.TNT, Ui.BAD, "Withdraw everything",
                        kv("Items", Numbers.plain(spawner.storage().totalItems())),
                        kv("Pages", Numbers.plain(pages)),
                        tip("Empties the whole spawner")),
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

        footer(menu, p -> openStorage(p, spawner, 0));
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
        GuiLayout layout = plugin.guiLayouts().sellConfirm();
        // The layout file can turn the confirmation off entirely, which is what the old plugin's
        // skip_sell_confirmation did.
        if (layout.skipSellConfirmation()) {
            actions().sellAll(player, spawner);
            openMain(player, spawner);
            return;
        }
        SellResult preview = plugin.sell().preview(spawner);

        List<String> lore = new ArrayList<>();
        lore.add(rule());
        lore.add(kv("Items", Numbers.plain(preview.itemsSold())));
        lore.add(kv("Gross", plugin.economy().format(preview.gross())));
        if (plugin.settings().taxPercent > 0.0D) {
            lore.add(kv("Tax", plugin.economy().format(preview.tax())
                    + " (" + Numbers.percent(plugin.settings().taxPercent / 100.0D) + ")"));
        }
        lore.add(kv("You receive", plugin.economy().format(preview.net())));
        if (preview.unsellableItems() > 0L) {
            lore.add(note(Numbers.plain(preview.unsellableItems()) + " items have no price and stay"));
        }

        Menu menu = Menu.panel(title("Confirm sale"), 3);
        // gui_layouts/sell_confirm_gui.yml: slot_1..slot_27 over three rows.
        for (Map.Entry<Integer, GuiButton> entry : layout.slots(GuiLayout.Condition.SELL_INTEGRATION)
                .entrySet()) {
            int configured = entry.getKey();
            GuiButton definition = entry.getValue();
            if (!definition.enabled() || configured < 1 || configured > 27) {
                continue;
            }
            int slot = configured - 1;
            if (definition.infoButton()) {
                menu.set(slot, Menu.decorate(actions().iconStack(spawner),
                        "<color:" + Ui.GOOD + "><bold>SELL STORAGE</bold></color>",
                        lore.toArray(new String[0])));
                continue;
            }
            String action = definition.actionFor(false);
            Material material = definition.usesSpawnerIcon()
                    ? plugin.lootEngine().iconFor(spawner) : definition.material();
            if ("confirm".equalsIgnoreCase(action)) {
                menu.set(slot, Menu.glow(button(material, Ui.GOOD, "Confirm",
                                kv("You receive", plugin.economy().format(preview.net())),
                                tip("Sell it all"))),
                        p -> {
                            actions().sellAll(p, spawner);
                            openMain(p, spawner);
                        });
            } else if ("cancel".equalsIgnoreCase(action)) {
                menu.set(slot, button(material, Ui.BAD, "Cancel", tip("Keep everything")),
                        p -> openMain(p, spawner));
            } else {
                menu.set(slot, Menu.pane(material));
            }
        }
        menu.open(player);
    }

    // ------------------------------------------------------------------ stack

    public void openStack(Player player, SpawnerData spawner) {
        if (!player.hasPermission("havocspawners.stack")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        int inHand = actions().countMatchingInInventory(player, spawner);

        Menu menu = Menu.panel(title("Stack manager"), 5);
        menu.set(4, Menu.decorate(Menu.badge(actions().iconStack(spawner), spawner.stackSize()),
                "<color:" + Ui.ACCENT + "><bold>STACK MANAGER</bold></color>",
                rule(),
                kv("Stacked", Numbers.plain(spawner.stackSize()) + " / "
                        + Numbers.plain(spawner.maxStackSize())),
                bar(ratio(spawner.stackSize(), spawner.maxStackSize())),
                kv("In your inventory", Numbers.plain(inHand) + " matching"),
                note("Stacking multiplies simulation and storage capacity")));

        int[] amounts = {1, 8, 64};
        int[] addSlots = {10, 11, 12};
        int[] removeSlots = {14, 15, 16};
        for (int i = 0; i < amounts.length; i++) {
            int amount = amounts[i];
            menu.set(addSlots[i], button(Material.LIME_DYE, Ui.GOOD, "Add " + amount,
                            note("Takes matching spawners from your inventory"),
                            tip("Click to add")),
                    p -> actions().changeStack(p, spawner, amount));
            menu.set(removeSlots[i], button(Material.RED_DYE, Ui.WARN, "Remove " + amount,
                            note("Gives them back as items"),
                            tip("Click to remove")),
                    p -> actions().changeStack(p, spawner, -amount));
        }
        menu.set(22, button(Material.SPAWNER, Ui.ACCENT, "Add everything",
                        kv("Available", Numbers.plain(inHand)),
                        tip("Adds every matching spawner you hold")),
                p -> actions().changeStack(p, spawner,
                        Math.max(1, actions().countMatchingInInventory(p, spawner))));

        footer(menu, p -> openMain(p, spawner));
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

        Menu menu = Menu.panel(title("Upgrades"), 5);
        menu.set(4, Menu.decorate(actions().iconStack(spawner),
                "<color:" + Ui.ACCENT + "><bold>" + current.name().toUpperCase(Locale.ROOT)
                        + "</bold></color> <color:" + Ui.FAINT + ">level " + spawner.level() + "</color>",
                rule(),
                kv("Cycle time", Numbers.duration(spawner.spawnDelayTicks() * 50L)),
                kv("Loot multiplier", "×" + current.lootMultiplier()),
                kv("Storage", Numbers.plain(spawner.maxPages()) + " pages"),
                kv("XP capacity", Numbers.plain(spawner.maxStoredExp()))));

        if (next == null) {
            menu.set(22, Menu.glow(Menu.icon(Material.NETHER_STAR,
                    "<color:" + Ui.GOOD + "><bold>Fully upgraded</bold></color>",
                    note("There is nothing left to buy"))));
        } else {
            double balance = plugin.economy().balance(player.getUniqueId());
            boolean affordable = balance >= next.cost();
            ItemStack buy = button(affordable ? Material.ANVIL : Material.BARRIER,
                    affordable ? Ui.GOOD : Ui.BAD, "Upgrade to " + next.name(),
                    rule(),
                    kv("Speed", "×" + next.delayMultiplier() + " cycle time"),
                    kv("Loot", "×" + next.lootMultiplier()),
                    kv("Extra pages", "+" + next.bonusPages()),
                    kv("Extra XP cap", "+" + Numbers.plain(next.bonusExpCapacity())),
                    rule(),
                    kv("Cost", plugin.economy().format(next.cost())),
                    kv("Your balance", plugin.economy().format(balance)),
                    affordable ? tip("Click to upgrade")
                            : note("You cannot afford this yet"));
            menu.set(22, affordable ? Menu.glow(buy) : buy, p -> {
                actions().buyUpgrade(p, spawner, next);
                openUpgrade(p, spawner);
            });
        }

        footer(menu, p -> openMain(p, spawner));
        menu.open(player);
    }

    // ------------------------------------------------------------------ automation

    public void openAutomation(Player player, SpawnerData spawner) {
        if (!player.hasPermission("havocspawners.automation")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        boolean hopper = AutomationService.hasHopper(spawner);

        Menu menu = Menu.panel(title("Automation"), 5);
        menu.set(4, Menu.icon(Material.REDSTONE,
                "<color:" + Ui.ACCENT + "><bold>AUTOMATION</bold></color>",
                rule(),
                kv("Runs every", plugin.settings().automationIntervalSeconds + "s"),
                kv("Earned so far", plugin.economy().format(spawner.earnedMoney())),
                note("Keeps working while you are offline")));

        ItemStack sell = button(spawner.autoSell() ? Material.GOLD_BLOCK : Material.GOLD_NUGGET,
                spawner.autoSell() ? Ui.GOOD : Ui.FAINT,
                (spawner.autoSell() ? "Disable" : "Enable") + " auto-sell",
                kv("Currently", onOff(spawner.autoSell())),
                note("Sells priced drops into your balance"),
                tip("Click to toggle"));
        menu.set(11, spawner.autoSell() ? Menu.glow(sell) : sell, p -> {
            spawner.autoSell(!spawner.autoSell());
            plugin.storage().queueSave(spawner);
            openAutomation(p, spawner);
        });

        ItemStack collect = button(hopper ? Material.HOPPER : Material.BARRIER,
                spawner.autoCollect() ? Ui.GOOD : Ui.FAINT,
                (spawner.autoCollect() ? "Disable" : "Enable") + " auto-collect",
                kv("Currently", onOff(spawner.autoCollect())),
                kv("Hopper below", hopper ? "found" : "missing"),
                note("Feeds a hopper directly under the spawner - nothing else"),
                tip(hopper ? "Click to toggle" : "Place a hopper under the spawner first"));
        menu.set(15, spawner.autoCollect() ? Menu.glow(collect) : collect, p -> {
            if (!spawner.autoCollect() && !AutomationService.hasHopper(spawner)) {
                plugin.messages().send(p, "automation.needs-hopper");
                return;
            }
            spawner.autoCollect(!spawner.autoCollect());
            plugin.storage().queueSave(spawner);
            openAutomation(p, spawner);
        });

        footer(menu, p -> openMain(p, spawner));
        menu.open(player);
    }

    // ------------------------------------------------------------------ networks

    public void openNetwork(Player player, SpawnerData spawner) {
        if (!player.hasPermission("havocspawners.network")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        List<String> names = plugin.networks().namesFor(player.getUniqueId());

        Menu menu = Menu.panel(title("Networks"), 5);
        menu.set(4, Menu.icon(Material.CHAIN, "<color:" + Ui.ACCENT + "><bold>NETWORKS</bold></color>",
                rule(),
                kv("This spawner", spawner.network() == null ? "unassigned" : spawner.network()),
                kv("Your networks", names.isEmpty() ? "none" : String.join(", ", names)),
                note("Group spawners so one button sells or drains all of them")));

        int slot = 10;
        for (String name : names) {
            if (slot > 16) {
                break;
            }
            boolean assigned = name.equalsIgnoreCase(spawner.network());
            ItemStack icon = button(assigned ? Material.LIME_BANNER : Material.WHITE_BANNER,
                    Ui.ACCENT, name,
                    kv("Spawners", Numbers.plain(
                            plugin.networks().members(player.getUniqueId(), name).size())),
                    assigned ? kv("This spawner", "is a member") : null,
                    tip("Open this network"));
            menu.set(slot++, assigned ? Menu.glow(icon) : icon,
                    p -> openNetworkOverview(p, name, spawner));
        }

        // A chest has no text field, so naming a network happens in chat instead.
        menu.set(20, button(Material.NAME_TAG, Ui.GOOD, "Create a network",
                        note("Closes this menu and asks you to type a name"),
                        tip("Click to start")),
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
            menu.set(22, button(Material.LEAD, Ui.ACCENT, "Assign this spawner",
                            note("Closes this menu and asks which network"),
                            tip("Click to choose")),
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
            menu.set(24, button(Material.SHEARS, Ui.WARN, "Remove from network",
                            tip("Leaves the network alone")),
                    p -> {
                        plugin.networks().assign(spawner, p.getUniqueId(), null);
                        openNetwork(p, spawner);
                    });
        }

        footer(menu, p -> openMain(p, spawner));
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

        Menu menu = Menu.panel(title(network), 5);
        menu.set(4, Menu.badge(Menu.icon(Material.CHAIN,
                "<color:" + Ui.ACCENT + "><bold>" + network.toUpperCase(Locale.ROOT) + "</bold></color>",
                rule(),
                kv("Spawners", Numbers.plain(members.size())),
                kv("Items held", Numbers.plain(items)),
                kv("Storage", Numbers.compact(slots) + " / " + Numbers.compact(capacity) + " slots"),
                bar(capacity <= 0 ? 0.0D : (double) slots / capacity),
                kv("Stored XP", Numbers.plain(exp)),
                kv("Sell value", plugin.economy().format(value))), members.size()));

        menu.set(10, button(Material.GOLD_INGOT, Ui.GOOD, "Sell whole network",
                        kv("Value", plugin.economy().format(value)),
                        tip("Sells every spawner in it")),
                p -> {
                    actions().sellNetwork(p, members);
                    openNetworkOverview(p, network, origin);
                });
        menu.set(12, button(Material.EXPERIENCE_BOTTLE, Ui.GOOD, "Claim all XP",
                        kv("Stored", Numbers.plain(exp)),
                        tip("Takes it from every spawner")),
                p -> {
                    actions().claimNetworkExp(p, members);
                    openNetworkOverview(p, network, origin);
                });
        menu.set(14, button(Material.DROPPER, Ui.WARN, "Drain everything to me",
                        kv("Items", Numbers.plain(items)),
                        note("Metered, so the server stays smooth"),
                        tip("Click to start")),
                p -> {
                    p.closeInventory();
                    actions().drainNetwork(p, members);
                });
        menu.set(16, button(Material.COMPARATOR, Ui.INK, "Toggle auto-sell for all",
                        tip("Flips every spawner at once")),
                p -> {
                    actions().toggleNetworkAutoSell(p, members);
                    openNetworkOverview(p, network, origin);
                });

        if (origin == null) {
            menu.set(31, close(), Player::closeInventory);
        } else {
            footer(menu, p -> openNetwork(p, origin));
        }
        menu.open(player);
    }

    // ------------------------------------------------------------------ analytics

    public void openAnalytics(Player player, SpawnerData spawner) {
        int hours = plugin.settings().analyticsHistoryHours;

        Menu menu = Menu.panel(title("Analytics"), 5);
        menu.set(4, Menu.decorate(actions().iconStack(spawner),
                "<color:" + Ui.ACCENT + "><bold>ANALYTICS</bold></color> <color:" + Ui.FAINT
                        + ">last " + hours + "h</color>",
                rule(),
                kv("Items produced", Numbers.plain(plugin.analytics().itemsInWindow(spawner))),
                kv("Per hour", Numbers.compact((long) plugin.analytics().itemsPerHour(spawner))),
                kv("Earnings", plugin.economy().format(plugin.analytics().moneyInWindow(spawner))),
                kv("Per hour", plugin.economy().format(plugin.analytics().moneyPerHour(spawner)))));

        menu.set(11, Menu.icon(Material.BOOK, "<color:" + Ui.INK + ">Lifetime</color>",
                rule(),
                kv("Items", Numbers.plain(spawner.producedItems())),
                kv("Experience", Numbers.plain(spawner.producedExp())),
                kv("Earnings", plugin.economy().format(spawner.earnedMoney())),
                kv("Placed", Numbers.duration(System.currentTimeMillis() - spawner.createdAt()) + " ago"),
                kv("Owner", spawner.ownerName() == null ? "unknown" : spawner.ownerName()),
                kv("Where", spawner.position().toString())));
        menu.set(15, button(Material.GOLDEN_APPLE, Ui.INK, "Your top spawners",
                        tip("Open the leaderboard")),
                p -> openLeaderboard(p, p.getUniqueId()));

        footer(menu, p -> openMain(p, spawner));
        menu.open(player);
    }

    // ------------------------------------------------------------------ filters

    public void openFilters(Player player, SpawnerData spawner, int page) {
        List<Material> candidates = actions().filterCandidates(spawner);
        int perPage = 45;
        int pages = Math.max(1, (candidates.size() + perPage - 1) / perPage);
        int current = Numbers.clamp(page, 0, pages - 1);

        Menu menu = Menu.grid(title("Drop filters") + " <color:" + Ui.FAINT + ">"
                + (current + 1) + "/" + pages + "</color>", 6);

        int start = current * perPage;
        int end = Math.min(candidates.size(), start + perPage);
        for (int i = start; i < end; i++) {
            Material material = candidates.get(i);
            boolean filtered = spawner.filtered().contains(material);
            ItemStack icon = Menu.icon(material,
                    (filtered ? "<color:" + Ui.BAD + ">✖ " : "<color:" + Ui.GOOD + ">✔ ")
                            + SpawnerItems.pretty(material.name()) + "</color>",
                    kv("Status", filtered ? "discarded on sight" : "kept"),
                    tip("Click to toggle"));
            menu.set(i - start, filtered ? icon : Menu.glow(icon), p -> {
                actions().toggleFilter(spawner, material);
                openFilters(p, spawner, current);
            });
        }

        menu.set(45, Menu.icon(Material.COMPARATOR,
                "<color:" + Ui.ACCENT + "><bold>DROP FILTERS</bold></color>",
                rule(),
                kv("Filtered", spawner.filtered().isEmpty()
                        ? "nothing" : Numbers.plain(spawner.filtered().size()) + " materials"),
                note("Filtered drops are discarded the moment they are generated"),
                note("Glowing means it is kept")));
        if (current > 0) {
            menu.set(48, button(Material.ARROW, Ui.ACCENT, "Previous page",
                            kv("Page", (current + 1) + " / " + pages)),
                    p -> openFilters(p, spawner, current - 1));
        }
        if (current < pages - 1) {
            menu.set(50, button(Material.ARROW, Ui.ACCENT, "Next page",
                            kv("Page", (current + 1) + " / " + pages)),
                    p -> openFilters(p, spawner, current + 1));
        }
        if (!spawner.filtered().isEmpty()) {
            menu.set(52, button(Material.WATER_BUCKET, Ui.GOOD, "Clear all filters",
                            tip("Keep everything again")),
                    p -> {
                        actions().clearFilters(spawner);
                        openFilters(p, spawner, 0);
                    });
        }
        menu.set(49, back(), p -> openStorage(p, spawner, 0));
        menu.fillEmpty(45, 53, Material.BLACK_STAINED_GLASS_PANE);
        menu.open(player);
    }

    // ------------------------------------------------------------------ server-wide screens

    public void openList(Player player, int page, UUID ownerFilter) {
        List<SpawnerData> spawners = new ArrayList<>(
                ownerFilter == null ? plugin.spawners().all() : plugin.spawners().ownedBy(ownerFilter));
        spawners.sort(Comparator.comparingLong((SpawnerData s) -> -s.storage().totalItems()));

        int perPage = 45;
        int pages = Math.max(1, (spawners.size() + perPage - 1) / perPage);
        int current = Numbers.clamp(page, 0, pages - 1);

        Menu menu = Menu.grid(title("Spawner browser") + " <color:" + Ui.FAINT + ">"
                + (current + 1) + "/" + pages + "</color>", 6);

        int start = current * perPage;
        int end = Math.min(spawners.size(), start + perPage);
        for (int i = start; i < end; i++) {
            SpawnerData spawner = spawners.get(i);
            Material icon = plugin.lootEngine().iconFor(spawner);
            menu.set(i - start, Menu.decorate(
                            Menu.badge(new ItemStack(icon == null || icon.isAir()
                                    ? Material.SPAWNER : icon), spawner.stackSize()),
                            "<color:" + Ui.INK + ">" + spawner.displayType() + "</color> <color:"
                                    + Ui.FAINT + ">×" + spawner.stackSize() + "</color>",
                            rule(),
                            kv("Items", Numbers.compact(spawner.storage().totalItems())),
                            kv("Owner", spawner.ownerName() == null ? "?" : spawner.ownerName()),
                            kv("Where", spawner.position().toString()),
                            tip("Click to teleport")),
                    p -> plugin.adminUi().teleport(p, spawner));
        }

        menu.set(45, Menu.icon(Material.COMPASS,
                "<color:" + Ui.ACCENT + "><bold>SPAWNER BROWSER</bold></color>",
                rule(),
                kv("Tracked", Numbers.plain(spawners.size()))));
        if (current > 0) {
            menu.set(48, button(Material.ARROW, Ui.ACCENT, "Previous page",
                            kv("Page", (current + 1) + " / " + pages)),
                    p -> openList(p, current - 1, ownerFilter));
        }
        if (current < pages - 1) {
            menu.set(50, button(Material.ARROW, Ui.ACCENT, "Next page",
                            kv("Page", (current + 1) + " / " + pages)),
                    p -> openList(p, current + 1, ownerFilter));
        }
        menu.set(52, button(Material.GOLDEN_APPLE, Ui.INK, "Leaderboard",
                        tip("Top earning spawners")),
                p -> openLeaderboard(p, null));
        menu.set(49, close(), Player::closeInventory);
        menu.fillEmpty(45, 53, Material.BLACK_STAINED_GLASS_PANE);
        menu.open(player);
    }

    public void openLeaderboard(Player player, UUID ownerFilter) {
        List<SpawnerData> top = plugin.analytics()
                .topEarners(ownerFilter, plugin.settings().leaderboardSize);

        Menu menu = Menu.panel(title("Leaderboard"), 5);
        menu.set(4, Menu.icon(Material.GOLDEN_APPLE,
                "<color:" + Ui.ACCENT + "><bold>TOP EARNING SPAWNERS</bold></color>",
                rule(),
                kv("Window", "last " + plugin.settings().analyticsHistoryHours + "h"),
                kv("Showing", ownerFilter == null ? "the whole server" : "only yours")));

        int[] podium = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int rank = 1;
        for (SpawnerData spawner : top) {
            if (rank > podium.length) {
                break;
            }
            Material icon = plugin.lootEngine().iconFor(spawner);
            ItemStack tile = Menu.decorate(Menu.badge(new ItemStack(icon == null || icon.isAir()
                            ? Material.SPAWNER : icon), rank),
                    "<color:" + Ui.ACCENT + "><bold>#" + rank + "</bold></color> <color:" + Ui.INK + ">"
                            + spawner.displayType() + "</color>",
                    rule(),
                    kv("Earned", plugin.economy().format(plugin.analytics().moneyInWindow(spawner))),
                    kv("Items", Numbers.compact(plugin.analytics().itemsInWindow(spawner))),
                    kv("Owner", spawner.ownerName() == null ? "?" : spawner.ownerName()));
            menu.set(podium[rank - 1], rank <= 3 ? Menu.glow(tile) : tile);
            rank++;
        }
        if (top.isEmpty()) {
            menu.set(22, Menu.icon(Material.BARRIER,
                    "<color:" + Ui.FAINT + ">No production recorded yet</color>"));
        }

        menu.set(29, button(Material.PLAYER_HEAD, Ui.INK, "Only mine", tip("Filter to your spawners")),
                p -> openLeaderboard(p, p.getUniqueId()));
        menu.set(33, button(Material.BEACON, Ui.INK, "Whole server", tip("Show everyone")),
                p -> openLeaderboard(p, null));
        menu.set(40, close(), Player::closeInventory);
        menu.open(player);
    }

    public void openPrices(Player player, int page) {
        List<Map.Entry<Material, Double>> prices =
                new ArrayList<>(plugin.prices().customPrices().entrySet());
        prices.sort(Comparator.comparingDouble((Map.Entry<Material, Double> e) -> -e.getValue()));

        int perPage = 45;
        int pages = Math.max(1, (prices.size() + perPage - 1) / perPage);
        int current = Numbers.clamp(page, 0, pages - 1);

        Menu menu = Menu.grid(title("Sell prices") + " <color:" + Ui.FAINT + ">"
                + (current + 1) + "/" + pages + "</color>", 6);

        int start = current * perPage;
        int end = Math.min(prices.size(), start + perPage);
        for (int i = start; i < end; i++) {
            Map.Entry<Material, Double> entry = prices.get(i);
            menu.set(i - start, Menu.icon(entry.getKey(),
                    "<color:" + Ui.INK + ">" + SpawnerItems.pretty(entry.getKey().name()) + "</color>",
                    rule(),
                    kv("Each", plugin.economy().format(entry.getValue())),
                    kv("Per stack", plugin.economy().format(entry.getValue() * 64))));
        }

        menu.set(45, Menu.icon(Material.GOLD_INGOT,
                "<color:" + Ui.ACCENT + "><bold>SELL PRICES</bold></color>",
                rule(),
                kv("Priced items", Numbers.plain(prices.size())),
                "none".equals(plugin.prices().shopName())
                        ? null : kv("Shop", plugin.prices().shopName())));
        if (current > 0) {
            menu.set(48, button(Material.ARROW, Ui.ACCENT, "Previous page",
                            kv("Page", (current + 1) + " / " + pages)),
                    p -> openPrices(p, current - 1));
        }
        if (current < pages - 1) {
            menu.set(50, button(Material.ARROW, Ui.ACCENT, "Next page",
                            kv("Page", (current + 1) + " / " + pages)),
                    p -> openPrices(p, current + 1));
        }
        menu.set(49, close(), Player::closeInventory);
        menu.fillEmpty(45, 53, Material.BLACK_STAINED_GLASS_PANE);
        menu.open(player);
    }

    // ------------------------------------------------------------------ configured layout

    /** True when selling is actually possible, which is what the layout's conditions key off. */
    private boolean sellAvailable() {
        return plugin.settings().economyEnabled && plugin.economy().available();
    }

    /** Names a configured button from its action, so a re-slotted layout still reads correctly. */
    private ItemStack layoutIcon(GuiButton definition, SpawnerData spawner, String action,
                                 int page, int pages) {
        Material material = definition.usesSpawnerIcon()
                ? plugin.lootEngine().iconFor(spawner) : definition.material();
        return switch (action == null ? "none" : action.toLowerCase(Locale.ROOT)) {
            case "previous_page" -> button(material, Ui.ACCENT, "Previous page",
                    kv("Page", (page + 1) + " / " + pages));
            case "next_page" -> button(material, Ui.ACCENT, "Next page",
                    kv("Page", (page + 1) + " / " + pages));
            case "sort_items" -> button(material, Ui.INK, "Sort storage",
                    note("Biggest stacks first"), tip("Click to sort"));
            case "open_filter" -> button(material, Ui.INK, "Drop filters",
                    kv("Filtered", spawner.filtered().isEmpty()
                            ? "nothing" : Numbers.plain(spawner.filtered().size()) + " materials"),
                    tip("Choose what to throw away"));
            case "sell_all" -> button(material, Ui.GOOD, "Sell everything",
                    kv("Worth", plugin.economy().format(plugin.sell().preview(spawner).net())),
                    tip("Sell the whole storage"));
            case "sell_and_exp" -> button(material, Ui.GOOD, "Sell everything + XP",
                    kv("Worth", plugin.economy().format(plugin.sell().preview(spawner).net())),
                    kv("Experience", Numbers.plain(spawner.storedExp())),
                    tip("Claim the XP, then sell"));
            case "collect_exp" -> button(material, Ui.GOOD, "Claim experience",
                    kv("Stored", Numbers.plain(spawner.storedExp())),
                    bar(ratio(spawner.storedExp(), spawner.maxStoredExp())),
                    tip("Take it all"));
            case "take_all" -> button(material, Ui.ACCENT, "Take this page",
                    note("Fills your inventory from this page"), tip("Click to take"));
            case "drop_page" -> button(material, Ui.WARN, "Drop this page",
                    note("Thrown where you are looking"),
                    note("The screen stays open, so you can keep going"),
                    tip("Click to throw"));
            case "bulk_withdraw" -> button(material, Ui.WARN, "Bulk withdraw",
                    kv("Pages held", Numbers.plain(spawner.storage().pageCount())),
                    tip("Empty many pages at once"));
            case "return_main" -> button(material, Ui.FAINT, "Back", tip("To the spawner menu"));
            case "close" -> button(material, Ui.FAINT, "Close");
            default -> Menu.pane(material);
        };
    }

    /** Runs a storage-screen action named by the layout file. */
    private void runStorageAction(Player player, SpawnerData spawner, String action, int page) {
        switch (action == null ? "none" : action.toLowerCase(Locale.ROOT)) {
            case "previous_page" -> openStorage(player, spawner, page - 1);
            case "next_page" -> openStorage(player, spawner, page + 1);
            case "sort_items" -> {
                actions().sortStorage(spawner);
                openStorage(player, spawner, page);
            }
            case "open_filter" -> openFilters(player, spawner, 0);
            case "sell_all" -> openSell(player, spawner);
            case "sell_and_exp" -> {
                actions().claimExpQuietly(player, spawner);
                openSell(player, spawner);
            }
            case "collect_exp" -> {
                actions().claimExp(player, spawner);
                openStorage(player, spawner, page);
            }
            case "take_all" -> {
                actions().takePage(player, spawner, page);
                openStorage(player, spawner, page);
            }
            case "drop_page" -> actions().dropOnePage(player, spawner, page);
            case "bulk_withdraw" -> openBulkDrop(player, spawner);
            case "return_main" -> openMain(player, spawner);
            case "close" -> player.closeInventory();
            default -> {
                // "none" and anything unrecognised: a display tile, not a button.
            }
        }
    }

    // ------------------------------------------------------------------ design system

    /** Every screen's title reads "Havoc | <what>", so the plugin is identifiable at a glance. */
    private static String title(String screen) {
        return "<color:" + Ui.ACCENT + "><bold>Havoc</bold></color> <color:" + Ui.FAINT
                + ">| </color><color:" + Ui.INK + ">" + screen + "</color>";
    }

    /** A button: a coloured title plus the standard lore grid. */
    private static ItemStack button(Material material, String colour, String name, String... lore) {
        return Menu.icon(material, "<color:" + colour + "><bold>" + name + "</bold></color>", lore);
    }

    /** One row of the label/value grid used in every tooltip. */
    private static String kv(String label, String value) {
        return "<color:" + Ui.FAINT + ">" + label + "</color>  <color:" + Ui.INK + ">"
                + value + "</color>";
    }

    /** The click affordance, always the last line. */
    private static String tip(String text) {
        return "<color:" + Ui.ACCENT + ">➤ </color><color:" + Ui.INK + ">" + text + "</color>";
    }

    private static String note(String text) {
        return "<color:" + Ui.FAINT + "><italic>" + text + "</italic></color>";
    }

    private static String rule() {
        return "<color:" + Ui.FAINT + ">━━━━━━━━━━━━━━━━</color>";
    }

    private static String bar(double ratio) {
        return Ui.bar(ratio, 16, ratio >= 0.95D ? Ui.BAD : ratio >= 0.75D ? Ui.WARN : Ui.GOOD);
    }

    private static double ratio(long value, long max) {
        return max <= 0L ? 0.0D : Math.min(1.0D, (double) value / (double) max);
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    /** Back in the bottom-centre slot, Close beside it - identical on every screen. */
    private static void footer(Menu menu, java.util.function.Consumer<Player> back) {
        int row = (menu.rows() - 1) * 9;
        menu.set(row + 4, back(), back);
        menu.set(row + 6, close(), Player::closeInventory);
    }

    private static ItemStack back() {
        return Menu.icon(Material.ARROW, "<color:" + Ui.FAINT + "><bold>← Back</bold></color>");
    }

    private static ItemStack close() {
        return Menu.icon(Material.BARRIER, "<color:" + Ui.BAD + "><bold>Close</bold></color>");
    }
}
