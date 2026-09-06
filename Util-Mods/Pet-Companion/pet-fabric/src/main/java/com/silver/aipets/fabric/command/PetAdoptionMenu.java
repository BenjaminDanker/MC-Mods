package com.silver.aipets.fabric.command;

import com.silver.aipets.common.domain.PetSpecies;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Vanilla private command feedback UI; choosing a species never creates or charges for a pet. */
public final class PetAdoptionMenu {
    private PetAdoptionMenu() { }

    public static void open(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Choose your companion")
                .formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.empty()
                .append(choice("[CAT]", "/pet adopt cat"))
                .append("   ")
                .append(choice("[DOG]", "/pet adopt dog")), false);
        source.sendFeedback(() -> Text.literal(
                "One pet per player. Appearance is chosen once and stays with your pet. "
                        + "An active subscription is required to adopt.")
                .formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.empty()
                .append(choice("[SUBSCRIBE]", "/pet link"))
                .append("   ")
                .append(choice("[MY PET]", "/pet status")), false);
    }

    public static void choose(ServerCommandSource source, PetSpecies species) {
        String kind = species == PetSpecies.CAT ? "cat" : "dog";
        source.sendFeedback(() -> Text.literal("Name your " + kind
                + ": click below, type a name after the space, then press Enter to adopt.")
                .formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.literal("[ENTER NAME]")
                .formatted(Formatting.GREEN, Formatting.UNDERLINE)
                .styled(style -> style.withClickEvent(
                        new ClickEvent.SuggestCommand("/pet adopt " + kind + " "))), false);
        source.sendFeedback(() -> Text.empty().append(choice("[BACK]", "/pet adopt")), false);
    }

    private static Text choice(String label, String command) {
        return Text.literal(label).formatted(Formatting.GREEN, Formatting.UNDERLINE)
                .styled(style -> style.withClickEvent(new ClickEvent.RunCommand(command)));
    }
}
