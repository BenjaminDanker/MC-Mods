package com.silver.villagerinterface.cutscene;

import com.mojang.datafixers.util.Pair;
import com.silver.villagerinterface.VillagerInterfaceMod;
import com.silver.villagerinterface.mixin.ChunkMapAccessor;
import com.silver.villagerinterface.mixin.TextDisplayAccessor;
import com.silver.villagerinterface.mixin.TrackedEntityInvoker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Cutscene actors: player-shaped NPCs that exist only for the one player watching the scene (sent to their client as
 * packets, never added to the world), so every viewer gets their own performance and nobody else sees it. Actors walk
 * between keyframes (the client animates the legs from the movement), turn, look at the camera, crouch, lie down, swim,
 * glide, swing an arm and hold items; what they say is "typed" into a speech bubble over their head.
 *
 * <p>A scene may also hide real entities (by tag) from the viewer while it plays, e.g. the floating Gamemaster while
 * his scripted copy performs the introduction.
 */
public final class Actors {
    /** Viewer -> entity tags hidden from them right now. */
    private static final Map<UUID, Set<String>> HIDDEN = new ConcurrentHashMap<>();

    static final class Key {
        int t;
        double forward;
        double right;
        double up;
        float yaw;
        float pitch;
        /** "standing", "crouching", "sleeping", "swimming", "fall_flying". */
        String pose;
        boolean swing;
        /** Item id for the main hand ("" empties it). */
        String item;
        /** Turn to face the camera instead of using yaw/pitch. */
        boolean lookAtCamera;
    }

    static final class Say {
        int t;
        String text;
        /** How long the bubble stays after it is fully typed. */
        int hold = 50;
    }

    static final class Spec {
        String skin;
        boolean slim;
        String name;
        List<Key> keys = new ArrayList<>();
        List<Say> says = new ArrayList<>();
    }

    /** One actor playing for one viewer. */
    private static final class Live {
        final Spec spec;
        final Entity body;
        final Display.TextDisplay bubble;
        int nextKey;
        Say speaking;
        int speakingFrom;
        int nextSay;
        String shown = "";
        boolean bubbleSent;

        Live(Spec spec, Entity body, Display.TextDisplay bubble) {
            this.spec = spec;
            this.body = body;
            this.bubble = bubble;
        }
    }

    /** The actors of one running scene. */
    static final class Cast {
        final List<Live> live = new ArrayList<>();
        final List<String> hidden;

        Cast(List<String> hidden) {
            this.hidden = hidden;
        }
    }

    private Actors() {
    }

