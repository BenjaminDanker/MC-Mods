package com.silver.elementchoice;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import me.moros.bending.api.GameProvider;
import me.moros.bending.api.platform.block.Block;
import me.moros.bending.api.platform.entity.LivingEntity;
import me.moros.bending.api.protection.AbstractProtection;
import me.moros.bending.api.registry.Registries;
import me.moros.bending.api.user.User;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bending does not work in server claims (spawn) or in claims that belong to someone else, only on unclaimed land and in the
 * player's own claims. Three parts: casting is refused (see ElementChoiceMod's permission hook), blocks inside a forbidden claim are
 * protected from bending that began elsewhere, and a player who walks into a forbidden claim loses their running abilities.
 */
final class ClaimWatch {
    private static final Logger LOGGER = LoggerFactory.getLogger("element-choice");
    private static final Set<UUID> INSIDE = new HashSet<>();
    private static volatile MinecraftServer server;

    private ClaimWatch() {
    }

    static void register() {
        boolean registered = false;
        try {
            registered = Registries.PROTECTIONS.register(new AbstractProtection("element-choice", "claims") {
                @Override
                public boolean canBuild(LivingEntity user, Block block) {
                    return !forbiddenBlock(user.uuid(), block);
                }
            });
        } catch (RuntimeException failure) {
            LOGGER.error("ALERT: could not register the claim protection with Bending; bending that starts outside a claim can still reach into one", failure);
        }
        LOGGER.info("Claim protection for bending registered: {}", registered);
    }

    /** Called once the server is up: from now on claim lookups work. */
    static void attach(MinecraftServer started) {
        server = started;
        LOGGER.info("Bending is refused in server claims and in claims that are not the caster's");
    }

    private static boolean forbiddenBlock(UUID player, Block block) {
        MinecraftServer current = server;
        if (current == null) {
            return false;
        }
        try {
            Identifier dimension = Identifier.parse(block.world().key().asString());
            return ClaimGuard.forbidden(current, player, dimension, new BlockPos(block.blockX(), block.blockY(), block.blockZ()));
        } catch (RuntimeException failure) {
            LOGGER.error("ALERT: claim lookup failed for a bending block check; allowing it", failure);
            return false;
        }
    }

    /** True when this player may not bend where they stand. Never throws: a failed lookup allows bending and says so. */
    static boolean forbidden(ServerPlayer player) {
        try {
            return ClaimGuard.forbidden(player);
        } catch (RuntimeException failure) {
            LOGGER.error("ALERT: claim lookup failed for {}; allowing bending", player.getName().getString(), failure);
            return false;
        }
    }

    /** Called a few times a second: tells players when they cross a boundary and ends what they were bending as they enter. */
    static void tick(MinecraftServer current) {
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            boolean now = forbidden(player);
            boolean was = INSIDE.contains(player.getUUID());
            if (now == was) {
                continue;
            }
            if (now) {
                INSIDE.add(player.getUUID());
                User user = Registries.BENDERS.get(player.getUUID());
                if (user != null) {
                    user.game().abilityManager(user.worldKey()).destroyUserInstances(user);
                }
                player.sendSystemMessage(Component.literal("Bending is off here: this land is claimed by someone else.").withStyle(ChatFormatting.GRAY), true);
            } else {
                INSIDE.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("Bending is available again.").withStyle(ChatFormatting.GRAY), true);
            }
        }
        INSIDE.removeIf(id -> current.getPlayerList().getPlayer(id) == null);
    }
}
