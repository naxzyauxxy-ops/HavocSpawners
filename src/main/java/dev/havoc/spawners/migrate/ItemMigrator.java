package dev.havoc.spawners.migrate;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.spawner.LegacyItems;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Rewrites old spawner items in place.
 * <p>
 * Identifying a foreign spawner item at placement time fixes the moment it is placed, but it does
 * nothing for the hundreds already sitting in ender chests, and every one of those is a support
 * ticket waiting to happen. This sweeps them: a SmartSpawner (or otherwise foreign) spawner item is
 * replaced, in its own slot, by the equivalent HavocSpawners item of the same type and count.
 * <p>
 * It is a conversion, not a deletion. Nothing is taken from a player: an item that cannot be
 * identified at all is left exactly where it is unless the server explicitly asks for it to be
 * cleared, because an unidentifiable spawner is still somebody's property.
 */
public final class ItemMigrator {

    /**
     * @param converted old items rewritten as ours
     * @param cleared   unidentifiable items removed (only when the server asked for that)
     * @param skipped   unidentifiable items left alone
     */
    public record Result(int converted, int cleared, int skipped) {

        public boolean touchedAnything() {
            return converted > 0 || cleared > 0;
        }

        public Result plus(Result other) {
            return new Result(converted + other.converted, cleared + other.cleared,
                    skipped + other.skipped);
        }
    }

    private static final Result NOTHING = new Result(0, 0, 0);

    private final HavocSpawners plugin;

    public ItemMigrator(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    /** Sweeps a player's inventory and ender chest. Must run on the thread that owns the player. */
    public Result convert(Player player) {
        Result result = sweep(player.getInventory(), 0);
        result = result.plus(sweep(player.getEnderChest(), 0));
        return result;
    }

    private Result sweep(Inventory inventory, int depth) {
        Result result = NOTHING;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (item.getType() == Material.SPAWNER) {
                Outcome outcome = rewrite(item);
                if (outcome.replacement != null) {
                    inventory.setItem(slot, outcome.replacement);
                    result = result.plus(new Result(item.getAmount(), 0, 0));
                } else if (outcome.clear) {
                    inventory.setItem(slot, null);
                    result = result.plus(new Result(0, item.getAmount(), 0));
                } else if (outcome.foreign) {
                    result = result.plus(new Result(0, 0, item.getAmount()));
                }
                continue;
            }
            // Vanilla cannot nest shulkers, but a plugin can, so the depth guard is there to make an
            // infinite descent impossible rather than merely unlikely.
            if (plugin.settings().legacyConvertShulkers && depth < 2 && isShulkerBox(item)) {
                result = result.plus(sweepShulker(inventory, slot, item, depth + 1));
            }
        }
        return result;
    }

    /**
     * Sweeps a shulker box being carried as an item.
     * <p>
     * Players hoard spawners in shulkers, so skipping these would leave most of the old items on a
     * busy server untouched. The box has to be written back explicitly - the block state behind an
     * item is a copy, not a live view.
     */
    private static boolean isShulkerBox(ItemStack item) {
        return item.getType().name().endsWith("SHULKER_BOX");
    }

    private Result sweepShulker(Inventory owner, int slot, ItemStack box, int depth) {
        try {
            ItemMeta meta = box.getItemMeta();
            if (!(meta instanceof BlockStateMeta blockStateMeta) || !blockStateMeta.hasBlockState()) {
                return NOTHING;
            }
            if (!(blockStateMeta.getBlockState() instanceof ShulkerBox shulker)) {
                return NOTHING;
            }
            Result result = sweep(shulker.getInventory(), depth);
            if (result.touchedAnything()) {
                blockStateMeta.setBlockState(shulker);
                box.setItemMeta(blockStateMeta);
                owner.setItem(slot, box);
            }
            return result;
        } catch (Throwable ex) {
            // A shulker we cannot open is not worth failing the whole sweep over.
            return NOTHING;
        }
    }

    private record Outcome(ItemStack replacement, boolean clear, boolean foreign) {
    }

    private static final Outcome LEAVE = new Outcome(null, false, false);

    private Outcome rewrite(ItemStack item) {
        if (plugin.items().isHavocSpawner(item)) {
            return LEAVE;
        }
        LegacyItems.Guess guess = LegacyItems.resolve(item);
        if (!guess.found()) {
            return new Outcome(null, plugin.settings().legacyRemoveUnidentified, true);
        }
        int stack = Math.max(1, Math.min(plugin.settings().maxStackSize, guess.stackSize()));
        ItemStack replacement = plugin.items().create(guess.entityType(), guess.itemMaterial(),
                stack, 1, item.getAmount());
        return new Outcome(replacement, false, true);
    }
}
