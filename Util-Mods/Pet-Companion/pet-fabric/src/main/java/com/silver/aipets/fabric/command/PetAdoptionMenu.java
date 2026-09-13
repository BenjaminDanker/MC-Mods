package com.silver.aipets.fabric.command;

import com.silver.aipets.common.domain.PetSpecies;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/** Friendly vanilla chat UI for the adoption flow. */
public final class PetAdoptionMenu {
    private PetAdoptionMenu() { }

    public static void open(CommandSourceStack source) {
        open(source, false);
    }

    /** Renders the next adoption action from the current membership state. */
    public static void open(CommandSourceStack source, boolean activeSubscription) {
        source.sendSuccess(() -> Component.literal("PET ADOPTION")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        if (activeSubscription) {
            source.sendSuccess(() -> Component.literal(
                    "Choose your new companion:")
                    .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.empty()
                    .append(choice("[CAT]", "/pet adopt cat"))
                    .append("   ")
                    .append(choice("[DOG]", "/pet adopt dog")), false);
            source.sendSuccess(() -> Component.literal(
                    "You'll choose their name next.")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "A pet membership is needed before you can adopt.")
                    .withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.literal(
                    "Continue to the secure checkout, then return to Minecraft.")
                    .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> choice(
                    "[CONTINUE TO SUBSCRIPTION]", "/pet adopt subscribe"), false);
        }
    }

    public static void choose(CommandSourceStack source, PetSpecies species) {
        String kind = species == PetSpecies.CAT ? "cat" : "dog";
        source.sendSuccess(() -> Component.literal("PET ADOPTION — CHOOSE A NAME")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("Name your " + kind
                + ". Click the button, type the name after the space, then press Enter.")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("[ENTER NAME]")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withClickEvent(
                        new ClickEvent.SuggestCommand("/pet adopt " + kind + " "))), false);
        source.sendSuccess(() -> Component.empty()
                .append(choice("[BACK]", "/pet adopt")), false);
    }

    private static Component choice(String label, String command) {
        return Component.literal(label).withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command)));
    }
}
