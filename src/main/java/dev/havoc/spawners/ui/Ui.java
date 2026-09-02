package dev.havoc.spawners.ui;

import dev.havoc.spawners.util.Text;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.body.ItemDialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Small facade over Paper's Dialog API.
 * <p>
 * Everything the plugin shows the player is a server-side dialog: no chest inventories, no click
 * slot maths, no inventory desync. Buttons carry real callbacks, so a dialog can be rebuilt with
 * fresh numbers simply by showing a new one.
 */
public final class Ui {

    /** Shared palette so every screen reads as one plugin. */
    public static final String ACCENT = "#b14dff";
    public static final String ACCENT_DIM = "#7d35b8";
    public static final String GOOD = "#5bd66f";
    public static final String WARN = "#ffb454";
    public static final String BAD = "#ff5d6c";
    public static final String INK = "#c9c4d6";
    public static final String FAINT = "#7d7a89";

    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofMinutes(30))
            .build();

    private Ui() {
    }

    public static Component title(String text) {
        return Text.mm("<gradient:" + ACCENT + ":" + ACCENT_DIM + "><bold>" + text + "</bold></gradient>");
    }

    public static Component line(String miniMessage) {
        return Text.mm(miniMessage);
    }

    /** "Label: value" body line with the plugin's colours already applied. */
    public static DialogBody stat(String label, String value) {
        return DialogBody.plainMessage(Text.mm(
                "<color:" + FAINT + ">" + label + "</color> <color:" + INK + ">" + value + "</color>"), 320);
    }

    public static DialogBody text(String miniMessage) {
        return DialogBody.plainMessage(Text.mm(miniMessage), 320);
    }

    public static DialogBody narrow(String miniMessage) {
        return DialogBody.plainMessage(Text.mm(miniMessage), 200);
    }

    public static ItemDialogBody item(ItemStack stack, String description) {
        return DialogBody.item(stack)
                .description(DialogBody.plainMessage(Text.mm(description), 240))
                .showDecorations(true)
                .showTooltip(true)
                .width(24)
                .height(24)
                .build();
    }

    public static ItemDialogBody icon(ItemStack stack) {
        return DialogBody.item(stack)
                .showDecorations(true)
                .showTooltip(true)
                .width(32)
                .height(32)
                .build();
    }

    /** A button that runs {@code onClick} on the clicking player. */
    public static ActionButton button(String label, String tooltip, int width, Consumer<Player> onClick) {
        DialogAction action = DialogAction.customClick((response, audience) -> {
            Player player = playerOf(audience);
            if (player != null) {
                onClick.accept(player);
            }
        }, CALLBACK_OPTIONS);
        return ActionButton.builder(Text.mm(label))
                .tooltip(tooltip == null ? null : Text.mm(tooltip))
                .width(width)
                .action(action)
                .build();
    }

    /** A button whose callback also receives the dialog's input values. */
    public static ActionButton input(String label, String tooltip, int width,
                                     java.util.function.BiConsumer<Player, io.papermc.paper.dialog.DialogResponseView> onClick) {
        DialogAction action = DialogAction.customClick((response, audience) -> {
            Player player = playerOf(audience);
            if (player != null) {
                onClick.accept(player, response);
            }
        }, CALLBACK_OPTIONS);
        return ActionButton.builder(Text.mm(label))
                .tooltip(tooltip == null ? null : Text.mm(tooltip))
                .width(width)
                .action(action)
                .build();
    }

    /** A plain label with no action - used to keep grid layouts even. */
    public static ActionButton plain(String label, int width) {
        return ActionButton.builder(Text.mm(label)).width(width).build();
    }

    public static Player playerOf(Audience audience) {
        return audience instanceof Player player ? player : null;
    }

    public static DialogBase base(String titleText, List<? extends DialogBody> body,
                                  List<? extends io.papermc.paper.registry.data.dialog.input.DialogInput> inputs,
                                  boolean stayOpen) {
        return DialogBase.builder(title(titleText))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(stayOpen
                        ? DialogBase.DialogAfterAction.NONE
                        : DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .inputs(inputs)
                .build();
    }

    public static Dialog multi(DialogBase base, List<ActionButton> buttons, ActionButton exit, int columns) {
        // A multi-action dialog needs at least one button; screens can legitimately end up with none
        // (a fully upgraded spawner, a filter list with nothing in it).
        List<ActionButton> safe = buttons.isEmpty()
                ? List.of(plain("<color:" + FAINT + ">—</color>", 90))
                : buttons;
        DialogType type = DialogType.multiAction(safe)
                .exitAction(exit)
                .columns(Math.max(1, columns))
                .build();
        return Dialog.create(factory -> factory.empty().base(base).type(type));
    }

    public static Dialog notice(DialogBase base, ActionButton button) {
        return Dialog.create(factory -> factory.empty().base(base).type(DialogType.notice(button)));
    }

    public static Dialog confirm(DialogBase base, ActionButton yes, ActionButton no) {
        return Dialog.create(factory -> factory.empty().base(base).type(DialogType.confirmation(yes, no)));
    }

    /** Progress bar rendered with block characters, e.g. ▰▰▰▱▱▱▱▱▱▱ */
    public static String bar(double ratio, int cells, String filledColor) {
        int filled = (int) Math.round(Math.max(0.0D, Math.min(1.0D, ratio)) * cells);
        StringBuilder builder = new StringBuilder();
        builder.append("<color:").append(filledColor).append('>');
        builder.append("▰".repeat(filled));
        builder.append("</color><color:").append(FAINT).append('>');
        builder.append("▱".repeat(Math.max(0, cells - filled)));
        builder.append("</color>");
        return builder.toString();
    }
}
