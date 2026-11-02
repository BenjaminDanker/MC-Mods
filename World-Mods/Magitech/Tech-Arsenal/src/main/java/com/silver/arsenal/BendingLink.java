package com.silver.arsenal;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The only place the arsenal touches Bending, so servers without it (the Tech server) never load a Bending class. */
final class BendingLink {
    private static final Logger LOGGER = LoggerFactory.getLogger("tech-arsenal");

    private BendingLink() {
    }

    /** Ends a bender's running abilities and puts every ability on cooldown. Returns whether the player was a bender. */
    static boolean disrupt(ServerPlayer player, long cooldownMillis) {
        if (!FabricLoader.getInstance().isModLoaded("bending")) {
            return false;
        }
        try {
            return Disrupt.apply(player, cooldownMillis);
        } catch (LinkageError | RuntimeException failure) {
            LOGGER.error("ALERT: the EMP could not reach Bending for {}", player.getName().getString(), failure);
            return false;
        }
    }

    /** Isolated so a missing or changed Bending API fails here and is reported, not at class-load of everything else. */
    private static final class Disrupt {
        static boolean apply(ServerPlayer player, long cooldownMillis) {
            var user = me.moros.bending.api.registry.Registries.BENDERS.get(player.getUUID());
            if (user == null || user.elements().isEmpty()) {
                return false;
            }
            user.game().abilityManager(user.worldKey()).destroyUserInstances(user);
            for (var ability : me.moros.bending.api.registry.Registries.ABILITIES) {
                if (!ability.hidden()) {
                    user.addCooldown(ability, cooldownMillis);
                }
            }
            return true;
        }
    }
}
