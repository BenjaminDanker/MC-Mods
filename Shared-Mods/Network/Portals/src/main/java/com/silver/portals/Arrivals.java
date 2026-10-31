package com.silver.portals;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;

/**
 * Arriving on this server through a portal. While a player logs in, the proxy answers a login query with the
 * arrival they travelled to (channel {@code portals:arrival}). They are then created at that arrival point (their
 * saved position is replaced as it is read), and a few ticks after joining they are moved there in case the
 * early placement didn't apply (a first visit has no saved position).
 */
final class Arrivals {
    static final Identifier CHANNEL = Identifier.fromNamespaceAndPath("portals", "arrival");
    /** Long enough for the proxy hop and a sleeping target's startup. */
    private static final long MAX_AGE_MS = 30_000;
    /** Ticks after joining before the arrival move, so the client has its first chunks. */
    private static final int DELAY_TICKS = 8;

    private record Pending(UUID transfer, String arrival, long at) {
    }

    /** Announced by the proxy at login, not yet acted on. */
    private static final Map<UUID, Pending> announced = new ConcurrentHashMap<>();
    /** Joined; moved to the arrival when the count reaches 0. */
    private static final Map<UUID, Integer> countdown = new ConcurrentHashMap<>();
    private static final Map<UUID, String> moving = new ConcurrentHashMap<>();

    private Arrivals() {
    }