    /** True when {@code entity} must not be shown to {@code viewer} right now. Cheap when no scene hides anything. */
    public static boolean hiddenFrom(ServerPlayer viewer, Entity entity) {
        if (HIDDEN.isEmpty()) {
            return false;
        }
        Set<String> tags = HIDDEN.get(viewer.getUUID());
        if (tags == null) {
            return false;
        }
        for (String tag : entity.entityTags()) {
            if (tags.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /** Sends the actors to the viewer and hides the scene's hidden entities from them. */
    static Cast start(ServerPlayer viewer, List<Spec> specs, List<String> hide, Placement placement) {
        Cast cast = new Cast(hide == null ? List.of() : List.copyOf(hide));
        if (!cast.hidden.isEmpty()) {
            HIDDEN.put(viewer.getUUID(), Set.copyOf(cast.hidden));
            refresh(viewer, cast.hidden);
        }
        ServerLevel level = (ServerLevel) viewer.level();
        for (Spec spec : specs == null ? List.<Spec>of() : specs) {
            if (spec.keys.isEmpty()) {
                continue;
            }
            Entity body = mannequin(level, spec);
            Display.TextDisplay bubble = bubble(level);
            if (body == null || bubble == null) {
                continue;
            }
            Live live = new Live(spec, body, bubble);
            Vec3 at = placement.at(spec.keys.get(0).forward, spec.keys.get(0).right, spec.keys.get(0).up);
            float yaw = placement.yaw(spec.keys.get(0).yaw);
            body.snapTo(at.x, at.y, at.z, yaw, spec.keys.get(0).pitch);
            bubble.snapTo(at.x, at.y + 2.25, at.z, 0F, 0F);
            add(viewer, body, yaw);
            cast.live.add(live);
        }
        return cast;
    }

    /** Moves every actor to tick {@code t}; {@code camera} is where the viewer looks from. */
    static void tick(ServerPlayer viewer, Cast cast, int t, Placement placement, Vec3 camera) {
        for (Live live : cast.live) {
            List<Key> keys = live.spec.keys;
            Key a = keys.get(0);
            Key b = a;
            for (int i = 0; i < keys.size(); i++) {
                if (keys.get(i).t <= t) {
                    a = keys.get(i);
                    b = i + 1 < keys.size() ? keys.get(i + 1) : a;
                }
            }
            // Linear between keys, so walking looks like an even pace.
            double f = b.t == a.t ? 1.0 : Mth.clamp((t - a.t) / (double) (b.t - a.t), 0.0, 1.0);
            Vec3 at = placement.at(Mth.lerp(f, a.forward, b.forward), Mth.lerp(f, a.right, b.right), Mth.lerp(f, a.up, b.up));
            float yaw;
            float pitch;
            if (a.lookAtCamera && camera != null) {
                Vec3 eye = at.add(0, 1.62, 0);
                Vec3 d = camera.subtract(eye);
                yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            } else {
                yaw = placement.yaw(Mth.rotLerp((float) f, a.yaw, b.yaw));
                pitch = Mth.lerp((float) f, a.pitch, b.pitch);
            }
            live.body.snapTo(at.x, at.y, at.z, yaw, pitch);
            viewer.connection.send(ClientboundTeleportEntityPacket.teleport(live.body.getId(), PositionMoveRotation.of(live.body), Set.of(), true));
            viewer.connection.send(new ClientboundRotateHeadPacket(live.body, (byte) Mth.floor(yaw * 256F / 360F)));
            live.bubble.snapTo(at.x, at.y + bubbleHeight(live.body), at.z, 0F, 0F);
            viewer.connection.send(ClientboundTeleportEntityPacket.teleport(live.bubble.getId(), PositionMoveRotation.of(live.bubble), Set.of(), false));

            // Things that happen once, when a keyframe is reached.
            while (live.nextKey < keys.size() && keys.get(live.nextKey).t <= t) {
                Key key = keys.get(live.nextKey++);
                if (key.pose != null) {
                    live.body.setPose(pose(key.pose));
                    sendDirty(viewer, live.body);
                }
                if (key.swing) {
                    viewer.connection.send(new ClientboundAnimatePacket(live.body, 0));
                }
                if (key.item != null) {
                    viewer.connection.send(new ClientboundSetEquipmentPacket(live.body.getId(),
                        List.of(Pair.of(EquipmentSlot.MAINHAND, item(key.item)))));
                }
            }
            speak(viewer, live, t);
        }
    }

    /** Types the current line into the bubble, two letters a tick, then holds it. */
    private static void speak(ServerPlayer viewer, Live live, int t) {
        List<Say> says = live.spec.says;
        while (live.nextSay < says.size() && says.get(live.nextSay).t <= t) {
            live.speaking = says.get(live.nextSay++);
            live.speakingFrom = live.speaking.t;
        }
        String text = "";
        if (live.speaking != null) {
            int typed = (t - live.speakingFrom) * 2;
            String full = live.speaking.text == null ? "" : live.speaking.text;
            if (typed >= full.length() + live.speaking.hold * 2) {
                live.speaking = null;
            } else {
                text = full.substring(0, Math.min(full.length(), typed));
            }
        }
        if (text.equals(live.shown)) {
            return;
        }
        live.shown = text;
        ((TextDisplayAccessor) live.bubble).villagerinterface$setText(Component.literal(text));
        // The bubble exists on the client only while someone is talking (an empty one would still show its backing).
        if (text.isEmpty()) {
            if (live.bubbleSent) {
                viewer.connection.send(new ClientboundRemoveEntitiesPacket(live.bubble.getId()));
                live.bubbleSent = false;
            }
        } else if (!live.bubbleSent) {
            live.bubble.getEntityData().packDirty();
            add(viewer, live.bubble, 0F);
            live.bubbleSent = true;
        } else {
            sendDirty(viewer, live.bubble);
        }
    }

    /** Removes the actors from the viewer's client and shows them the hidden entities again. */
    static void stop(ServerPlayer viewer, Cast cast) {
        if (!cast.live.isEmpty()) {
            int[] ids = new int[cast.live.size() * 2];
            for (int i = 0; i < cast.live.size(); i++) {
                ids[2 * i] = cast.live.get(i).body.getId();
                ids[2 * i + 1] = cast.live.get(i).bubble.getId();
            }
            viewer.connection.send(new ClientboundRemoveEntitiesPacket(ids));
        }
        if (HIDDEN.remove(viewer.getUUID()) != null) {
            refresh(viewer, cast.hidden);
        }
    }

    /** The viewer left: forget what was hidden for them (nothing to send). */
    static void forget(UUID viewer) {
        HIDDEN.remove(viewer);
    }

    /** Makes the server re-decide, for this viewer, whether each tagged entity nearby is shown. */
    private static void refresh(ServerPlayer viewer, List<String> tags) {
        ServerLevel level = (ServerLevel) viewer.level();
        var map = ((ChunkMapAccessor) level.getChunkSource().chunkMap).villagerinterface$entityMap();
        for (Entity entity : level.getAllEntities()) {
            if (entity.entityTags().stream().anyMatch(tags::contains)) {
                Object tracked = map.get(entity.getId());
                if (tracked != null) {
                    ((TrackedEntityInvoker) tracked).villagerinterface$updatePlayer(viewer);
                }
            }
        }
    }

    private static void add(ServerPlayer viewer, Entity entity, float yaw) {
        viewer.connection.send(new ClientboundAddEntityPacket(entity.getId(), entity.getUUID(), entity.getX(), entity.getY(), entity.getZ(),
            entity.getXRot(), yaw, entity.getType(), 0, Vec3.ZERO, yaw));
        sendAll(viewer, entity);
    }

    private static void sendAll(ServerPlayer viewer, Entity entity) {
        List<SynchedEntityData.DataValue<?>> values = entity.getEntityData().getNonDefaultValues();
        if (values != null && !values.isEmpty()) {
            viewer.connection.send(new ClientboundSetEntityDataPacket(entity.getId(), values));
        }
    }

    private static void sendDirty(ServerPlayer viewer, Entity entity) {
        List<SynchedEntityData.DataValue<?>> values = entity.getEntityData().packDirty();
        if (values != null && !values.isEmpty()) {
            viewer.connection.send(new ClientboundSetEntityDataPacket(entity.getId(), values));
        }
    }

    private static double bubbleHeight(Entity body) {
        return switch (body.getPose()) {
            case CROUCHING -> 1.9;
            case SLEEPING, SWIMMING, FALL_FLYING -> 1.0;
            default -> 2.25;
        };
    }

    private static Pose pose(String name) {
        return switch (name) {
            case "crouching" -> Pose.CROUCHING;
            case "sleeping" -> Pose.SLEEPING;
            case "swimming" -> Pose.SWIMMING;
            case "fall_flying" -> Pose.FALL_FLYING;
            default -> Pose.STANDING;
        };
    }

    private static ItemStack item(String id) {
        if (id.isBlank()) {
            return ItemStack.EMPTY;
        }
        Identifier parsed = Identifier.tryParse(id);
        Item item = parsed == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(parsed);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static Entity mannequin(ServerLevel level, Spec spec) {
        try {
            StringBuilder nbt = new StringBuilder("{NoGravity:1b,Invulnerable:1b,immovable:1b,hide_description:1b");
            if (spec.skin != null && !spec.skin.isBlank()) {
                nbt.append(",profile:{texture:\"").append(spec.skin).append("\",model:\"").append(spec.slim ? "slim" : "wide").append("\"}");
            }
            if (spec.name != null && !spec.name.isBlank()) {
                nbt.append(",CustomName:\"").append(spec.name.replace("\"", "")).append("\",CustomNameVisible:1b");
            }
            nbt.append('}');
            CompoundTag tag = TagParser.parseCompoundFully(nbt.toString());
            return EntityType.loadEntityRecursive(EntityTypes.MANNEQUIN, tag, level, EntitySpawnReason.COMMAND, e -> e);
        } catch (Exception failure) {
            VillagerInterfaceMod.LOGGER.error("ALERT: a cutscene actor could not be made", failure);
            return null;
        }
    }

    private static Display.TextDisplay bubble(ServerLevel level) {
        try {
            CompoundTag tag = TagParser.parseCompoundFully("{billboard:\"center\",line_width:180,teleport_duration:3,"
                + "background:1610612736,shadow:1b,text:\"\"}");
            Entity entity = EntityType.loadEntityRecursive(EntityTypes.TEXT_DISPLAY, tag, level, EntitySpawnReason.COMMAND, e -> e);
            return entity instanceof Display.TextDisplay display ? display : null;
        } catch (Exception failure) {
            VillagerInterfaceMod.LOGGER.error("ALERT: a cutscene speech bubble could not be made", failure);
            return null;
        }
    }

    /** Converts scene-relative offsets (forward, right, up from the anchor, facing its yaw) to world positions. */
    record Placement(double ax, double ay, double az, float anchorYaw) {
        Vec3 at(double forward, double right, double up) {
            double rad = Math.toRadians(anchorYaw);
            double forwardX = -Math.sin(rad);
            double forwardZ = Math.cos(rad);
            double rightX = -Math.cos(rad);
            double rightZ = -Math.sin(rad);
            return new Vec3(ax + forwardX * forward + rightX * right, ay + up, az + forwardZ * forward + rightZ * right);
        }

        float yaw(float relative) {
            return anchorYaw + relative;
        }
    }
}
