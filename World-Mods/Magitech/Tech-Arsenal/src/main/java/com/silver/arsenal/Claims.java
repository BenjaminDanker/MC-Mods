package com.silver.arsenal;

import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Weapons and deployables do not work in server claims (spawn) or in claims that belong to someone else; unclaimed land and the
 * player's own claims are fine. Movement and healing tools (grapple, stim, barrier, rev) are not weapons and always work.
 */
final class Claims {
    private static final Logger LOGGER = LoggerFactory.getLogger("tech-arsenal");
    private static final boolean AVAILABLE = FabricLoader.getInstance().isModLoaded("openpartiesandclaims");
    private static final boolean DEBUG = Boolean.getBoolean("arsenal.debug");
    private static final Set<String> ALWAYS_ALLOWED = Set.of("grapple", "stim", "barrier", "rev");

    private Claims() {
    }

    static boolean forbids(UUID owner, UUID player) {
        return owner != null && !owner.equals(player);
    }

    /** Whether using this kind of action here is refused. A failed lookup allows it and says so. */
    static boolean blocked(ServerPlayer player, String kind) {
        if (!AVAILABLE || ALWAYS_ALLOWED.contains(kind)) {
            return false;
        }
        try {
            UUID owner = Lookup.owner(player);
            if (DEBUG) {
                LOGGER.info("claim at {} owner={} player={}", player.blockPosition(), owner, player.getUUID());
            }
            return forbids(owner, player.getUUID());
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.error("ALERT: claim lookup failed for {}; allowing the weapon", player.getName().getString(), failure);
            return false;
        }
    }

    /** The Open Parties and Claims calls, isolated so the mod loads without it. */
    private static final class Lookup {
        static UUID owner(ServerPlayer player) {
            var claim = xaero.pac.common.server.api.OpenPACServerAPI.get(player.level().getServer()).getServerClaimsManager()
                .get(player.level().dimension().identifier(), player.blockPosition());
            return claim == null ? null : claim.getPlayerId();
        }
    }
}
