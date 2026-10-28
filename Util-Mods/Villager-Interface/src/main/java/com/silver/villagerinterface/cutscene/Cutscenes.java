package com.silver.villagerinterface.cutscene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.silver.villagerinterface.VillagerInterfaceMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Short in-world scenes. A scene plays on its own (watching is the default) and sneaking skips it, which works the
 * same on Java and Bedrock. Java players watch through a camera that glides along the scene's keyframes (they are put
 * in spectator mode, looking through an invisible display entity, and put back exactly where and how they were);
 * Bedrock players, whose client may not follow the camera, get the same lines as titles where they stand.
 *
 * <p>Scenes are JSON: config/villagerinterface/cutscenes/ID.json, else the copy inside this mod. Keyframes are
 * relative to an anchor and its facing (forward, right, up in blocks; yaw and pitch in degrees), so one scene works
 * wherever it is played. Lines are shown at their tick as a title, subtitle and/or chat line.
 *
 * <p>Other mods call {@link #play} by name.
 */
public final class Cutscenes {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final class Keyframe {
        int t;
        double forward;
        double right;
        double up;
        float yaw;
        float pitch;
    }

    static final class Line {
        int t;
        String title;
        String subtitle;
        String chat;
    }

    static final class Scene {
        List<Keyframe> keyframes = new ArrayList<>();
        List<Line> lines = new ArrayList<>();

        int duration() {
            int end = 0;
            for (Keyframe k : keyframes) {
                end = Math.max(end, k.t);
            }
            for (Line l : lines) {
                end = Math.max(end, l.t + 60);
            }
            return end;
        }
    }

    /** Where and how a player was before the scene, so they are put back even after a crash (saved to disk). */
    static final class Saved {
        String dimension;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        String gameMode;
    }

    private static final class Running {
        final Scene scene;
        final Entity camera;
        final long startTick;
        final double ax;
        final double ay;
        final double az;
        final float anchorYaw;
        int nextLine;
        boolean wasSneaking = true;

        Running(Scene scene, Entity camera, long startTick, double ax, double ay, double az, float anchorYaw) {
            this.scene = scene;
            this.camera = camera;
            this.startTick = startTick;
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.anchorYaw = anchorYaw;
        }
    }

    private static final Map<UUID, Running> RUNNING = new HashMap<>();
    /** Cameras of scenes playing now (filled before the camera enters the world, so the stray-camera cleanup spares it). */
    private static final Set<Entity> LIVE_CAMERAS = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static Map<String, Saved> saved = new HashMap<>();
    private static MinecraftServer server;
    private static Path savedFile;

    private Cutscenes() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            savedFile = started.getWorldPath(LevelResource.ROOT).resolve("villagerinterface").resolve("cutscene-restore.json");
            saved = loadSaved();
        });
        ServerTickEvents.END_SERVER_TICK.register(ticking -> tick());
        // A camera left behind by a crash is removed as soon as its chunk loads.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains("villagerinterface_camera") && !LIVE_CAMERAS.contains(entity)) {
                entity.discard();
            }
        });
        // A player who left (or whose server crashed) mid-scene is put back when they come again.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> {
            ServerPlayer player = handler.getPlayer();
            Saved state = saved.get(player.getStringUUID());
            if (state != null) {
                restore(player, state);
            }
        });
        // Leaving mid-scene: put the player back before their data is saved.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, left) -> {
            Running running = RUNNING.remove(handler.getPlayer().getUUID());
            if (running != null) {
                end(handler.getPlayer(), running);
            }
        });
    }

    /**
     * Plays scene {@code id} for the player, anchored at (x, y, z) facing {@code yaw}. Returns how long it lasts in ticks,
     * or 0 when it did not start (unknown scene, or the player is already watching one).
     */
    public static int play(ServerPlayer player, String id, double x, double y, double z, float yaw) {
        if (server == null || RUNNING.containsKey(player.getUUID()) || player.isSpectator()) {
            return 0;
        }
        Scene scene = load(id);
        if (scene == null || scene.keyframes.isEmpty()) {
            VillagerInterfaceMod.LOGGER.warn("Cutscene {} is missing or has no keyframes", id);
            return 0;
        }
        Entity camera = null;
        if (!isBedrock(player)) {
            camera = spawnCamera((ServerLevel) player.level());
            if (camera != null) {
                Saved state = new Saved();
                state.dimension = player.level().dimension().identifier().toString();
                state.x = player.getX();
                state.y = player.getY();
                state.z = player.getZ();
                state.yaw = player.getYRot();
                state.pitch = player.getXRot();
                state.gameMode = player.gameMode().getName();
                saved.put(player.getStringUUID(), state);
                writeSaved();
                place(camera, scene.keyframes.get(0), x, y, z, yaw);
                player.setGameMode(GameType.SPECTATOR);
                player.setCamera(camera);
            }
        }
        RUNNING.put(player.getUUID(), new Running(scene, camera, server.getTickCount(), x, y, z, yaw));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 15));
        VillagerInterfaceMod.LOGGER.info("{} is watching cutscene {}{}", player.getName().getString(), id, camera == null ? " (titles only)" : "");
        return scene.duration();
    }

    /** Floodgate gives Bedrock players a UUID whose upper half is zero. */
    static boolean isBedrock(ServerPlayer player) {
        return player.getUUID().getMostSignificantBits() == 0L;
    }

    private static void tick() {
        if (RUNNING.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (var it = RUNNING.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Running running = entry.getValue();
            if (player == null) {
                it.remove();
                discard(running);
                continue;
            }
            int t = (int) (now - running.startTick);
            boolean skipped;
            if (running.camera != null) {
                // Sneaking makes the client leave the camera by itself.
                skipped = t > 5 && player.getCamera() != running.camera;
            } else {
                boolean sneaking = player.isShiftKeyDown();
                skipped = sneaking && !running.wasSneaking;
                running.wasSneaking = sneaking;
            }
            if (skipped || t >= running.scene.duration()) {
                it.remove();
                end(player, running);
                continue;
            }
            if (running.camera != null) {
                move(running, t);
            }
            while (running.nextLine < running.scene.lines.size() && running.scene.lines.get(running.nextLine).t <= t) {
                show(player, running.scene.lines.get(running.nextLine++));
            }
            if (t % 20 == 0) {
                player.sendSystemMessage(Component.literal("Sneak to skip").withStyle(ChatFormatting.DARK_GRAY), true);
            }
        }
    }

    private static void show(ServerPlayer player, Line line) {
        if (line.title != null || line.subtitle != null) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(line.subtitle == null ? "" : line.subtitle)
                .withStyle(ChatFormatting.GRAY)));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(line.title == null ? "" : line.title)
                .withStyle(ChatFormatting.GOLD)));
        }
        if (line.chat != null) {
            player.sendSystemMessage(Component.literal(line.chat).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    private static void move(Running running, int t) {
        List<Keyframe> keys = running.scene.keyframes;
        Keyframe a = keys.get(0);
        Keyframe b = a;
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).t <= t) {
                a = keys.get(i);
                b = i + 1 < keys.size() ? keys.get(i + 1) : a;
            }
        }
        double f = b.t == a.t ? 1.0 : Math.min(1.0, Math.max(0.0, (t - a.t) / (double) (b.t - a.t)));
        f = f * f * (3 - 2 * f);
        Keyframe at = new Keyframe();
        at.forward = a.forward + (b.forward - a.forward) * f;
        at.right = a.right + (b.right - a.right) * f;
        at.up = a.up + (b.up - a.up) * f;
        at.yaw = (float) (a.yaw + (b.yaw - a.yaw) * f);
        at.pitch = (float) (a.pitch + (b.pitch - a.pitch) * f);
        place(running.camera, at, running.ax, running.ay, running.az, running.anchorYaw);
    }

    /** Puts the camera at a keyframe relative to the anchor; Minecraft yaw 0 faces south (+z) and turns west. */
    private static void place(Entity camera, Keyframe key, double ax, double ay, double az, float anchorYaw) {
        double rad = Math.toRadians(anchorYaw);
        double forwardX = -Math.sin(rad);
        double forwardZ = Math.cos(rad);
        double rightX = -Math.cos(rad);
        double rightZ = -Math.sin(rad);
        double x = ax + forwardX * key.forward + rightX * key.right;
        double z = az + forwardZ * key.forward + rightZ * key.right;
        camera.teleportTo((ServerLevel) camera.level(), x, ay + key.up, z, Set.of(), anchorYaw + key.yaw, key.pitch, false);
    }

    private static Entity spawnCamera(ServerLevel level) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("teleport_duration", 3);
        Entity camera = EntityType.loadEntityRecursive(net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY, tag, level, EntitySpawnReason.COMMAND, entity -> entity);
        if (camera == null) {
            return null;
        }
        camera.addTag("villagerinterface_camera");
        LIVE_CAMERAS.add(camera);
        level.addFreshEntity(camera);
        return camera;
    }

    private static void end(ServerPlayer player, Running running) {
        discard(running);
        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        Saved state = saved.get(player.getStringUUID());
        if (state != null) {
            restore(player, state);
        }
    }

    private static void discard(Running running) {
        if (running.camera != null) {
            LIVE_CAMERAS.remove(running.camera);
            running.camera.discard();
        }
    }

    private static void restore(ServerPlayer player, Saved state) {
        player.setCamera(player);
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(state.dimension)));
        if (level != null) {
            player.teleportTo(level, state.x, state.y, state.z, Set.of(), state.yaw, state.pitch, false);
        }
        player.setGameMode(GameType.byName(state.gameMode, GameType.SURVIVAL));
        saved.remove(player.getStringUUID());
        writeSaved();
    }

    private static Scene load(String id) {
        if (!id.matches("[a-z0-9_]+")) {
            return null;
        }
        Path override = FabricLoader.getInstance().getConfigDir().resolve("villagerinterface").resolve("cutscenes").resolve(id + ".json");
        try {
            if (Files.exists(override)) {
                return GSON.fromJson(Files.readString(override), Scene.class);
            }
            try (InputStream in = Cutscenes.class.getResourceAsStream("/villagerinterface/cutscenes/" + id + ".json")) {
                return in == null ? null : GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Scene.class);
            }
        } catch (IOException | RuntimeException failure) {
            VillagerInterfaceMod.LOGGER.error("Could not read cutscene {}", id, failure);
            return null;
        }
    }

    private static Map<String, Saved> loadSaved() {
        try {
            if (Files.exists(savedFile)) {
                JsonObject json = GSON.fromJson(Files.readString(savedFile), JsonObject.class);
                Map<String, Saved> out = new HashMap<>();
                json.entrySet().forEach(e -> out.put(e.getKey(), GSON.fromJson(e.getValue(), Saved.class)));
                return out;
            }
        } catch (IOException | RuntimeException failure) {
            VillagerInterfaceMod.LOGGER.error("ALERT: could not read {}; players caught mid-scene are not put back", savedFile, failure);
        }
        return new HashMap<>();
    }

    private static void writeSaved() {
        if (savedFile == null) {
            return;
        }
        try {
            Files.createDirectories(savedFile.getParent());
            Path temporary = savedFile.resolveSibling(savedFile.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(saved));
            Files.move(temporary, savedFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            VillagerInterfaceMod.LOGGER.error("ALERT: could not save {}", savedFile, failure);
        }
    }
}
