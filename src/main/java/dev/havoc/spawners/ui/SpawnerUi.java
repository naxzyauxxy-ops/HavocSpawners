package dev.havoc.spawners.ui;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.config.Messages;
import dev.havoc.spawners.econ.SellResult;
import dev.havoc.spawners.feature.UpgradeTier;
import dev.havoc.spawners.spawner.ItemSig;
import dev.havoc.spawners.spawner.SpawnMode;
import dev.havoc.spawners.spawner.SpawnerData;
import dev.havoc.spawners.spawner.SpawnerItems;
import dev.havoc.spawners.spawner.SpawnerManager;
import dev.havoc.spawners.spawner.VirtualStorage;
import dev.havoc.spawners.util.Numbers;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What every spawner screen actually <em>does</em>, and the one place that decides who sees what.
 * <p>
 * The plugin has a single Java presentation - the chest GUI in {@link ChestUi} - and a Bedrock one,
 * because Geyser cannot render Java menus of any kind. Both drive the methods in this class, so
 * withdrawing, selling, stacking and upgrading behave identically whichever a player is looking at,
 * and a fix lands in both at once.
 */
public final class SpawnerUi {

    private final HavocSpawners plugin;

    public SpawnerUi(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ routing

    private boolean bedrock(Player player) {
        return plugin.bedrock().useForms(player);
    }

    public void openMain(Player player, SpawnerData spawner) {
        if (bedrock(player)) {
            plugin.bedrockUi().openMain(player, spawner);
            return;
        }
        plugin.chestUi().openMain(player, spawner);
    }

    public void openStorage(Player player, SpawnerData spawner, int page) {
        if (bedrock(player)) {
            plugin.bedrockUi().openStorage(player, spawner, page);
            return;
        }
        plugin.chestUi().openStorage(player, spawner, page);
    }

    public void openItemActions(Player player, SpawnerData spawner, ItemSig sig) {
        if (bedrock(player)) {
            plugin.bedrockUi().openItemActions(player, spawner, sig);
            return;
        }
        plugin.chestUi().openItemActions(player, spawner, sig);
    }

    public void openBulkDrop(Player player, SpawnerData spawner) {
        if (bedrock(player)) {
            plugin.bedrockUi().openBulkDrop(player, spawner);
            return;
        }
        plugin.chestUi().openBulkDrop(player, spawner);
    }

    public void openSell(Player player, SpawnerData spawner) {
        if (bedrock(player)) {
            plugin.bedrockUi().openSell(player, spawner);
            return;
        }
        plugin.chestUi().openSell(player, spawner);
    }

    public void openStack(Player player, SpawnerData spawner) {
        if (bedrock(player)) {
            plugin.bedrockUi().openStack(player, spawner);
            return;
        }
        plugin.chestUi().openStack(player, spawner);
    }

    public void openUpgrade(Player player, SpawnerData spawner) {
        if (bedrock(player)) {
            plugin.bedrockUi().openUpgrade(player, spawner);
            return;
        }
        plugin.chestUi().openUpgrade(player, spawner);
    }

    public void openAutomation(Player player, SpawnerData spawner) {
        if (bedrock(player)) {
            plugin.bedrockUi().openAutomation(player, spawner);
            return;
        }
        plugin.chestUi().openAutomation(player, spawner);
    }

    public void openNetwork(Player player, SpawnerData spawner) {
        if (bedrock(player)) {
            plugin.bedrockUi().openNetwork(player, spawner);
            return;
        }
        plugin.chestUi().openNetwork(player, spawner);
    }

    public void openNetworkOverview(Player player, String network, SpawnerData origin) {
        if (bedrock(player)) {
            plugin.bedrockUi().openNetworkOverview(player, network, origin);
            return;
        }
        plugin.chestUi().openNetworkOverview(player, network, origin);
    }

    public void openAnalytics(Player player, SpawnerData spawner) {
        if (bedrock(player)) {
            plugin.bedrockUi().openAnalytics(player, spawner);
            return;
        }
        plugin.chestUi().openAnalytics(player, spawner);
    }

    public void openFilters(Player player, SpawnerData spawner, int page) {
        if (bedrock(player)) {
            plugin.bedrockUi().openFilters(player, spawner, page);
            return;
        }
        plugin.chestUi().openFilters(player, spawner, page);
    }

    // ------------------------------------------------------------------ actions

    /**
     * Pauses or resumes the spawner.
     * <p>
     * A stopped spawner keeps everything it has already produced and simply stops simulating, so this
     * is the switch for "stop filling up while I deal with it", not a destructive action.
     */
    void toggleRunning(Player player, SpawnerData spawner) {
        boolean stopping = !spawner.stopped();
        spawner.stopped(stopping);
        if (!stopping) {
            // Resuming: restart the clock, so a spawner paused for a week does not immediately pay
            // out a week of catch-up cycles.
            spawner.lastSpawnMillis(System.currentTimeMillis());
        }
        plugin.storage().queueSave(spawner);
        plugin.messages().send(player, stopping ? "spawner.stopped" : "spawner.resumed");
    }

    /**
     * Flips this spawner between simulated loot and real mobs.
     * <p>
     * Switching to REAL leaves everything already banked exactly where it is - the storage stays
     * browsable and withdrawable, it just stops growing, so nobody loses a stockpile by trying the
     * other mode out.
     */
    void toggleSpawnMode(Player player, SpawnerData spawner) {
        if (!plugin.settings().allowPlayerSpawnMode) {
            plugin.messages().send(player, "spawn-mode.locked", Messages.of(
                    "mode", plugin.settings().spawnMode.display()));
            return;
        }
        SpawnMode current = SpawnerManager.effectiveMode(spawner, plugin.settings());
        SpawnMode next = current == SpawnMode.REAL ? SpawnMode.SIMULATED : SpawnMode.REAL;
        spawner.spawnMode(next);
        spawner.lastSpawnMillis(System.currentTimeMillis());
        plugin.storage().queueSave(spawner);
        plugin.messages().send(player, "spawn-mode.changed", Messages.of("mode", next.display()));
    }

    ItemStack iconStack(SpawnerData spawner) {
        Material icon = plugin.lootEngine().iconFor(spawner);
        return new ItemStack(icon == null || icon.isAir() ? Material.SPAWNER : icon);
    }

    /**
     * Throws every unit of one item out, then runs {@code onFinish}.
     * <p>
     * Shared by both presentations so the busy check and the "nothing to drop" message cannot end
     * up worded differently on Bedrock than on Java.
     */
    void dropItemOnGround(Player player, SpawnerData spawner, ItemSig sig, Runnable onFinish) {
        if (plugin.dropService().isRunning(spawner)) {
            plugin.messages().send(player, "bulk-drop.busy");
            return;
        }
        if (!plugin.dropService().dropItemToGround(player, spawner, sig, onFinish)) {
            plugin.messages().send(player, "bulk-drop.nothing");
        }
    }

    /** Adds or removes a material from the discard list. */
    void toggleFilter(SpawnerData spawner, Material material) {
        if (!spawner.filtered().remove(material)) {
            spawner.filtered().add(material);
        }
        spawner.markDirty();
        plugin.storage().queueSave(spawner);
    }

    void clearFilters(SpawnerData spawner) {
        spawner.filtered().clear();
        spawner.markDirty();
        plugin.storage().queueSave(spawner);
    }

    void sortToTop(SpawnerData spawner, Material material) {
        spawner.preferredSort(material);
        spawner.storage().sortPreferring(material);
        plugin.storage().queueSave(spawner);
    }

    /** Everything worth offering as a filter: what is stored, what is already filtered, what drops. */
    List<Material> filterCandidates(SpawnerData spawner) {
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
        return candidates;
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

    /**
     * Throws one page out - the old plugin's drop button.
     * <p>
     * The screen is deliberately left open and refreshed once the throw finishes, so a player can
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
        player.closeInventory();
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

    /** Sells the whole spawner and reports the result. */
    void sellAll(Player player, SpawnerData spawner) {
        SellResult result = plugin.sell().sellAll(spawner, player.getUniqueId());
        if (result.isEmpty()) {
            plugin.messages().send(player, "sell.nothing");
            return;
        }
        plugin.messages().send(player, "sell.success", Messages.of(
                "items", Numbers.plain(result.itemsSold()),
                "money", plugin.economy().format(result.net())));
    }

    /** Re-sorts storage biggest-first, honouring whatever material was pinned to the top. */
    void sortStorage(SpawnerData spawner) {
        spawner.storage().sortPreferring(spawner.preferredSort());
        plugin.storage().queueSave(spawner);
    }

    /** Claims XP without the "nothing stored" complaint or the redraw - for combined buttons. */
    void claimExpQuietly(Player player, SpawnerData spawner) {
        long exp = spawner.storedExp();
        if (exp <= 0L) {
            return;
        }
        spawner.storedExp(0L);
        plugin.storage().queueSave(spawner);
        player.giveExp((int) Math.min(Integer.MAX_VALUE, exp), plugin.settings().allowExpMending);
        plugin.messages().send(player, "exp.claimed", Messages.of("exp", Numbers.plain(exp)));
    }

    /**
     * Moves one storage page straight into the player's inventory.
     * <p>
     * Bounded by what the inventory can hold rather than by the page: whatever does not fit goes
     * back into storage, so nothing is ever destroyed by a full inventory.
     */
    void takePage(Player player, SpawnerData spawner, int page) {
        long from = (long) Math.max(0, page) * VirtualStorage.SLOTS_PER_PAGE;
        List<ItemStack> stacks = spawner.storage().slice(from,
                from + VirtualStorage.SLOTS_PER_PAGE - 1, true);
        if (stacks.isEmpty()) {
            plugin.messages().send(player, "storage.empty");
            return;
        }
        long delivered = 0L;
        long returned = 0L;
        Map<Integer, ItemStack> leftovers =
                player.getInventory().addItem(stacks.toArray(new ItemStack[0]));
        for (ItemStack stack : stacks) {
            delivered += stack.getAmount();
        }
        for (ItemStack leftover : leftovers.values()) {
            if (leftover == null) {
                continue;
            }
            returned += leftover.getAmount();
            spawner.storage().addUnchecked(ItemSig.of(leftover), leftover.getAmount());
        }
        plugin.storage().queueSave(spawner);
        if (returned > 0L) {
            plugin.messages().send(player, "inventory-full");
        }
        plugin.messages().send(player, "storage.withdrew", Messages.of(
                "amount", Numbers.plain(delivered - returned),
                "item", "items"));
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

    /** Charges for the next tier and applies it. Reports its own failures. */
    void buyUpgrade(Player player, SpawnerData spawner, UpgradeTier next) {
        if (next == null) {
            return;
        }
        if (!plugin.economy().available()) {
            plugin.messages().send(player, "economy.unavailable");
            return;
        }
        if (!plugin.economy().withdraw(player.getUniqueId(), next.cost())) {
            plugin.messages().send(player, "upgrade.too-poor", Messages.of(
                    "cost", plugin.economy().format(next.cost())));
            return;
        }
        spawner.level(next.level());
        spawner.recompute(plugin.settings(), plugin.upgrades());
        plugin.storage().queueSave(spawner);
        plugin.messages().send(player, "upgrade.success", Messages.of(
                "tier", next.name(), "level", String.valueOf(next.level())));
    }

    void sellNetwork(Player player, List<SpawnerData> members) {
        long sold = 0L;
        double earned = 0.0D;
        for (SpawnerData member : members) {
            SellResult result = plugin.sell().sellAll(member, player.getUniqueId());
            sold += result.itemsSold();
            earned += result.net();
        }
        plugin.messages().send(player, "sell.success", Messages.of(
                "items", Numbers.plain(sold), "money", plugin.economy().format(earned)));
    }

    void claimNetworkExp(Player player, List<SpawnerData> members) {
        long claimed = 0L;
        for (SpawnerData member : members) {
            claimed += member.storedExp();
            member.storedExp(0L);
            plugin.storage().queueSave(member);
        }
        if (claimed > 0L) {
            player.giveExp((int) Math.min(Integer.MAX_VALUE, claimed), plugin.settings().allowExpMending);
        }
        plugin.messages().send(player, "exp.claimed", Messages.of("exp", Numbers.plain(claimed)));
    }

    void drainNetwork(Player player, List<SpawnerData> members) {
        int started = 0;
        for (SpawnerData member : members) {
            if (plugin.dropService().dropAll(player, member)) {
                started++;
            }
        }
        plugin.messages().send(player, "network.drain-started", Messages.of(
                "count", Numbers.plain(started)));
    }

    void toggleNetworkAutoSell(Player player, List<SpawnerData> members) {
        boolean enable = members.stream().anyMatch(m -> !m.autoSell());
        for (SpawnerData member : members) {
            member.autoSell(enable);
            plugin.storage().queueSave(member);
        }
        plugin.messages().send(player, enable ? "network.autosell-on" : "network.autosell-off");
    }
}