    static void register() {
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, sync) ->
            sender.sendPacket(CHANNEL, new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer())));
        ServerLoginNetworking.registerGlobalReceiver(CHANNEL, (server, handler, understood, buf, sync, responder) -> {
            if (!understood) {
                return; // not through the proxy (a direct connection)
            }
            try {
                // version 2: version byte, has-arrival flag, transfer id, arrival name, player, expiry
                if (buf.readUnsignedByte() != 2 || !buf.readBoolean()) {
                    return;
                }
                UUID transfer = buf.readUUID();
                String arrival = buf.readUtf(32767);
                UUID player = buf.readUUID();
                long expiresAt = buf.readLong();
                if (expiresAt <= System.currentTimeMillis()) {
                    PortalsMod.LOGGER.warn("Ignoring an expired arrival {} for {}", transfer, player);
                    return;
                }
                announced.put(player, new Pending(transfer, arrival, System.currentTimeMillis()));
                PortalsMod.LOGGER.info("{} is arriving at {} (transfer {})", player, arrival, transfer);
            } catch (RuntimeException broken) {
                PortalsMod.LOGGER.error("Could not read an arrival from the proxy", broken);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            Travel.joined(player.getUUID());
            server.execute(() -> {
                Pending pending = take(player.getUUID());
                if (pending != null) {
                    moving.put(player.getUUID(), pending.arrival());
                    countdown.put(player.getUUID(), DELAY_TICKS);
                    Portals.quietEndPortalRedirect(player);
                }
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            Travel.left(id);
            countdown.remove(id);
            moving.remove(id);
        });
        ServerTickEvents.END_SERVER_TICK.register(Arrivals::tick);
    }

    private static Pending take(UUID player) {
        Pending pending = announced.remove(player);
        if (pending != null && System.currentTimeMillis() - pending.at() > MAX_AGE_MS) {
            PortalsMod.LOGGER.warn("The arrival of {} at {} came too late; ignored", player, pending.arrival());
            return null;
        }
        return pending;
    }

    /** True while a player's arrival is announced or about to move them (portals and redirects leave them alone). */
    static boolean pending(UUID player) {
        Pending pending = announced.get(player);
        if (pending != null && System.currentTimeMillis() - pending.at() > MAX_AGE_MS) {
            announced.remove(player);
            pending = null;
        }
        return pending != null || countdown.containsKey(player);
    }

    private static void tick(MinecraftServer server) {
        for (Map.Entry<UUID, Integer> entry : countdown.entrySet()) {
            UUID id = entry.getKey();
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                countdown.remove(id);
                moving.remove(id);
                continue;
            }
            if (entry.getValue() > 1) {
                entry.setValue(entry.getValue() - 1);
                continue;
            }
            countdown.remove(id);
            String arrival = moving.remove(id);
            if (!moveTo(player, arrival)) {
                PortalsMod.LOGGER.warn("{} arrived for {}, but this server has no such arrival point", player.getName().getString(), arrival);
            }
        }
    }

    /**
     * The portal whose arrival receives travellers announced as {@code arrival}: one that names it as its {@code
     * toPortal}, else the portal of that name. Null when neither has an arrival point.
     */
    static PortalConfig.Portal arrivalPortal(String arrival) {
        if (arrival == null) {
            return null;
        }
        for (PortalConfig.Portal portal : PortalsMod.portals()) {
            if (arrival.equals(portal.toPortal) && portal.arrival != null) {
                return portal;
            }
        }
        for (PortalConfig.Portal portal : PortalsMod.portals()) {
            if (arrival.equals(portal.name) && portal.arrival != null) {
                return portal;
            }
        }
        return null;
    }

    /** Moves the player to the arrival point announced as {@code arrival}; false when there is none. */
    static boolean moveTo(ServerPlayer player, String arrival) {
        PortalConfig.Portal portal = arrivalPortal(arrival);
        if (portal == null) {
            return false;
        }
        PortalConfig.Arrival at = portal.arrival;
        ServerLevel level = world(player.level().getServer(), at);
        level.getChunk((int) Math.floor(at.x) >> 4, (int) Math.floor(at.z) >> 4);
        Portals.quietEndPortalRedirect(player);
        float yaw = at.yaw != null ? at.yaw : player.getYRot();
        float pitch = at.pitch != null ? at.pitch : player.getXRot();
        if (player.level() != level || player.distanceToSqr(at.x, at.y, at.z) >= 2.25) {
            player.teleportTo(level, at.x, at.y, at.z, EnumSet.noneOf(Relative.class), yaw, pitch, true);
        }
        Travel.cameThrough(player.getUUID(), null);
        PortalsMod.LOGGER.info("{} arrived at {} ({})", player.getName().getString(), portal.name, at);
        return true;
    }

    /**
     * While a joining player's saved data is read: if they are arriving, the same data placed at the arrival point,
     * so the server spawns them there directly. Null when they aren't arriving.
     */
    static CompoundTag placeAtArrival(MinecraftServer server, UUID player, CompoundTag saved) {
        Pending pending = announced.get(player);
        if (server == null || pending == null || System.currentTimeMillis() - pending.at() > MAX_AGE_MS) {
            return null;
        }
        PortalConfig.Portal portal = arrivalPortal(pending.arrival());
        if (portal == null) {
            return null;
        }
        PortalConfig.Arrival at = portal.arrival;
        CompoundTag placed = saved.copy();
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(at.x));
        position.add(DoubleTag.valueOf(at.y));
        position.add(DoubleTag.valueOf(at.z));
        placed.put("Pos", position);
        placed.putString("Dimension", world(server, at).dimension().identifier().toString());
        if (at.yaw != null || at.pitch != null) {
            ListTag rotation = new ListTag();
            rotation.add(FloatTag.valueOf(at.yaw != null ? at.yaw : 0F));
            rotation.add(FloatTag.valueOf(at.pitch != null ? at.pitch : 0F));
            placed.put("Rotation", rotation);
        }
        // no falling speed, fall damage or vehicle from where they were
        placed.remove("Motion");
        placed.remove("RootVehicle");
        placed.putDouble("fall_distance", 0D);
        return placed;
    }

    private static ServerLevel world(MinecraftServer server, PortalConfig.Arrival at) {
        String name = at.world == null || at.world.isBlank() ? "minecraft:overworld" : at.world.trim();
        Identifier id = switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "overworld" -> Identifier.withDefaultNamespace("overworld");
            case "nether", "the_nether" -> Identifier.withDefaultNamespace("the_nether");
            case "end", "the_end" -> Identifier.withDefaultNamespace("the_end");
            default -> Identifier.tryParse(name);
        };
        ServerLevel level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            PortalsMod.LOGGER.warn("Arrival world {} isn't loaded; using the overworld", name);
            return server.overworld();
        }
        return level;
    }
}
