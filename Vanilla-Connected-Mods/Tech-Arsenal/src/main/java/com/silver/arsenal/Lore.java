package com.silver.arsenal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** The tooltip, written from the same numbers the weapon uses so it can never disagree with them. */
final class Lore {
    private static final String[] TIERS = {"", "Foundry", "Circuit", "Heavy"};

    private Lore() {
    }

    static List<Component> lines(Spec spec, boolean tempered) {
        List<Component> lines = new ArrayList<>();
        if (!spec.blurb.isBlank()) {
            lines.add(gray(spec.blurb));
        }
        if (spec.cell != null) {
            lines.add(stat("Charges", String.valueOf(new Params(spec.cell).integer("charges", 12))));
        }
        if (spec.melee != null) {
            Params melee = new Params(spec.melee);
            double damage = melee.num("damage", 0) * (tempered ? Tuning.temperedDamage : 1.0);
            lines.add(stat("Melee", String.format(Locale.ROOT, "%.1f damage, %.1f swings/s", damage, melee.num("speed", 1))));
        }
        if (spec.use != null) {
            Params use = new Params(spec.use);
            double mult = tempered ? Tuning.temperedDamage : 1.0;
            if (use.has("damage")) {
                int pellets = use.integer("pellets", 1);
                String damage = String.format(Locale.ROOT, "%.1f", use.num("damage", 0) * mult);
                lines.add(stat("Damage", pellets > 1 ? damage + " x " + pellets : damage));
            }
            if (use.has("blast")) {
                Params blast = use.child("blast");
                lines.add(stat("Blast", String.format(Locale.ROOT, "%.1f damage in %.1f blocks", blast.num("damage", 0) * mult, blast.num("radius", 0))));
            }
            if (use.has("cooldown")) {
                lines.add(stat("Reload", String.format(Locale.ROOT, "%.1fs", use.num("cooldown", 0) / 20.0)));
            }
            if (use.has("range")) {
                lines.add(stat("Range", String.format(Locale.ROOT, "%.0f blocks", use.num("range", 0))));
            }
        }
        if (spec.ammo != null) {
            Params ammo = new Params(spec.ammo);
            if (ammo.has("charge")) {
                lines.add(stat("Uses", ammo.integer("charge", 1) + " charge per use, from power cells"));
            } else {
                lines.add(stat("Uses", Component.translatable("item.arsenal." + ammo.str("item", "")).getString()));
            }
        }
        if (spec.tier > 0 && spec.tier < TIERS.length) {
            lines.add(Component.literal(TIERS[spec.tier] + " tier").withStyle(ChatFormatting.DARK_AQUA));
        }
        if (tempered) {
            lines.add(Component.literal("Deepsteel-tempered: +" + Math.round((Tuning.temperedDamage - 1) * 100) + "% damage").withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    private static Component gray(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    }

    private static Component stat(String label, String value) {
        return Component.literal(label + ": ").withStyle(ChatFormatting.DARK_GRAY).append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }
}
