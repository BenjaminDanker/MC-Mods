package com.silver.aipets.fabric.command;

import com.silver.aipets.common.domain.PetSpecies;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Friendly vanilla chat UI for the adoption flow. */
public final class PetAdoptionMenu {
    private PetAdoptionMenu() { }

    public static void open(ServerCommandSource source) {
        open(source, false);
    }

    /** Renders the next adoption action from the current membership state. */
    public static void open(ServerCommandSource source, boolean activeSubscription) {
        source.sendFeedback(() -> Text.literal("PET ADOPTION")
                .formatted(Formatting.AQUA, Formatting.BOLD), false);
        if (activeSubscription) {
            source.sendFeedback(() -> Text.literal(
                    "Choose your new companion:")
                    .formatted(Formatting.GRAY), false);
            source.sendFeedback(() -> Text.empty()
                    .append(choice("[CAT]", "/pet adopt cat"))
                    .append("   ")
                    .append(choice("[DOG]", "/pet adopt dog")), false);
            source.sendFeedback(() -> Text.literal(
                    "You'll choose their name next.")
                    .formatted(Formatting.GRAY), false);
        } else {
            source.sendFeedback(() -> Text.literal(
                    "A pet membership is needed before you can adopt.")
                    .formatted(Formatting.YELLOW), false);
            source.sendFeedback(() -> Text.literal(
                    "Continue to the secure checkout, then return to Minecraft.")
                    .formatted(Formatting.GRAY), false);
            source.sendFeedback(() -> choice(
                    "[CONTINUE TO SUBSCRIPTION]", "/pet adopt subscribe"), false);
        }
    }

    public static void choose(ServerCommandSource source, PetSpecies species) {
        String kind = species == PetSpecies.CAT ? "cat" : "dog";
        source.sendFeedback(() -> Text.literal("PET ADOPTION — CHOOSE A NAME")
                .formatted(Formatting.AQUA, Formatting.BOLD), false);
        source.sendFeedback(() -> Text.literal("Name your " + kind
                + ". Click the button, type the name after the space, then press Enter.")
                .formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal("[ENTER NAME]")
                .formatted(Formatting.GREEN, Formatting.UNDERLINE)
                .styled(style -> style.withClickEvent(
                        new ClickEvent.SuggestCommand("/pet adopt " + kind + " "))), false);
        source.sendFeedback(() -> Text.empty()
                .append(choice("[BACK]", "/pet adopt")), false);
    }

    private static Text choice(String label, String command) {
        return Text.literal(label).formatted(Formatting.GREEN, Formatting.UNDERLINE)
                .styled(style -> style.withClickEvent(new ClickEvent.RunCommand(command)));
    }
}
