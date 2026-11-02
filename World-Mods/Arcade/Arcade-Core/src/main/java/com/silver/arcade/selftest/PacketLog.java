package com.silver.arcade.selftest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Records what the server sends to the self-test's bots (class name plus a short summary of the interesting ones).
 * The mixin calls {@link #record}; it does nothing unless a self-test switched {@link #active} on and the receiver
 * is a registered bot.
 */
public final class PacketLog {
    /** One packet: server tick when it was sent, class name (no package), summary. */
    public record Rec(int tick, String type, String info) {
    }

    public static volatile boolean active;
    private static final Set<String> WATCHED = ConcurrentHashMap.newKeySet();
    /** One bot's packets: the newest {@link #KEEP}, numbered from {@code base}. Marks are absolute numbers, so trimming old ones keeps them valid. */
    private static final class Store {
        final List<Rec> list = new ArrayList<>();
        long base;
    }

    private static final Map<String, Store> LOG = new ConcurrentHashMap<>();
    private static final int KEEP = 300_000;

    private PacketLog() {
    }

    public static void watch(String name) {
        WATCHED.add(name);
        LOG.computeIfAbsent(name, n -> new Store());
    }

    public static void unwatch(String name) {
        WATCHED.remove(name);
        LOG.remove(name);
    }

    public static void stopAll() {
        active = false;
        WATCHED.clear();
        LOG.clear();
    }

    /** Called from the mixin for every packet sent to a player's connection. */
    public static void record(ServerPlayer player, Packet<?> packet) {
        if (!active) {
            return;
        }
        String name = player.getGameProfile().name();
        if (!WATCHED.contains(name)) {
            return;
        }
        Store store = LOG.get(name);
        if (store == null) {
            return;
        }
        synchronized (store) {
            add(store, player.level().getServer().getTickCount(), packet);
        }
    }

    private static void add(Store store, int tick, Packet<?> packet) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> sub : bundle.subPackets()) {
                add(store, tick, sub);
            }
            return;
        }
        List<Rec> list = store.list;
        if (list.size() >= KEEP * 3 / 2) {
            list.subList(0, KEEP / 2).clear(); // the oldest are dropped; marks stay valid (see Store.base)
            store.base += KEEP / 2;
        }
        String type = packet.getClass().getSimpleName();
        String info = "";
        try {
            info = switch (packet) {
                case net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket p -> p.text().getString();
                case net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket p -> p.text().getString();
                case net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket p -> p.text().getString();
                case net.minecraft.network.protocol.game.ClientboundSystemChatPacket p -> (p.overlay() ? "[bar] " : "[chat] ") + p.content().getString();
                case net.minecraft.network.protocol.common.ClientboundShowDialogPacket p -> dialogInfo(p.dialog().value());
                case net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket p -> p.payload().type().id().toString();
                case net.minecraft.network.protocol.game.ClientboundAddEntityPacket p -> p.getType().toShortString() + "#" + p.getId();
                case net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket p -> p.getEntityIds().toString();
                case net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket p -> "#" + p.id();
                case net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket p -> "#" + p.id();
                case net.minecraft.network.protocol.game.ClientboundRotateHeadPacket p -> "";
                case net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket p -> "slot " + p.getSlot() + " " + p.getItem().getItem();
                case net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket p -> "slot " + p.slot() + " " + p.contents().getItem();
                case net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket p -> p.getBlockState().getBlock().getDescriptionId();
                case net.minecraft.network.protocol.game.ClientboundSoundPacket p -> p.getSound().getRegisteredName();
                case net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket p -> p.getParticle().getType().toString();
                default -> "";
            };
        } catch (RuntimeException ignored) {
            // a summary is a nicety
        }
        list.add(new Rec(tick, type, info));
    }

    /** "title || body lines || buttons" of a dialog screen. */
    private static String dialogInfo(net.minecraft.server.dialog.Dialog dialog) {
        var common = dialog.common();
        StringBuilder out = new StringBuilder(common.title().getString()).append(" || ");
        for (var body : common.body()) {
            if (body instanceof net.minecraft.server.dialog.body.PlainMessage message) {
                out.append(message.contents().getString()).append(" ¦ ");
            }
        }
        out.append("|| ");
        if (dialog instanceof net.minecraft.server.dialog.MultiActionDialog multi) {
            for (var action : multi.actions()) {
                out.append(action.button().label().getString()).append(" ¦ ");
            }
        }
        return out.toString();
    }

    /** A copy of everything still kept for a bot. */
    public static List<Rec> snapshot(String name) {
        Store store = LOG.get(name);
        if (store == null) {
            return List.of();
        }
        synchronized (store) {
            return new ArrayList<>(store.list);
        }
    }

    /** A number to ask {@link #since} with later. */
    public static long mark(String name) {
        Store store = LOG.get(name);
        if (store == null) {
            return 0;
        }
        synchronized (store) {
            return store.base + store.list.size();
        }
    }

    /** Recorded since {@code mark} (a number from {@link #mark}). */
    public static List<Rec> since(String name, long mark) {
        Store store = LOG.get(name);
        if (store == null) {
            return List.of();
        }
        synchronized (store) {
            long from = Math.max(0, mark - store.base);
            return from >= store.list.size() ? List.of() : new ArrayList<>(store.list.subList((int) from, store.list.size()));
        }
    }

    public static boolean has(List<Rec> recs, String type) {
        return recs.stream().anyMatch(r -> r.type().equals(type));
    }

    /** True when a packet of any of the '|'-separated class names was recorded. */
    public static boolean hasAny(List<Rec> recs, String types) {
        for (String type : types.split("\\|")) {
            if (has(recs, type)) {
                return true;
            }
        }
        return false;
    }

    /** True when a packet of that class with the text in its summary was recorded. */
    public static boolean hasText(List<Rec> recs, String type, String text) {
        return recs.stream().anyMatch(r -> r.type().equals(type) && r.info().contains(text));
    }

    public static long count(List<Rec> recs, String type) {
        return recs.stream().filter(r -> r.type().equals(type)).count();
    }

    public static String text(Component c) {
        return c == null ? "" : c.getString();
    }
}
