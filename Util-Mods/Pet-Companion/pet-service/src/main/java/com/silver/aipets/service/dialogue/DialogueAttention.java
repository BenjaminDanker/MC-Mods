package com.silver.aipets.service.dialogue;

import java.util.Locale;
import java.util.regex.Pattern;

/** Keeps background geography and ordinary movement out of the conversational foreground. */
public final class DialogueAttention {
    private static final Pattern PLACE = Pattern.compile(
            "\\b(where|world|server|dimension|biome|nether|end|ocean|cave|desert|sky|magic|trip|travel|visited|place|area)\\b");
    private static final Pattern ACTIVITY = Pattern.compile(
            "\\b(doing|happening|activity|moving|looking|watching|sitting|exploring|investigating)\\b");

    private DialogueAttention() { }

    public static boolean placeRelevant(String ownerMessage) {
        return PLACE.matcher(ownerMessage.toLowerCase(Locale.ROOT)).find();
    }

    public static String render(String ownerMessage, DialogueGameContext game) {
        var scene = game.scene();
        String lower = ownerMessage.toLowerCase(Locale.ROOT);
        StringBuilder facts = new StringBuilder("owner_is_here=true\n");
        if (placeRelevant(ownerMessage)) {
            facts.append("current_server=").append(WorldLocationContext.realm(game.backend()))
                    .append(", dimension=").append(WorldLocationContext.dimension(game.dimension()))
                    .append("\n");
            if (!scene.petBiome().equals("unknown"))
                facts.append("current_biome=").append(scene.petBiome()).append("\n");
        }
        if (scene.proximity().equals("UNKNOWN")) facts.append("owner_visibility=UNKNOWN\n");
        else facts.append("owner_visible=").append(scene.ownerVisible()).append("\n");
        if (scene.nearbyThreat()) facts.append("visible_threat=true\n");
        if (scene.ownerActivity().equals("RECENTLY_HURT")
                || scene.ownerHealth().equals("CRITICAL")
                || scene.ownerHealth().equals("HURT"))
            facts.append("owner_health=").append(scene.ownerHealth()).append("\n");
        if (scene.ownerActivity().equals("USING_ITEM") || ACTIVITY.matcher(lower).find())
            facts.append("owner_now=").append(scene.ownerActivity())
                    .append(", pet_now=").append(scene.petActivity()).append("\n");
        if (scene.weather().equals("THUNDER") || lower.matches(".*\\b(weather|rain|storm)\\b.*"))
            facts.append("weather=").append(scene.weather()).append("\n");
        return facts.toString();
    }
}
