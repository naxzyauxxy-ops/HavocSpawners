package dev.havoc.spawners.ui;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.config.Messages;
import dev.havoc.spawners.econ.SellResult;
import dev.havoc.spawners.feature.UpgradeTier;
import dev.havoc.spawners.spawner.ItemSig;
import dev.havoc.spawners.spawner.SpawnerData;
import dev.havoc.spawners.spawner.SpawnerItems;
import dev.havoc.spawners.spawner.VirtualStorage;
import dev.havoc.spawners.util.Numbers;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Every player-facing screen for a single spawner. */
public final class SpawnerUi {

    private static final int TYPES_PER_PAGE = 6;

    private final HavocSpawners plugin;

    public SpawnerUi(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ main

    public void openMain(Player player, SpawnerData spawner) {
        if (plugin.bedrock().useForms(player)) {
            plugin.bedrockUi().openMain(player, spawner);
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(Ui.icon(iconStack(spawner)));
        body.add(Ui.text("<color:" + Ui.ACCENT + "><bold>" + spawner.displayType() + "</bold></color>"
                + "  <color:" + Ui.FAINT + ">×" + Numbers.plain(spawner.stackSize()) + "</color>"));

        UpgradeTier tier = plugin.upgrades().tier(spawner.level());
        body.add(Ui.stat("Tier", "<color:" + Ui.ACCENT + ">" + tier.name() + "</color> <color:"
                + Ui.FAINT + ">(level " + spawner.level() + ")</color>"));

        long used = spawner.storage().usedSlots();
        body.add(Ui.stat("Storage", Numbers.compact(used) + " / " + Numbers.compact(spawner.maxSlots())
                + " slots  " + Ui.bar(spawner.fillRatio(), 10, fillColor(spawner.fillRatio()))));
        body.add(Ui.stat("Items held", Numbers.plain(spawner.storage().totalItems())
                + " <color:" + Ui.FAINT + ">across " + Numbers.plain(spawner.storage().pageCount())
                + " pages</color>"));
        body.add(Ui.stat("Experience", Numbers.plain(spawner.storedExp()) + " / "
                + Numbers.plain(spawner.maxStoredExp())));
        body.add(Ui.stat("Cycle", Numbers.duration(spawner.spawnDelayTicks() * 50L)
                + " <color:" + Ui.FAINT + ">· " + spawner.minMobs() + "-" + spawner.maxMobs()
                + " per cycle</color>"));
        body.add(Ui.stat("Status", statusLine(spawner)));
        if (plugin.settings().analyticsEnabled) {
            body.add(Ui.stat("Rate", Numbers.compact((long) plugin.analytics().itemsPerHour(spawner))
                    + " items/h  <color:" + Ui.FAINT + ">·</color>  "
                    + plugin.economy().format(plugin.analytics().moneyPerHour(spawner)) + "/h"));
        }

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(Ui.button("<color:" + Ui.ACCENT + ">▤ Storage</color>",
                "Browse and withdraw what this spawner produced", 100,
                p -> openStorage(p, spawner, 0)));
        buttons.add(Ui.button("<color:" + Ui.GOOD + ">✦ Claim XP</color>",
                "Take " + Numbers.plain(spawner.storedExp()) + " stored experience", 100,
                p -> claimExp(p, spawner)));
        buttons.add(Ui.button("<color:" + Ui.GOOD + ">$ Sell all</color>",
                "Sell everything in storage", 100, p -> openSell(p, spawner)));
        buttons.add(Ui.button("<color:" + Ui.ACCENT + ">⇅ Stack</color>",
                "Add or remove stacked spawners", 100, p -> openStack(p, spawner)));
        if (plugin.settings().upgradesEnabled) {
            buttons.add(Ui.button("<color:" + Ui.WARN + ">⬆ Upgrade</color>",
                    "Spend money to make this spawner better", 100, p -> openUpgrade(p, spawner)));
        }
        if (plugin.settings().automationEnabled) {
            buttons.add(Ui.button("<color:" + Ui.ACCENT + ">⚙ Automation</color>",
                    "Auto-sell and auto-collect", 100, p -> openAutomation(p, spawner)));
        }
        if (plugin.settings().networksEnabled) {
            buttons.add(Ui.button("<color:" + Ui.ACCENT + ">⛓ Network</color>",
                    "Group spawners and control them together", 100, p -> openNetwork(p, spawner)));
        }
        if (plugin.settings().analyticsEnabled) {
            buttons.add(Ui.button("<color:" + Ui.INK + ">📈 Analytics</color>",
                    "Production and earnings history", 100, p -> openAnalytics(p, spawner)));
        }

        DialogBase base = Ui.base("Havoc Spawner", body, List.of(), true);
        player.showDialog(Ui.multi(base, buttons, exitButton(), 2));
    }

    String statusLine(SpawnerData spawner) {
        if (spawner.stopped()) {
            return "<color:" + Ui.BAD + ">Stopped</color>";
        }
        if (spawner.atCapacity()) {
            return "<color:" + Ui.WARN + ">Full</color>";
        }
        if (!spawner.active()) {
            return "<color:" + Ui.FAINT + ">Idle - no player in range</color>";
        }
        return "<color:" + Ui.GOOD + ">Running</color>";
    }

    static String fillColor(double ratio) {
        if (ratio >= 0.95D) {
            return Ui.BAD;
        }
        return ratio >= 0.75D ? Ui.WARN : Ui.GOOD;
    }

    ItemStack iconStack(SpawnerData spawner) {
        Material icon = plugin.lootEngine().iconFor(spawner);
        return new ItemStack(icon == null || icon.isAir() ? Material.SPAWNER : icon);
    }

    private ActionButton exitButton() {
        return Ui.button("<color:" + Ui.FAINT + ">Close</color>", null, 90, player -> player.closeDialog());
    }

    private ActionButton backButton(SpawnerData spawner) {
        return Ui.button("<color:" + Ui.FAINT + ">← Back</color>", null, 90, p -> openMain(p, spawner));
    }

    // ------------------------------------------------------------------ storage

    public void openStorage(Player player, SpawnerData spawner, int page) {
        if (plugin.bedrock().useForms(player)) {
            plugin.bedrockUi().openStorage(player, spawner, page);
            return;
        }
        List<Map.Entry<ItemSig, Long>> entries = spawner.storage().orderedEntries();
        int pages = Math.max(1, (entries.size() + TYPES_PER_PAGE - 1) / TYPES_PER_PAGE);
        int current = Numbers.clamp(page, 0, pages - 1);

        List<DialogBody> body = new ArrayList<>();
        long used = spawner.storage().usedSlots();
        body.add(Ui.text("<color:" + Ui.ACCENT + "><bold>Storage</bold></color>  <color:" + Ui.FAINT + ">"
                + Numbers.compact(used) + "/" + Numbers.compact(spawner.maxSlots()) + " slots · "
                + Numbers.plain(spawner.storage().pageCount()) + " pages</color>"));
        body.add(Ui.text(Ui.bar(spawner.fillRatio(), 20, fillColor(spawner.fillRatio()))));

        if (entries.isEmpty()) {
            body.add(Ui.text("<color:" + Ui.FAINT + "><italic>Nothing stored yet.</italic></color>"));
        }

        List<ActionButton> buttons = new ArrayList<>();
        int start = current * TYPES_PER_PAGE;
        int end = Math.min(entries.size(), start + TYPES_PER_PAGE);
        for (int i = start; i < end; i++) {
            Map.Entry<ItemSig, Long> entry = entries.get(i);
            ItemSig sig = entry.getKey();
            long amount = entry.getValue();
            long stacks = (amount + sig.maxStack() - 1L) / sig.maxStack();
            double share = used <= 0L ? 0.0D : (double) stacks / (double) used;
            double unit = plugin.prices().priceOf(sig.template(), plugin.settings());

            body.add(Ui.item(sig.copy(Math.min(sig.maxStack(), (int) Math.min(amount, 64))),
                    "<color:" + Ui.INK + ">" + SpawnerItems.pretty(sig.material().name()) + "</color> "
                            + "<color:" + Ui.FAINT + ">×</color> <color:" + Ui.ACCENT + ">"
                            + Numbers.plain(amount) + "</color>"
                            + "\n<color:" + Ui.FAINT + ">" + Numbers.plain(stacks) + " stacks · "
                            + Numbers.percent(share) + " of storage"
                            + (unit > 0.0D ? " · worth " + plugin.economy().format(unit * amount) : " · unsellable")
                            + "</color>"));

            buttons.add(Ui.button("<color:" + Ui.INK + ">" + SpawnerItems.pretty(sig.material().name())
                            + "</color> <color:" + Ui.FAINT + ">(" + Numbers.compact(amount) + ")</color>",
                    "Withdraw, sell or filter this item", 150,
                    p -> openItemActions(p, spawner, sig)));
        }

        if (pages > 1) {
            buttons.add(Ui.button(current > 0 ? "<color:" + Ui.ACCENT + ">◀ Previous</color>"
                            : "<color:" + Ui.FAINT + ">◀ Previous</color>",
                    "Page " + (current + 1) + " of " + pages, 90,
                    p -> openStorage(p, spawner, current - 1)));
            buttons.add(Ui.button(current < pages - 1 ? "<color:" + Ui.ACCENT + ">Next ▶</color>"
                            : "<color:" + Ui.FAINT + ">Next ▶</color>",
                    "Page " + (current + 1) + " of " + pages, 90,
                    p -> openStorage(p, spawner, current + 1)));
        }

        buttons.add(Ui.button("<color:" + Ui.WARN + ">⇩ Drop a page</color>",
                "Throws 45 stacks out where you are looking - the screen stays open so you can keep going",
                150, p -> dropOnePage(p, spawner, current)));
        buttons.add(Ui.button("<color:" + Ui.WARN + ">⇩ Bulk withdraw</color>",
                "Empty many storage pages at once", 150, p -> openBulkDrop(p, spawner)));
        buttons.add(Ui.button("<color:" + Ui.GOOD + ">$ Sell everything</color>",
                "Sell all priced items", 150, p -> openSell(p, spawner)));
        buttons.add(Ui.button("<color:" + Ui.INK + ">⚗ Filters</color>",
                "Choose which drops are discarded", 150, p -> openFilters(p, spawner, 0)));

        DialogBase base = Ui.base("Storage · " + spawner.displayType(), body, List.of(), true);
        player.showDialog(Ui.multi(base, buttons, backButton(spawner), 2));
    }

    public void openItemActions(Player player, SpawnerData spawner, ItemSig sig) {
        if (plugin.bedrock().useForms(player)) {
            plugin.bedrockUi().openItemActions(player, spawner, sig);
            return;
        }
        long amount = spawner.storage().countOf(sig);
        double unit = plugin.prices().priceOf(sig.template(), plugin.settings());
        String name = SpawnerItems.pretty(sig.material().name());

        List<DialogBody> body = new ArrayList<>();
        body.add(Ui.icon(sig.copy(Math.min(sig.maxStack(), 64))));
        body.add(Ui.stat("Stored", Numbers.plain(amount)));
        body.add(Ui.stat("Unit price", unit > 0.0D ? plugin.economy().format(unit)
                : "<color:" + Ui.BAD + ">not sellable</color>"));
        if (unit > 0.0D) {
            body.add(Ui.stat("Total value", plugin.economy().format(unit * amount)));
        }
        body.add(Ui.stat("Filtered", spawner.filtered().contains(sig.material())
                ? "<color:" + Ui.BAD + ">yes - new drops are discarded</color>"
                : "<color:" + Ui.GOOD + ">no</color>"));

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(Ui.button("<color:" + Ui.ACCENT + ">Take one stack</color>", null, 130,
                p -> withdraw(p, spawner, sig, sig.maxStack())));
        buttons.add(Ui.button("<color:" + Ui.ACCENT + ">Fill my inventory</color>", null, 130,
                p -> withdraw(p, spawner, sig, sig.maxStack() * 36)));
        buttons.add(Ui.button("<color:" + Ui.WARN + ">⇩ Drop all on ground</color>",
                "Throws every " + name + " out where you are looking", 130, p -> {
                    if (plugin.dropService().isRunning(spawner)) {
                        plugin.messages().send(p, "bulk-drop.busy");
                        return;
                    }
                    // Stay on screen; the item is gone afterwards, so land back on storage.
                    boolean started = plugin.dropService().dropItemToGround(p, spawner, sig,
                            () -> reopenStorage(p, spawner, 0));
                    if (!started) {
                        plugin.messages().send(p, "bulk-drop.nothing");
                    }
                }));
        if (unit > 0.0D && plugin.settings().economyEnabled) {
            buttons.add(Ui.button("<color:" + Ui.GOOD + ">Sell all " + name + "</color>", null, 130,
                    p -> sellOne(p, spawner, sig)));
        }
        buttons.add(Ui.button(spawner.filtered().contains(sig.material())
                        ? "<color:" + Ui.GOOD + ">Stop filtering</color>"
                        : "<color:" + Ui.BAD + ">Filter out</color>",
                "Filtered drops are never stored", 130,
                p -> {
                    if (!spawner.filtered().remove(sig.material())) {
                        spawner.filtered().add(sig.material());
                    }
                    spawner.markDirty();
                    plugin.storage().queueSave(spawner);
                    openItemActions(p, spawner, sig);
                }));
        buttons.add(Ui.button("<color:" + Ui.INK + ">Sort to top</color>",
                "Show this item first in storage", 130,
                p -> {
                    spawner.preferredSort(sig.material());
                    spawner.storage().sortPreferring(sig.material());
                    plugin.storage().queueSave(spawner);
                    openStorage(p, spawner, 0);
                }));

        DialogBase base = Ui.base(name, body, List.of(), true);
        player.showDialog(Ui.multi(base, buttons,
                Ui.button("<color:" + Ui.FAINT + ">← Storage</color>", null, 90,
                        p -> openStorage(p, spawner, 0)), 2));
    }

    void withdraw(Player player, SpawnerData spawner, ItemSig sig, long maxAmount) {
        long available = spawner.storage().countOf(sig);
        long wanted = Math.min(available, maxAmount);
        if (wanted <= 0L) {
            plugin.messages().send(player, "storage.empty");
            return;
        }
        long taken = spawner.storage().remove(sig, wanted);
        long delivered = 0L;
        long remaining = taken;
        List<ItemStack> batch = new ArrayList<>();
        while (remaining > 0L && batch.size() < 36) {
            int size = (int) Math.min(remaining, sig.maxStack());
            batch.add(sig.copy(size));
            remaining -= size;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(batch.toArray(new ItemStack[0]));
        long returned = remaining;
        for (ItemStack stack : leftovers.values()) {
            if (stack != null) {
                returned += stack.getAmount();
            }
        }
        delivered = taken - returned;
        if (returned > 0L) {
            spawner.storage().addUnchecked(sig, returned);
        }
        plugin.storage().queueSave(spawner);
        plugin.messages().send(player, "storage.withdrew", Messages.of(
                "amount", Numbers.plain(delivered),
                "item", SpawnerItems.pretty(sig.material().name())));
        openItemActions(player, spawner, sig);
    }

    void sellOne(Player player, SpawnerData spawner, ItemSig sig) {
        if (!player.hasPermission("havocspawners.sell")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (!plugin.economy().available()) {
            plugin.messages().send(player, "economy.unavailable");
            return;
        }
        long amount = spawner.storage().countOf(sig);
        double unit = plugin.prices().priceOf(sig.template(), plugin.settings());
        if (amount <= 0L || unit <= 0.0D) {
            plugin.messages().send(player, "sell.nothing");
            return;
        }
        long removed = spawner.storage().remove(sig, amount);
        double gross = removed * unit;
        double net = gross - gross * (plugin.settings().taxPercent / 100.0D);
        plugin.economy().deposit(player.getUniqueId(), net);
        spawner.addEarnedMoney(net);
        plugin.analytics().recordEarnings(spawner, net);
        plugin.storage().queueSave(spawner);
        plugin.messages().send(player, "sell.success", Messages.of(
                "items", Numbers.plain(removed),
                "money", plugin.economy().format(net)));
        openStorage(player, spawner, 0);
    }

    // ------------------------------------------------------------------ bulk drop

    public void openBulkDrop(Player player, SpawnerData spawner) {
        if (plugin.bedrock().useForms(player)) {
            plugin.bedrockUi().openBulkDrop(player, spawner);
            return;
        }
        if (!player.hasPermission("havocspawners.bulkdrop")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        int pages = spawner.storage().pageCount();
        long items = spawner.storage().totalItems();

        List<DialogBody> body = new ArrayList<>();
        body.add(Ui.text("<color:" + Ui.WARN + "><bold>Bulk withdraw</bold></color>"));
        body.add(Ui.text("<color:" + Ui.FAINT + ">Pick a page range and everything inside it is handed to you"
                + " a few stacks per tick, so the server never stalls.</color>"));
        body.add(Ui.stat("Pages held", Numbers.plain(pages)));
        body.add(Ui.stat("Items held", Numbers.plain(items)));
        body.add(Ui.stat("Delivery", plugin.settings().stacksPerTick + " stacks/tick · max "
                + Numbers.plain(plugin.settings().maxItemEntities) + " ground items"));
        if (plugin.dropService().isRunning(spawner)) {
            body.add(Ui.text("<color:" + Ui.BAD + ">A withdrawal is already running on this spawner.</color>"));
        }

        List<DialogInput> inputs = new ArrayList<>();
        if (pages > 1) {
            inputs.add(DialogInput.numberRange("from", Ui.line("First page"), 1.0F, pages)
                    .width(280)
                    .labelFormat("%s: %s")
                    .initial(1.0F)
                    .step(1.0F)
                    .build());
            inputs.add(DialogInput.numberRange("to", Ui.line("Last page"), 1.0F, pages)
                    .width(280)
                    .labelFormat("%s: %s")
                    .initial((float) pages)
                    .step(1.0F)
                    .build());
        }
        inputs.add(DialogInput.bool("inv", Ui.line("Into my inventory instead of the ground"))
                .initial(plugin.settings().preferPlayerInventory)
                .onTrue("true")
                .onFalse("false")
                .build());

        List<ActionButton> buttons = new ArrayList<>();
        if (pages > 1) {
            buttons.add(Ui.input("<color:" + Ui.WARN + ">⇩ Withdraw range</color>",
                    "Drain the selected pages", 150, (p, response) -> {
                        Float from = response.getFloat("from");
                        Float to = response.getFloat("to");
                        int first = from == null ? 1 : Math.round(from);
                        int last = to == null ? pages : Math.round(to);
                        runBulk(p, spawner, first - 1, last - 1, toInventory(response));
                    }));
        }
        buttons.add(Ui.input("<color:" + Ui.BAD + ">⇩⇩ Withdraw everything</color>",
                Numbers.plain(items) + " items", 150, (p, response) -> {
                    p.closeDialog();
                    if (plugin.dropService().isRunning(spawner)) {
                        plugin.messages().send(p, "bulk-drop.busy");
                        return;
                    }
                    if (!plugin.dropService().dropAll(p, spawner, toInventory(response))) {
                        plugin.messages().send(p, "bulk-drop.nothing");
                        return;
                    }
                    plugin.messages().send(p, "bulk-drop.started", Messages.of(
                            "pages", Numbers.plain(pages)));
                }));

        DialogBase base = Ui.base("Bulk withdraw", body, inputs, false);
        player.showDialog(Ui.multi(base, buttons,
                Ui.button("<color:" + Ui.FAINT + ">← Storage</color>", null, 90,
                        p -> openStorage(p, spawner, 0)), 1));
    }

    /** Reads the "into my inventory" toggle, falling back to the configured default. */
    private boolean toInventory(io.papermc.paper.dialog.DialogResponseView response) {
        Boolean value = response.getBoolean("inv");
        return value == null ? plugin.settings().preferPlayerInventory : value;
    }

    /**
     * Throws one page out - the old plugin's drop button.
     * <p>
     * The dialog is deliberately left open and refreshed once the throw finishes, so a player can
     * sit on the storage screen and empty page after page without reopening it every time.
     */
    void dropOnePage(Player player, SpawnerData spawner, int storagePage) {
        if (!player.hasPermission("havocspawners.bulkdrop")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (plugin.dropService().isRunning(spawner)) {
            plugin.messages().send(player, "bulk-drop.busy");
            return;
        }
        boolean started = plugin.dropService().dropPages(player, spawner, 0, 0, false,
                () -> reopenStorage(player, spawner, storagePage));
        if (!started) {
            plugin.messages().send(player, "bulk-drop.nothing");
        }
    }

    /** Redraws the storage screen in place, clamped in case the page count shrank. */
    void reopenStorage(Player player, SpawnerData spawner, int page) {
        if (!player.isOnline()) {
            return;
        }
        openStorage(player, spawner, page);
    }

    void runBulk(Player player, SpawnerData spawner, int firstPage, int lastPage,
                         boolean toInventory) {
        player.closeDialog();
        if (plugin.dropService().isRunning(spawner)) {
            plugin.messages().send(player, "bulk-drop.busy");
            return;
        }
        boolean started = plugin.dropService().dropPages(player, spawner, firstPage, lastPage, toInventory);
        if (!started) {
            plugin.messages().send(player, "bulk-drop.nothing");
            return;
        }
        plugin.messages().send(player, "bulk-drop.started", Messages.of(
                "pages", Numbers.plain(lastPage - firstPage + 1)));
    }

    // ------------------------------------------------------------------ sell

    public void openSell(Player player, SpawnerData spawner) {
        if (plugin.bedrock().useForms(player)) {
            plugin.bedrockUi().openSell(player, spawner);
            return;
        }
        if (!player.hasPermission("havocspawners.sell")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (!plugin.settings().economyEnabled || !plugin.economy().available()) {
            plugin.messages().send(player, "economy.unavailable");
            return;
        }
        SellResult preview = plugin.sell().preview(spawner);

        List<DialogBody> body = new ArrayList<>();
        body.add(Ui.text("<color:" + Ui.GOOD + "><bold>Sell storage</bold></color>"));
        body.add(Ui.stat("Items", Numbers.plain(preview.itemsSold())));
        body.add(Ui.stat("Gross", plugin.economy().format(preview.gross())));
        if (plugin.settings().taxPercent > 0.0D) {
            body.add(Ui.stat("Tax", plugin.economy().format(preview.tax())
                    + " <color:" + Ui.FAINT + ">(" + Numbers.percent(plugin.settings().taxPercent / 100.0D)
                    + ")</color>"));
        }
        body.add(Ui.stat("You receive", "<color:" + Ui.GOOD + ">"
                + plugin.economy().format(preview.net()) + "</color>"));
        if (preview.unsellableItems() > 0L) {
            body.add(Ui.text("<color:" + Ui.FAINT + ">" + Numbers.plain(preview.unsellableItems())
                    + " items have no price and stay in storage.</color>"));
        }

        int shown = 0;
        for (Map.Entry<Material, Long> entry : preview.soldByMaterial().entrySet()) {
            if (shown++ >= 5) {
                break;
            }
            body.add(Ui.item(new ItemStack(entry.getKey()),
                    "<color:" + Ui.INK + ">" + SpawnerItems.pretty(entry.getKey().name()) + "</color> "
                            + "<color:" + Ui.FAINT + ">×" + Numbers.plain(entry.getValue()) + " → </color>"
                            + "<color:" + Ui.GOOD + ">"
                            + plugin.economy().format(preview.valueByMaterial()
                            .getOrDefault(entry.getKey(), 0.0D)) + "</color>"));
        }

        DialogBase base = Ui.base("Confirm sale", body, List.of(), false);
        ActionButton yes = Ui.button("<color:" + Ui.GOOD + ">Sell for "
                + plugin.economy().format(preview.net()) + "</color>", null, 160, p -> {
            SellResult result = plugin.sell().sellAll(spawner, p.getUniqueId());
            if (result.isEmpty()) {
                plugin.messages().send(p, "sell.nothing");
            } else {
                plugin.messages().send(p, "sell.success", Messages.of(
                        "items", Numbers.plain(result.itemsSold()),
                        "money", plugin.economy().format(result.net())));
            }
            openMain(p, spawner);
        });
        ActionButton no = Ui.button("<color:" + Ui.FAINT + ">Cancel</color>", null, 160,
                p -> openMain(p, spawner));
        player.showDialog(Ui.confirm(base, yes, no));
    }

    void claimExp(Player player, SpawnerData spawner) {
        long exp = spawner.storedExp();
        if (exp <= 0L) {
            plugin.messages().send(player, "exp.empty");
            openMain(player, spawner);
            return;
        }
        spawner.storedExp(0L);
        plugin.storage().queueSave(spawner);
        player.giveExp((int) Math.min(Integer.MAX_VALUE, exp), plugin.settings().allowExpMending);
        plugin.messages().send(player, "exp.claimed", Messages.of("exp", Numbers.plain(exp)));
        openMain(player, spawner);
    }

    // ------------------------------------------------------------------ stack

    public void openStack(Player player, SpawnerData spawner) {
        if (plugin.bedrock().useForms(player)) {
            plugin.bedrockUi().openStack(player, spawner);
            return;
        }
        if (!player.hasPermission("havocspawners.stack")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        int inHand = countMatchingInInventory(player, spawner);

        List<DialogBody> body = new ArrayList<>();
        body.add(Ui.icon(iconStack(spawner)));
        body.add(Ui.stat("Stacked", Numbers.plain(spawner.stackSize()) + " / "
                + Numbers.plain(spawner.maxStackSize())));
        body.add(Ui.stat("In your inventory", Numbers.plain(inHand) + " matching spawners"));
        body.add(Ui.text("<color:" + Ui.FAINT + ">Stacking multiplies mob simulation and storage capacity.</color>"));

        int max = Math.max(1, Math.min(64, Math.max(inHand, spawner.stackSize())));
        List<DialogInput> inputs = List.of(
                DialogInput.numberRange("amount", Ui.line("Amount"), 1.0F, max)
                        .width(280).labelFormat("%s: %s").initial(1.0F).step(1.0F).build());

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(Ui.input("<color:" + Ui.GOOD + ">＋ Add from inventory</color>", null, 150,
                (p, response) -> {
                    Float value = response.getFloat("amount");
                    changeStack(p, spawner, value == null ? 1 : Math.round(value));
                }));
        buttons.add(Ui.input("<color:" + Ui.WARN + ">− Remove to inventory</color>", null, 150,
                (p, response) -> {
                    Float value = response.getFloat("amount");
                    changeStack(p, spawner, -(value == null ? 1 : Math.round(value)));
                }));

        DialogBase base = Ui.base("Stack manager", body, inputs, false);
        player.showDialog(Ui.multi(base, buttons, backButton(spawner), 1));
    }

    int countMatchingInInventory(Player player, SpawnerData spawner) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || !plugin.items().isHavocSpawner(stack)) {
                continue;
            }
            if (matches(stack, spawner)) {
                count += stack.getAmount() * plugin.items().readStackSize(stack);
            }
        }
        return count;
    }

    private boolean matches(ItemStack stack, SpawnerData spawner) {
        if (spawner.isItemSpawner()) {
            return spawner.itemMaterial() == plugin.items().readItemMaterial(stack);
        }
        return spawner.entityType() == plugin.items().readEntityType(stack);
    }

    void changeStack(Player player, SpawnerData spawner, int delta) {
        if (delta == 0) {
            return;
        }
        if (delta > 0) {
            int available = countMatchingInInventory(player, spawner);
            int room = spawner.maxStackSize() - spawner.stackSize();
            int applied = Math.min(delta, Math.min(available, room));
            if (applied <= 0) {
                plugin.messages().send(player, "stack.cannot-add");
                return;
            }
            removeMatching(player, spawner, applied);
            spawner.stackSize(spawner.stackSize() + applied);
            spawner.recompute(plugin.settings(), plugin.upgrades());
            plugin.storage().queueSave(spawner);
            plugin.messages().send(player, "stack.added", Messages.of(
                    "amount", Numbers.plain(applied), "total", Numbers.plain(spawner.stackSize())));
        } else {
            int applied = Math.min(-delta, spawner.stackSize() - 1);
            if (applied <= 0) {
                plugin.messages().send(player, "stack.cannot-remove");
                return;
            }
            ItemStack give = plugin.items().create(spawner.entityType(), spawner.itemMaterial(),
                    1, spawner.level(), Math.min(applied, 64));
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(give);
            int returned = 0;
            for (ItemStack stack : leftovers.values()) {
                returned += stack == null ? 0 : stack.getAmount();
            }
            int actually = applied - returned;
            if (actually <= 0) {
                plugin.messages().send(player, "inventory-full");
                return;
            }
            spawner.stackSize(spawner.stackSize() - actually);
            spawner.recompute(plugin.settings(), plugin.upgrades());
            plugin.storage().queueSave(spawner);
            plugin.messages().send(player, "stack.removed", Messages.of(
                    "amount", Numbers.plain(actually), "total", Numbers.plain(spawner.stackSize())));
        }
        openStack(player, spawner);
    }

    private void removeMatching(Player player, SpawnerData spawner, int amount) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !plugin.items().isHavocSpawner(stack) || !matches(stack, spawner)) {
                continue;
            }
            int perItem = Math.max(1, plugin.items().readStackSize(stack));
            int itemsNeeded = (int) Math.ceil(left / (double) perItem);
            int take = Math.min(stack.getAmount(), itemsNeeded);
            left -= take * perItem;
            if (take >= stack.getAmount()) {
                player.getInventory().setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
                player.getInventory().setItem(i, stack);
            }
        }
    }

    // ------------------------------------------------------------------ upgrades

    public void openUpgrade(Player player, SpawnerData spawner) {
        if (plugin.bedrock().useForms(player)) {
            plugin.bedrockUi().openUpgrade(player, spawner);
            return;
        }
        if (!player.hasPermission("havocspawners.upgrade")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        UpgradeTier current = plugin.upgrades().tier(spawner.level());
        UpgradeTier next = plugin.upgrades().next(spawner.level());

        List<DialogBody> body = new ArrayList<>();
        body.add(Ui.icon(iconStack(spawner)));
        body.add(Ui.text("<color:" + Ui.ACCENT + "><bold>" + current.name() + "</bold></color>"
                + " <color:" + Ui.FAINT + ">level " + spawner.level() + "</color>"));
        body.add(Ui.stat("Cycle time", Numbers.duration(spawner.spawnDelayTicks() * 50L)));
        body.add(Ui.stat("Loot multiplier", "×" + current.lootMultiplier()));
        body.add(Ui.stat("Storage", Numbers.plain(spawner.maxPages()) + " pages"));
        body.add(Ui.stat("XP capacity", Numbers.plain(spawner.maxStoredExp())));

        List<ActionButton> buttons = new ArrayList<>();
        if (next == null) {
            body.add(Ui.text("<color:" + Ui.GOOD + ">This spawner is fully upgraded.</color>"));
        } else {
            body.add(Ui.text("<color:" + Ui.FAINT + ">─────────────────</color>"));
            body.add(Ui.text("<color:" + Ui.WARN + "><bold>Next: " + next.name() + "</bold></color>"));
            body.add(Ui.stat("Speed", "×" + next.delayMultiplier() + " cycle time"));
            body.add(Ui.stat("Loot", "×" + next.lootMultiplier()));
            body.add(Ui.stat("Extra pages", "+" + next.bonusPages()));
            body.add(Ui.stat("Extra XP cap", "+" + Numbers.plain(next.bonusExpCapacity())));
            body.add(Ui.stat("Cost", plugin.economy().format(next.cost())));
            body.add(Ui.stat("Your balance", plugin.economy().format(
                    plugin.economy().balance(player.getUniqueId()))));

            buttons.add(Ui.button("<color:" + Ui.GOOD + ">⬆ Upgrade to " + next.name() + "</color>",
                    plugin.economy().format(next.cost()), 190, p -> {
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
                    }));
        }

        DialogBase base = Ui.base("Upgrades", body, List.of(), true);
        player.showDialog(Ui.multi(base, buttons, backButton(spawner), 1));
    }

    // ------------------------------------------------------------------ automation

    public void openAutomation(Player player, SpawnerData spawner) {
        if (plugin.bedrock().useForms(player)) {
            plugin.bedrockUi().openAutomation(player, spawner);
            return;
        }
        if (!player.hasPermission("havocspawners.automation")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(Ui.text("<color:" + Ui.ACCENT + "><bold>Automation</bold></color>"));
        body.add(Ui.text("<color:" + Ui.FAINT + ">Runs every "
                + plugin.settings().automationIntervalSeconds + "s, even while you are offline.</color>"));
        body.add(Ui.stat("Auto-sell", spawner.autoSell()
                ? "<color:" + Ui.GOOD + ">on</color>" : "<color:" + Ui.FAINT + ">off</color>"));
        body.add(Ui.stat("Auto-collect", spawner.autoCollect()
                ? "<color:" + Ui.GOOD + ">on</color>" : "<color:" + Ui.FAINT + ">off</color>"));
        body.add(Ui.stat("Linked container", spawner.linkedContainer() == null
                ? "<color:" + Ui.FAINT + ">none</color>"
                : "<color:" + Ui.INK + ">" + spawner.linkedContainer() + "</color>"));
        body.add(Ui.stat("Earned so far", plugin.economy().format(spawner.earnedMoney())));

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(Ui.button(spawner.autoSell()
                        ? "<color:" + Ui.BAD + ">Disable auto-sell</color>"
                        : "<color:" + Ui.GOOD + ">Enable auto-sell</color>",
                "Sells priced drops straight into your balance", 170, p -> {
                    spawner.autoSell(!spawner.autoSell());
                    plugin.storage().queueSave(spawner);
                    openAutomation(p, spawner);
                }));
        buttons.add(Ui.button(spawner.autoCollect()
                        ? "<color:" + Ui.BAD + ">Disable auto-collect</color>"
                        : "<color:" + Ui.GOOD + ">Enable auto-collect</color>",
                "Pushes drops into the linked container", 170, p -> {
                    if (!spawner.autoCollect() && spawner.linkedContainer() == null) {
                        plugin.messages().send(p, "automation.link-first");
                        return;
                    }
                    spawner.autoCollect(!spawner.autoCollect());
                    plugin.storage().queueSave(spawner);
                    openAutomation(p, spawner);
                }));
        buttons.add(Ui.button("<color:" + Ui.ACCENT + ">⛓ Link a container</color>",
                "Then right-click a chest within " + plugin.settings().autoCollectRadius + " blocks", 170,
                p -> {
                    plugin.beginLinking(p, spawner);
                    p.closeDialog();
                    plugin.messages().send(p, "automation.link-mode");
                }));
        if (spawner.linkedContainer() != null) {
            buttons.add(Ui.button("<color:" + Ui.WARN + ">Unlink container</color>", null, 170, p -> {
                spawner.linkedContainer(null);
                spawner.autoCollect(false);
                plugin.storage().queueSave(spawner);
                openAutomation(p, spawner);
            }));
        }

        DialogBase base = Ui.base("Automation", body, List.of(), true);
        player.showDialog(Ui.multi(base, buttons, backButton(spawner), 1));
    }

    // ------------------------------------------------------------------ networks

    public void openNetwork(Player player, SpawnerData spawner) {
        if (plugin.bedrock().useForms(player)) {
            plugin.bedrockUi().openNetwork(player, spawner);
            return;
        }
        if (!player.hasPermission("havocspawners.network")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        List<String> names = plugin.networks().namesFor(player.getUniqueId());

        List<DialogBody> body = new ArrayList<>();
        body.add(Ui.text("<color:" + Ui.ACCENT + "><bold>Spawner networks</bold></color>"));
        body.add(Ui.text("<color:" + Ui.FAINT + ">Group spawners so one button sells, collects or drains"
                + " all of them.</color>"));
        body.add(Ui.stat("This spawner", spawner.network() == null
                ? "<color:" + Ui.FAINT + ">unassigned</color>"
                : "<color:" + Ui.ACCENT + ">" + spawner.network() + "</color>"));
        body.add(Ui.stat("Your networks", names.isEmpty() ? "none" : String.join(", ", names)));

        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text("name", Ui.line("New network name"))
                .width(280).labelVisible(true).initial("").maxLength(24).build());
        if (!names.isEmpty()) {
            List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                options.add(SingleOptionDialogInput.OptionEntry.create(
                        names.get(i), Ui.line("<color:" + Ui.INK + ">" + names.get(i) + "</color>"), i == 0));
            }
            inputs.add(DialogInput.singleOption("pick", Ui.line("Network"), options)
                    .width(280).labelVisible(true).build());
        }

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(Ui.input("<color:" + Ui.GOOD + ">✚ Create network</color>", null, 160, (p, response) -> {
            String name = response.getText("name");
            String created = plugin.networks().create(p.getUniqueId(), name);
            if (created == null) {
                plugin.messages().send(p, "network.create-failed");
            } else {
                plugin.messages().send(p, "network.created", Messages.of("name", created));
            }
            openNetwork(p, spawner);
        }));
        if (!names.isEmpty()) {
            buttons.add(Ui.input("<color:" + Ui.ACCENT + ">⛓ Assign this spawner</color>", null, 160,
                    (p, response) -> {
                        String pick = response.getText("pick");
                        if (pick == null || !plugin.networks().assign(spawner, p.getUniqueId(), pick)) {
                            plugin.messages().send(p, "network.assign-failed");
                        } else {
                            plugin.messages().send(p, "network.assigned", Messages.of("name", pick));
                        }
                        openNetwork(p, spawner);
                    }));
            buttons.add(Ui.input("<color:" + Ui.INK + ">▤ Open network</color>", null, 160, (p, response) -> {
                String pick = response.getText("pick");
                if (pick != null) {
                    openNetworkOverview(p, pick, spawner);
                }
            }));
            buttons.add(Ui.input("<color:" + Ui.BAD + ">✖ Delete network</color>", null, 160, (p, response) -> {
                String pick = response.getText("pick");
                if (pick != null) {
                    plugin.networks().delete(p.getUniqueId(), pick);
                    plugin.messages().send(p, "network.deleted", Messages.of("name", pick));
                }
                openNetwork(p, spawner);
            }));
        }
        if (spawner.network() != null) {
            buttons.add(Ui.button("<color:" + Ui.WARN + ">Remove from network</color>", null, 160, p -> {
                plugin.networks().assign(spawner, p.getUniqueId(), null);
                openNetwork(p, spawner);
            }));
        }

        DialogBase base = Ui.base("Networks", body, inputs, false);
        player.showDialog(Ui.multi(base, buttons, backButton(spawner), 2));
    }

    public void openNetworkOverview(Player player, String network, SpawnerData origin) {
        if (plugin.bedrock().useForms(player)) {
            plugin.bedrockUi().openNetworkOverview(player, network, origin);
            return;
        }
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

        List<DialogBody> body = new ArrayList<>();
        body.add(Ui.text("<color:" + Ui.ACCENT + "><bold>" + network + "</bold></color>"));
        body.add(Ui.stat("Spawners", Numbers.plain(members.size())));
        body.add(Ui.stat("Items held", Numbers.plain(items)));
        body.add(Ui.stat("Storage", Numbers.compact(slots) + " / " + Numbers.compact(capacity) + " slots  "
                + Ui.bar(capacity <= 0 ? 0 : (double) slots / capacity, 14, Ui.ACCENT)));
        body.add(Ui.stat("Stored XP", Numbers.plain(exp)));
        body.add(Ui.stat("Sell value", plugin.economy().format(value)));

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(Ui.button("<color:" + Ui.GOOD + ">$ Sell whole network</color>",
                plugin.economy().format(value), 180, p -> {
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
                }));
        buttons.add(Ui.button("<color:" + Ui.GOOD + ">✦ Claim all XP</color>",
                Numbers.plain(exp) + " experience", 180, p -> {
                    long claimed = 0L;
                    for (SpawnerData member : members) {
                        claimed += member.storedExp();
                        member.storedExp(0L);
                        plugin.storage().queueSave(member);
                    }
                    if (claimed > 0L) {
                        p.giveExp((int) Math.min(Integer.MAX_VALUE, claimed),
                                plugin.settings().allowExpMending);
                    }
                    plugin.messages().send(p, "exp.claimed", Messages.of("exp", Numbers.plain(claimed)));
                    openNetworkOverview(p, network, origin);
                }));
        buttons.add(Ui.button("<color:" + Ui.WARN + ">⇩ Drain everything to me</color>",
                "Withdraws every spawner in the network, metered so the server stays smooth", 180, p -> {
                    p.closeDialog();
                    int started = 0;
                    for (SpawnerData member : members) {
                        if (plugin.dropService().dropAll(p, member)) {
                            started++;
                        }
                    }
                    plugin.messages().send(p, "network.drain-started", Messages.of(
                            "count", Numbers.plain(started)));
                }));
        buttons.add(Ui.button("<color:" + Ui.INK + ">⚙ Toggle auto-sell for all</color>", null, 180, p -> {
            boolean enable = members.stream().anyMatch(m -> !m.autoSell());
            for (SpawnerData member : members) {
                member.autoSell(enable);
                plugin.storage().queueSave(member);
            }
            plugin.messages().send(p, enable ? "network.autosell-on" : "network.autosell-off");
            openNetworkOverview(p, network, origin);
        }));

        DialogBase base = Ui.base("Network · " + network, body, List.of(), true);
        ActionButton back = origin == null
                ? Ui.button("<color:" + Ui.FAINT + ">Close</color>", null, 90, Player::closeDialog)
                : Ui.button("<color:" + Ui.FAINT + ">← Back</color>", null, 90, p -> openNetwork(p, origin));
        player.showDialog(Ui.multi(base, buttons, back, 1));
    }

    // ------------------------------------------------------------------ analytics

    public void openAnalytics(Player player, SpawnerData spawner) {
        if (plugin.bedrock().useForms(player)) {
            plugin.bedrockUi().openAnalytics(player, spawner);
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        int hours = plugin.settings().analyticsHistoryHours;
        body.add(Ui.icon(iconStack(spawner)));
        body.add(Ui.text("<color:" + Ui.ACCENT + "><bold>Analytics</bold></color> <color:" + Ui.FAINT
                + ">last " + hours + "h</color>"));
        body.add(Ui.stat("Items produced", Numbers.plain(plugin.analytics().itemsInWindow(spawner))
                + " <color:" + Ui.FAINT + ">(" + Numbers.compact((long) plugin.analytics().itemsPerHour(spawner))
                + "/h)</color>"));
        body.add(Ui.stat("Earnings", plugin.economy().format(plugin.analytics().moneyInWindow(spawner))
                + " <color:" + Ui.FAINT + ">(" + plugin.economy().format(
                plugin.analytics().moneyPerHour(spawner)) + "/h)</color>"));
        body.add(Ui.text("<color:" + Ui.FAINT + ">─────────────────</color>"));
        body.add(Ui.stat("Lifetime items", Numbers.plain(spawner.producedItems())));
        body.add(Ui.stat("Lifetime XP", Numbers.plain(spawner.producedExp())));
        body.add(Ui.stat("Lifetime earnings", plugin.economy().format(spawner.earnedMoney())));
        body.add(Ui.stat("Placed", Numbers.duration(System.currentTimeMillis() - spawner.createdAt()) + " ago"));
        body.add(Ui.stat("Owner", spawner.ownerName() == null ? "unknown" : spawner.ownerName()));
        body.add(Ui.stat("Location", spawner.position().toString()));

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(Ui.button("<color:" + Ui.INK + ">🏆 Your top spawners</color>", null, 170,
                p -> plugin.adminUi().openLeaderboard(p, p.getUniqueId())));

        DialogBase base = Ui.base("Analytics", body, List.of(), true);
        player.showDialog(Ui.multi(base, buttons, backButton(spawner), 1));
    }

    // ------------------------------------------------------------------ filters

    public void openFilters(Player player, SpawnerData spawner, int page) {
        if (plugin.bedrock().useForms(player)) {
            plugin.bedrockUi().openFilters(player, spawner, page);
            return;
        }
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

        int perPage = 8;
        int pages = Math.max(1, (candidates.size() + perPage - 1) / perPage);
        int current = Numbers.clamp(page, 0, pages - 1);

        List<DialogBody> body = new ArrayList<>();
        body.add(Ui.text("<color:" + Ui.ACCENT + "><bold>Drop filters</bold></color>"));
        body.add(Ui.text("<color:" + Ui.FAINT + ">Filtered drops are discarded the moment they are"
                + " generated - useful for cobblestone, poppies and other junk.</color>"));
        body.add(Ui.stat("Filtered", spawner.filtered().isEmpty() ? "nothing"
                : Numbers.plain(spawner.filtered().size()) + " materials"));

        List<ActionButton> buttons = new ArrayList<>();
        int start = current * perPage;
        int end = Math.min(candidates.size(), start + perPage);
        for (int i = start; i < end; i++) {
            Material material = candidates.get(i);
            boolean filtered = spawner.filtered().contains(material);
            buttons.add(Ui.button((filtered ? "<color:" + Ui.BAD + ">✖ " : "<color:" + Ui.GOOD + ">✔ ")
                            + SpawnerItems.pretty(material.name()) + "</color>",
                    filtered ? "Currently discarded" : "Currently kept", 150, p -> {
                        if (!spawner.filtered().remove(material)) {
                            spawner.filtered().add(material);
                        }
                        spawner.markDirty();
                        plugin.storage().queueSave(spawner);
                        openFilters(p, spawner, current);
                    }));
        }
        if (pages > 1) {
            buttons.add(Ui.button("<color:" + Ui.ACCENT + ">◀ Previous</color>", null, 90,
                    p -> openFilters(p, spawner, current - 1)));
            buttons.add(Ui.button("<color:" + Ui.ACCENT + ">Next ▶</color>", null, 90,
                    p -> openFilters(p, spawner, current + 1)));
        }
        if (!spawner.filtered().isEmpty()) {
            buttons.add(Ui.button("<color:" + Ui.GOOD + ">Clear all filters</color>", null, 150, p -> {
                spawner.filtered().clear();
                spawner.markDirty();
                plugin.storage().queueSave(spawner);
                openFilters(p, spawner, 0);
            }));
        }

        DialogBase base = Ui.base("Filters · " + spawner.displayType(), body, List.of(), true);
        player.showDialog(Ui.multi(base, buttons,
                Ui.button("<color:" + Ui.FAINT + ">← Storage</color>", null, 90,
                        p -> openStorage(p, spawner, 0)), 2));
    }
}
