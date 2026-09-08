package dev.havoc.spawners.listener;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.ui.Menu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.function.Consumer;

/**
 * Click handling for the chest-GUI ("modern") screens.
 * <p>
 * Every click on a Menu is cancelled before anything else happens, including shift-clicks and
 * number-key swaps from the player's own inventory, so an icon can never be taken out and a real
 * item can never be shoved in. Only then is the slot's action run.
 */
public final class MenuListener implements Listener {

    private final HavocSpawners plugin;

    public MenuListener(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) {
            return;
        }
        // Cancel first, unconditionally: a shift-click in the *player's* inventory would otherwise
        // move a real item into the panel.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != top) {
            return;
        }
        Consumer<Player> action = menu.actionAt(event.getSlot());
        if (action == null) {
            return;
        }
        try {
            action.accept(player);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Menu action failed: " + ex);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
