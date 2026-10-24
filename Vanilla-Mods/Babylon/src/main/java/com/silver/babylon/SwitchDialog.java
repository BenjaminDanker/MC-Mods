package com.silver.babylon;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;

/** The confirmation shown before a player swaps one path for the other. A built-in dialog, so vanilla clients see it. */
final class SwitchDialog {
    private SwitchDialog() {
    }

    /** What switching destroys; also used in chat so the wording never drifts. */
    static String warning(String fromLabel) {
        return "Switching erases everything you have on " + fromLabel + ": your advancements, your skill tree, your claims there,"
            + " and the items you keep on that server, ender chest included. Soulbound items and what you hold on other servers are not affected."
            + " This cannot be undone.";
    }

    static Dialog confirm(String fromLabel, String toLabel, String toId) {
        List<DialogBody> body = List.of(
            new PlainMessage(Component.literal("You walk the path of " + fromLabel + ".").withStyle(ChatFormatting.GRAY), 300),
            new PlainMessage(Component.literal(warning(fromLabel)).withStyle(ChatFormatting.RED), 300),
            new PlainMessage(Component.literal("You will start fresh on " + toLabel + ".").withStyle(ChatFormatting.GRAY), 300));
        ActionButton go = new ActionButton(new CommonButtonData(
            Component.literal("Switch to " + toLabel + " and erase " + fromLabel).withStyle(ChatFormatting.RED),
            Optional.of(Component.literal("This cannot be undone.")), 200),
            Optional.of(new StaticAction(new ClickEvent.RunCommand("choose " + toId + " confirm"))));
        ActionButton stay = new ActionButton(new CommonButtonData(
            Component.literal("Stay on " + fromLabel).withStyle(ChatFormatting.GREEN), Optional.empty(), 200), Optional.empty());
        return new MultiActionDialog(
            new CommonDialogData(Component.literal("Switch Paths?").withStyle(ChatFormatting.GOLD), Optional.empty(),
                true, false, DialogAction.CLOSE, body, List.of()),
            List.of(go), Optional.of(stay), 1);
    }
}
