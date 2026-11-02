package com.silver.elementchoice;

import java.util.List;
import me.moros.bending.api.event.AbilityActivationEvent;
import me.moros.bending.api.GameProvider;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * The Magic story's chapters are answered, not just visited: a chapter completes when the player calls their element
 * (casts any bound ability) inside one of its places, with the chapter before it done. Finding the place first tells
 * them it is listening (checked every two seconds, once per chapter). Server thread, as Bending's events are.
 */
final class StoryCalls {
    private record Chapter(String advancement, String parent, List<String> structures) {
    }

    private static final List<Chapter> CHAPTERS = List.of(
        new Chapter("magicstory:tower", "magicstory:awakened", List.of("terralith:mage_tower", "terralith:mage_tower_autumn",
            "terralith:mage_tower_spring", "terralith:mage_tower_summer", "terralith:mage_tower_winter")),
        new Chapter("magicstory:temple", "magicstory:tower", List.of("structory_towers:ancient_temple", "structory_towers:sacred_relic_temple")),
        new Chapter("magicstory:wizard", "magicstory:temple", List.of("structory_towers:wizard_tower")),
        new Chapter("magicstory:fire", "magicstory:wizard", List.of("incendium:forbidden_castle", "incendium:infernal_altar",
            "incendium:sanctum", "incendium:nether_reactor")),
        new Chapter("magicstory:void", "magicstory:fire", List.of("nullscape:rift", "nullscape:dragon_skeleton", "structory_towers:end/end_tower")));

    private final MinecraftServer server;

    private StoryCalls(MinecraftServer server) {
        this.server = server;
    }

    static void register(MinecraftServer server) {
        if (server.getAdvancements().get(Identifier.parse("magicstory:tower")) == null) {
            return; // the Magic story datapack is not on this server
        }
        StoryCalls calls = new StoryCalls(server);
        GameProvider.get().eventBus().subscribe(AbilityActivationEvent.class, calls::onActivation);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(ticking -> {
            if (ticking.getTickCount() % 40 == 0) {
                ticking.getPlayerList().getPlayers().forEach(calls::hint);
            }
        });
    }

    /** The next open chapter for this player, or null when the story is done (or not begun). */
    private Chapter next(ServerPlayer player) {
        for (Chapter chapter : CHAPTERS) {
            AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(chapter.advancement()));
            AdvancementHolder parent = server.getAdvancements().get(Identifier.parse(chapter.parent()));
            if (holder == null || parent == null || done(player, holder)) {
                continue;
            }
            return done(player, parent) ? chapter : null;
        }
        return null;
    }

    /** Standing in the next chapter's place for the first time: the place is listening. */
    private void hint(ServerPlayer player) {
        Chapter chapter = next(player);
        if (chapter == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        String tag = "heard_" + chapter.advancement().substring(chapter.advancement().indexOf(':') + 1);
        if (player.entityTags().contains(tag) || !inside(level, player, chapter.structures())) {
            return;
        }
        player.addTag(tag);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Something old is listening here. Call your element: cast any ability you have bound.")
            .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Something old is listening here.")
            .withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.ITALIC), true);
        level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
            net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    private void onActivation(AbilityActivationEvent event) {
        if (!event.ability().canBind()) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(event.user().uuid());
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Chapter chapter = next(player);
        if (chapter != null && inside(level, player, chapter.structures())) {
            AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(chapter.advancement()));
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
            List<String> remaining = new java.util.ArrayList<>();
            progress.getRemainingCriteria().forEach(remaining::add);
            remaining.forEach(criterion -> player.getAdvancements().award(holder, criterion));
        }
    }

    private static boolean done(ServerPlayer player, AdvancementHolder holder) {
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static boolean inside(ServerLevel level, ServerPlayer player, List<String> structures) {
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (String id : structures) {
            Structure structure = registry.getValue(Identifier.parse(id));
            if (structure == null) {
                continue;
            }
            StructureStart start = level.structureManager().getStructureWithPieceAt(player.blockPosition(), structure);
            if (start != null && start.isValid()) {
                return true;
            }
        }
        return false;
    }
}
