package com.silver.spawnprotect.protect;

import com.silver.spawnprotect.SpawnProtectMod;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Replaces OpenPAC's denial chat with its own action-bar claim display ("Server Claim", "Wilderness", ...).
 * OpenPAC re-sends that bar whenever a player's remembered claim differs from the one they stand in, so
 * forgetting the remembered claim makes the native bar reappear on the next tick. Rate limited so repeated
 * clicks cannot spam it.
 */
public final class OpenPacClaimBar {
    private static final long COOLDOWN_MILLIS = 3_000L;
    private static final Map<UUID, Long> LAST_SHOWN = new ConcurrentHashMap<>();
    private static volatile boolean failed;
    private static Method from;
    private static Method setLastClaimCheck;

    private OpenPacClaimBar() {
    }

    public static void show(ServerPlayer player) {
        if (player == null || failed) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_SHOWN.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_MILLIS) {
            return;
        }
        LAST_SHOWN.put(player.getUUID(), now);
        try {
            if (from == null) {
                Class<?> api = Class.forName("xaero.pac.common.server.player.data.api.ServerPlayerDataAPI");
                from = api.getMethod("from", ServerPlayer.class);
            }
            Object data = from.invoke(null, player);
            if (data == null) {
                return; // OpenPAC has not loaded this player's data yet
            }
            if (setLastClaimCheck == null) {
                for (Method method : data.getClass().getMethods()) {
                    if (method.getName().equals("setLastClaimCheck") && method.getParameterCount() == 1) {
                        setLastClaimCheck = method;
                        break;
                    }
                }
                if (setLastClaimCheck == null) {
                    throw new NoSuchMethodException("setLastClaimCheck");
                }
            }
            setLastClaimCheck.invoke(data, new Object[] {null});
        } catch (ReflectiveOperationException e) {
            // OpenPAC's API changed: stop trying until restart.
            failed = true;
            SpawnProtectMod.LOGGER.error("[SpawnProtect] ALERT OpenPAC claim bar unavailable; denied actions stay silent: {}", e.toString());
        } catch (RuntimeException e) {
            SpawnProtectMod.LOGGER.warn("[SpawnProtect] OpenPAC claim bar failed for {}: {}", player.getName().getString(), e.toString());
        }
    }

    public static void forget(UUID playerId) {
        LAST_SHOWN.remove(playerId);
    }
}
