package com.silver.skyislands.portal;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VoidDeathRedirectHandler {
    private static final String SKY_ISLANDS_ENTRANCE_PORTAL = "sky-island_entrance";
    private static final Logger LOGGER = LoggerFactory.getLogger(VoidDeathRedirectHandler.class);
    private static final long PENDING_REDIRECT_GRACE_MS = 5_000L;
    private static final Map<UUID, Long> pendingRedirects = new ConcurrentHashMap<>();

    private VoidDeathRedirectHandler() {
    }

    public static void init() {
        LOGGER.info("[Sky-Islands][portal] VoidDeathRedirectHandler initialized in lethal-damage mode");
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            pendingRedirects.remove(handler.getPlayer().getUuid());
        });
    }

    public static boolean tryRedirectBeforeLethalVoidDamage(
        ServerPlayerEntity player,
        ServerWorld world,
        DamageSource damageSource,
        float amount
    ) {
        if (player == null || world == null) {
            return false;
        }

        if (player.isSpectator() || !PortalRedirector.isPortalRedirectEnabled()) {
            return false;
        }

        if (!isVoidDamage(damageSource)) {
            return false;
        }

        if (player.getY() >= world.getBottomY()) {
            return false;
        }

        if (!isLethalDamage(player, amount)) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long pendingUntil = pendingRedirects.get(player.getUuid());
        if (pendingUntil != null) {
            if (pendingUntil >= now) {
                return true;
            }
            pendingRedirects.remove(player.getUuid());
        }

        LOGGER.info("[Sky-Islands][portal] lethal void redirect trigger for {} in world {} pos=({}, {}, {}) bottomY={} health={} absorption={} damage={}",
            player.getName().getString(),
            world.getRegistryKey().getValue(),
            player.getX(),
            player.getY(),
            player.getZ(),
            world.getBottomY(),
            player.getHealth(),
            player.getAbsorptionAmount(),
            amount);

        boolean stagedAtArrival = tryStageAtSkyIslandsArrival(player);
        if (!stagedAtArrival) {
            LOGGER.info("[Sky-Islands][portal] local arrival staging failed for {}; continuing with redirect anyway",
                player.getName().getString());
        }

        if (!PortalRedirector.redirectToConfiguredServer(player, "lethal void damage", PortalRedirector.VANILLA_ENTRANCE_PORTAL)) {
            LOGGER.info("[Sky-Islands][portal] lethal void redirect did not fire for {}",
                player.getName().getString());
            return stagedAtArrival;
        }

        pendingRedirects.put(player.getUuid(), now + PENDING_REDIRECT_GRACE_MS);
        LOGGER.info("Prevented lethal Sky-Islands void damage for {} via local arrival staging and proxy redirect", player.getName().getString());
        return true;
    }

    private static boolean isVoidDamage(DamageSource damageSource) {
        return damageSource != null && damageSource.isOf(DamageTypes.OUT_OF_WORLD);
    }

    private static boolean isLethalDamage(ServerPlayerEntity player, float amount) {
        float effectiveHealth = player.getHealth() + player.getAbsorptionAmount();
        return effectiveHealth > 0.0F && amount >= effectiveHealth;
    }

    private static boolean tryStageAtSkyIslandsArrival(ServerPlayerEntity player) {
        try {
            Class<?> modClass = Class.forName("de.michiruf.serverportals.ServerPortalsMod");
            Field configField = modClass.getField("CONFIG");
            Object config = configField.get(null);
            if (config == null) {
                LOGGER.info("[Sky-Islands][portal] local arrival staging skipped for {}: ServerPortals config missing",
                    player.getName().getString());
                return false;
            }

            Method portalsMethod = config.getClass().getMethod("portals");
            Object portalsObj = portalsMethod.invoke(config);
            if (!(portalsObj instanceof Iterable<?> portals)) {
                LOGGER.info("[Sky-Islands][portal] local arrival staging skipped for {}: portal list missing",
                    player.getName().getString());
                return false;
            }

            Object matchedPortal = null;
            String matchedPortalName = SKY_ISLANDS_ENTRANCE_PORTAL;
            for (Object portal : portals) {
                if (portal == null) {
                    continue;
                }

                Method indexMethod = portal.getClass().getMethod("index");
                String index = (String) indexMethod.invoke(portal);
                if (index == null || !index.equalsIgnoreCase(SKY_ISLANDS_ENTRANCE_PORTAL)) {
                    continue;
                }

                Method arrivalLocationMethod = portal.getClass().getMethod("arrivalLocation");
                Object arrivalLocation = arrivalLocationMethod.invoke(portal);
                if (arrivalLocation == null) {
                    continue;
                }

                matchedPortal = portal;
                break;
            }

            if (matchedPortal == null) {
                LOGGER.info("[Sky-Islands][portal] local arrival staging skipped for {}: no local portal entry named {}",
                    player.getName().getString(),
                    SKY_ISLANDS_ENTRANCE_PORTAL);
                return false;
            }

            Method teleportMethod = modClass.getDeclaredMethod(
                "teleportPlayerToArrival",
                ServerPlayerEntity.class,
                matchedPortal.getClass(),
                String.class,
                String.class
            );
            teleportMethod.setAccessible(true);
            Object result = teleportMethod.invoke(null, player, matchedPortal, matchedPortalName, "sky-islands void rescue");
            boolean teleported = result instanceof Boolean && (Boolean) result;
            if (!teleported) {
                LOGGER.info("[Sky-Islands][portal] local arrival staging failed for {} via portal {}",
                    player.getName().getString(),
                    matchedPortalName);
                return false;
            }

            player.setHealth(player.getMaxHealth());
            player.setAbsorptionAmount(0.0F);
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance = 0.0F;
            LOGGER.info("[Sky-Islands][portal] local arrival staging teleported {} via portal {} to pos=({}, {}, {})",
                player.getName().getString(),
                matchedPortalName,
                player.getX(),
                player.getY(),
                player.getZ());
            return true;
        } catch (Exception ex) {
            LOGGER.info("[Sky-Islands][portal] local arrival staging failed for {}: {}",
                player.getName().getString(),
                ex.getMessage());
            return false;
        }
    }
}