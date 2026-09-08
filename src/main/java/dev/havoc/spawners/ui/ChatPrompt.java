package dev.havoc.spawners.ui;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.util.Text;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Asks a player to type something in chat.
 * <p>
 * A chest inventory has no text field, so the one thing the dialogs can do that a chest cannot is
 * free text - naming a network. Rather than dropping that feature in modern mode, the menu closes
 * and the next thing the player types is captured here.
 * <p>
 * The pending prompt is cleared on answer, on cancel and on quit, so a player can never be left
 * with their chat silently swallowed.
 */
public final class ChatPrompt {

    private final HavocSpawners plugin;
    private final Map<UUID, BiConsumer<Player, String>> pending = new ConcurrentHashMap<>();

    public ChatPrompt(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    /** Closes the player's screen and captures their next chat line. */
    public void ask(Player player, String question, BiConsumer<Player, String> answer) {
        pending.put(player.getUniqueId(), answer);
        player.closeInventory();
        player.sendMessage(Text.mm(question));
        player.sendMessage(Text.mm("<color:" + Ui.FAINT + ">Type <color:" + Ui.ACCENT
                + ">cancel</color> to go back.</color>"));
    }

    public boolean waiting(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    public void forget(Player player) {
        pending.remove(player.getUniqueId());
    }

    /**
     * Feeds a chat line to a waiting prompt.
     *
     * @return true when the line was consumed and must not reach public chat
     */
    public boolean answer(Player player, String message) {
        BiConsumer<Player, String> handler = pending.remove(player.getUniqueId());
        if (handler == null) {
            return false;
        }
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("cancel")) {
            plugin.messages().send(player, "prompt.cancelled");
            return true;
        }
        // Back onto a server thread: chat arrives asynchronously, and everything a prompt does -
        // opening a menu, saving a spawner - has to happen on the main/region thread.
        plugin.sched().global(() -> {
            if (player.isOnline()) {
                handler.accept(player, trimmed);
            }
        });
        return true;
    }
}
