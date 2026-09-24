package dev.havoc.spawners.ui;

import dev.havoc.spawners.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A chest screen with a callback per slot, plus the small design system every screen is built from.
 * <p>
 * Being an {@link InventoryHolder} is what makes it safe: the click listener only ever acts on an
 * inventory whose holder is a Menu, and every click inside one is cancelled before the action runs,
 * so an icon can never be pulled out and a real item can never be pushed in.
 * <p>
 * The look is deliberately uniform - a coloured frame, a dark interior, a centred title tile and a
 * footer - so that fifteen different screens read as one plugin instead of fifteen chest inventories.
 */
public final class Menu implements InventoryHolder {

    /** Blank name for the panes that make up the frame, so they show no tooltip text. */
    private static final String BLANK = "<reset> ";

    private final Inventory inventory;
    private final Consumer<Player>[] actions;
    private final int rows;

    @SuppressWarnings("unchecked")
    private Menu(String title, int rows) {
        this.rows = Math.max(1, Math.min(6, rows));
        int size = this.rows * 9;
        this.inventory = Bukkit.createInventory(this, size, Text.mm(title));
        this.actions = new Consumer[size];
    }

    /**
     * A framed screen: accent panes around the edge, dark panes inside.
     * <p>
     * This is the default for every screen that is a set of buttons rather than a grid of items.
     */
    public static Menu panel(String title, int rows) {
        Menu menu = new Menu(title, rows);
        menu.frame();
        return menu;
    }

    /** A bare screen, for the ones that need every slot for real items (storage, filters, lists). */
    public static Menu grid(String title, int rows) {
        return new Menu(title, rows);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public int rows() {
        return rows;
    }

    public int size() {
        return inventory.getSize();
    }

    /** Edge in the accent colour, interior in near-black. */
    private void frame() {
        ItemStack edge = pane(Material.RED_STAINED_GLASS_PANE);
        ItemStack inner = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < size(); slot++) {
            int row = slot / 9;
            int column = slot % 9;
            boolean border = row == 0 || row == rows - 1 || column == 0 || column == 8;
            inventory.setItem(slot, border ? edge : inner);
        }
    }

    /** Fills every slot that is still empty. Used by the grid screens for their control row. */
    public Menu fillEmpty(int from, int to, Material material) {
        ItemStack filler = pane(material);
        for (int slot = Math.max(0, from); slot <= Math.min(size() - 1, to); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
        return this;
    }

    public static ItemStack pane(Material material) {
        return icon(material, BLANK);
    }

    public Menu set(int slot, ItemStack icon, Consumer<Player> action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return this;
        }
        inventory.setItem(slot, icon);
        actions[slot] = action;
        return this;
    }

    public Menu set(int slot, ItemStack icon) {
        return set(slot, icon, null);
    }

    /** The action behind a slot, or null when that slot is decoration. */
    public Consumer<Player> actionAt(int slot) {
        return slot < 0 || slot >= actions.length ? null : actions[slot];
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ icons

    /**
     * Builds an icon from MiniMessage strings - the same strings the chat messages use, so one
     * change in {@code theme:} re-skins the whole GUI.
     */
    public static ItemStack icon(Material material, String name, String... loreLines) {
        // A block with no item form (water, fire, a wall torch) would throw, and a loot or filter
        // list can name one, so anything unplaceable falls back to a neutral icon.
        Material safe = material == null || material.isAir() || !material.isItem()
                ? Material.STONE : material;
        return decorate(new ItemStack(safe), name, loreLines);
    }

    public static ItemStack decorate(ItemStack item, String name, String... loreLines) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (name != null) {
            // Italics off: vanilla italicises every custom name, which looks wrong on a panel.
            meta.displayName(Text.mm(name).decoration(TextDecoration.ITALIC, false));
        }
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            if (line == null) {
                continue;
            }
            for (String part : line.split("\n")) {
                lore.add(Text.mm(part).decoration(TextDecoration.ITALIC, false));
            }
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        // Armour values, durability bars and potion effects are noise on a menu button.
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE);
        item.setItemMeta(meta);
        return item;
    }

    /** The enchant shimmer, without an enchantment showing in the tooltip. Marks an active toggle. */
    public static ItemStack glow(ItemStack item) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        } catch (Throwable ignored) {
            // Cosmetic only - never worth failing a screen over.
        }
        return item;
    }

    /** Stack count as a number badge. Clamped, because 65+ renders as an empty slot on some clients. */
    public static ItemStack badge(ItemStack item, long count) {
        item.setAmount((int) Math.max(1, Math.min(64, count)));
        return item;
    }
}
