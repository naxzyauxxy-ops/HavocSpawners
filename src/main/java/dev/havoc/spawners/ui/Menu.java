package dev.havoc.spawners.ui;

import dev.havoc.spawners.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A chest-inventory screen with a callback per slot.
 * <p>
 * This is the whole of the "modern" (chest GUI) presentation layer. It is deliberately the same
 * shape as the dialog layer: a screen is a list of labelled buttons with a {@code Consumer<Player>}
 * behind each one, so both presentations drive the identical action code and cannot drift apart.
 * <p>
 * Being an {@link InventoryHolder} is what makes it safe: the click listener only ever acts on an
 * inventory whose holder is a Menu, so no other plugin's inventory is ever touched, and every click
 * inside one is cancelled before the action runs - the player can never pull an icon out.
 */
public final class Menu implements InventoryHolder {

    /** Filler for the empty slots, so a screen reads as a designed panel rather than a chest. */
    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;

    private final Inventory inventory;
    private final Consumer<Player>[] actions;
    private final boolean fill;

    @SuppressWarnings("unchecked")
    public Menu(String title, int rows, boolean fill) {
        int size = Math.max(9, Math.min(54, rows * 9));
        this.inventory = Bukkit.createInventory(this, size, Text.mm(title));
        this.actions = new Consumer[size];
        this.fill = fill;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public int size() {
        return inventory.getSize();
    }

    /** Places an icon, optionally with something to run when it is clicked. */
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
        if (fill) {
            ItemStack filler = icon(FILLER, "<color:" + Ui.FAINT + "> </color>");
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                if (inventory.getItem(slot) == null) {
                    inventory.setItem(slot, filler);
                }
            }
        }
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ icons

    /**
     * Builds an icon from MiniMessage strings - the same strings the dialogs use, so a colour change
     * in {@code theme:} re-skins both presentations at once.
     */
    public static ItemStack icon(Material material, String name, String... loreLines) {
        // A block that has no item form (water, fire, a wall torch) would throw here, and a loot or
        // filter list can name one, so anything unplaceable falls back to a neutral icon.
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
        item.setItemMeta(meta);
        return item;
    }
}
