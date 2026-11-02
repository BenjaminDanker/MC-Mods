package com.silver.magitech;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Completes an advancement for a player directly; nothing happens if it is unknown here or already done. */
final class Grants {
    private Grants() {
    }

    static void grant(ServerPlayer player, String id) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(Identifier.parse(id));
        if (holder == null) {
            return;
        }
        var progress = player.getAdvancements().getOrStartProgress(holder);
        List<String> remaining = new ArrayList<>();
        progress.getRemainingCriteria().forEach(remaining::add);
        remaining.forEach(criterion -> player.getAdvancements().award(holder, criterion));
    }
}
