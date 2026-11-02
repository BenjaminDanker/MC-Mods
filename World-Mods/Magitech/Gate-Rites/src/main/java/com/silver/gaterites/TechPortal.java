package com.silver.gaterites;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Tech side. Someone who has finished the Tech story lights a steel frame with a Gate Core (the pinnacle of their science).
 * The gate is theirs alone: nobody else can step through, and it breaks the moment they do. The frame stays.
 */
final class TechPortal {
    private static final Logger LOGGER = LoggerFactory.getLogger("gate-rites");
    private static final int MAX_PORTAL_BLOCKS = 600;
    private static final Identifier GATE_CORE = Identifier.fromNamespaceAndPath("gaterites", "gate_core");

    private static final class Lit {
        final UUID owner;
        final ServerLevel level;
        final List<BlockPos> blocks;
        final AABB hull;
        final Vec3 centre;
        final long closeAtTick;
        final Map<UUID, Long> lastRefusal = new HashMap<>();

        Lit(UUID owner, ServerLevel level, List<BlockPos> blocks, long closeAtTick) {
            this.owner = owner;
            this.level = level;
            this.blocks = blocks;
            AABB box = new AABB(blocks.getFirst());
            for (BlockPos pos : blocks) {
                box = box.minmax(new AABB(pos));
            }
            this.hull = box;
            this.centre = box.getCenter();
            this.closeAtTick = closeAtTick;
        }
    }

    private final MinecraftServer server;
    private final GateConfig config;
    private final Crossing crossing;
    private final Block frame;
    private final Item gateCore;
    private final Item ignition;
    private final List<Lit> lit = new ArrayList<>();

    TechPortal(MinecraftServer server, GateConfig config) {
        this.server = server;
        this.config = config;
        this.crossing = new Crossing(config);
        this.frame = BuiltInRegistries.BLOCK.getValue(Identifier.parse(config.frameBlock));
        this.gateCore = BuiltInRegistries.ITEM.getValue(GATE_CORE);
        this.ignition = BuiltInRegistries.ITEM.getValue(Identifier.parse(config.ignitionItem));
        clearLeftovers();
        UseBlockCallback.EVENT.register(this::onUse);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        LOGGER.info("Tech gate active: a Gate Core on a {} frame opens a gate for its finisher alone ({}s)", config.frameBlock, config.openSeconds);
    }

    private InteractionResult onUse(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level world,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (world.isClientSide() || !(player instanceof ServerPlayer user) || !(world instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        ItemStack held = user.getItemInHand(hand);
        if (!held.is(gateCore) || !level.getBlockState(hit.getBlockPos()).is(frame)) {
            return InteractionResult.PASS;
        }
        if (!user.entityTags().contains(config.completedTag)) {
            user.sendSystemMessage(Component.literal("The Gate Core stays dark. Only one who has finished the story of Tech can wake it.")
                .withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        BlockPos inside = tryLight(level, user, hit.getBlockPos());
        if (inside == null) {
            user.sendSystemMessage(Component.literal("The Gate Core finds no complete frame of steel here.").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        lit.stream().filter(gate -> gate.owner.equals(user.getUUID())).toList().forEach(this::remove);
        List<BlockPos> blocks = portalBlocks(level, inside);
        Lit gate = new Lit(user.getUUID(), level, blocks, server.getTickCount() + config.openSeconds * 20L);
        lit.add(gate);
        persist();
        if (!user.getAbilities().instabuild) {
            held.shrink(1);
        }
        level.playSound(null, gate.centre.x, gate.centre.y, gate.centre.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.4F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, gate.centre.x, gate.centre.y, gate.centre.z, 80, 0.8, 1.2, 0.8, 0.3);
        user.sendSystemMessage(Component.literal("The Gate Core flares and is spent. The gate is yours alone, and it will not wait long.")
            .withStyle(ChatFormatting.AQUA));
        LOGGER.info("{} lit a gate at {} ({} portal blocks)", user.getName().getString(), inside, blocks.size());
        return InteractionResult.SUCCESS;
    }

    /** The position beside the clicked frame block that Portals accepted as the inside of a gate, or null. */
    private BlockPos tryLight(ServerLevel level, ServerPlayer player, BlockPos clicked) {
        for (Direction direction : Direction.values()) {
            BlockPos inside = clicked.relative(direction).immutable();
            if (level.getBlockState(inside).isAir() && com.silver.portals.Portals.light(level, inside, ignition, player)) {
                return inside;
            }
        }
        return null;
    }

    /** Every portal block joined to {@code start}. */
    private static List<BlockPos> portalBlocks(ServerLevel level, BlockPos start) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && found.size() < MAX_PORTAL_BLOCKS) {
            BlockPos pos = queue.poll();
            if (!(com.silver.portals.Portals.isPortal(level.getBlockState(pos)))) {
                continue;
            }
            found.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction).immutable();
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        if (found.isEmpty()) {
            found.add(start);
        }
        return found;
    }

    private void tick(MinecraftServer unused) {
        if (lit.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (Iterator<Lit> it = lit.iterator(); it.hasNext();) {
            Lit gate = it.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(gate.owner);
            if (owner == null) {
                breakGate(gate);
                it.remove();
                persist();
                LOGGER.info("The gate of an absent owner ({}) was closed", gate.owner);
                continue;
            }
            if (now >= gate.closeAtTick) {
                owner.sendSystemMessage(Component.literal("The gate collapses.").withStyle(ChatFormatting.GRAY));
                breakGate(gate);
                it.remove();
                persist();
                continue;
            }
            if (owner.level() == gate.level && owner.getBoundingBox().intersects(gate.hull)) {
                if (crossing.send(owner)) {
                    LOGGER.info("{} stepped through their gate", owner.getName().getString());
                    gate.level.playSound(null, gate.centre.x, gate.centre.y, gate.centre.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);
                    gate.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, gate.centre.x, gate.centre.y, gate.centre.z, 120, 0.8, 1.2, 0.8, 0.4);
                } else {
                    owner.sendSystemMessage(Component.literal("The gate flickers and will not carry you. Tell an admin.").withStyle(ChatFormatting.RED));
                }
                breakGate(gate);
                it.remove();
                persist();
                continue;
            }
            AABB warding = gate.hull.inflate(0.6);
            for (ServerPlayer other : gate.level.players()) {
                if (other != owner && warding.intersects(other.getBoundingBox())) {
                    long last = gate.lastRefusal.getOrDefault(other.getUUID(), Long.MIN_VALUE / 2);
                    boolean tell = now - last >= Gate.REFUSAL_SPACING_TICKS;
                    if (tell) {
                        gate.lastRefusal.put(other.getUUID(), now);
                    }
                    Gate.refuse(other, awayFromPlane(gate, other), tell);
                }
            }
        }
    }

    /** Out of the gate's plane, to whichever side the stranger is on: pushing along the frame would only jam them against it. */
    private static Vec3 awayFromPlane(Lit gate, ServerPlayer stranger) {
        Vec3 offset = stranger.position().subtract(gate.centre);
        boolean planeSpansX = gate.hull.getXsize() > gate.hull.getZsize();
        return planeSpansX ? new Vec3(0, 0, offset.z >= 0 ? 1 : -1) : new Vec3(offset.x >= 0 ? 1 : -1, 0, 0);
    }

    void shutdown() {
        for (Lit gate : List.copyOf(lit)) {
            remove(gate);
        }
    }

    private void remove(Lit gate) {
        lit.remove(gate);
        breakGate(gate);
        persist();
    }

    /** Removes the lit portal blocks and leaves the frame. */
    private static void breakGate(Lit gate) {
        for (BlockPos pos : gate.blocks) {
            if (com.silver.portals.Portals.isPortal(gate.level.getBlockState(pos))) {
                gate.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    // ------------------------------------------------------------------ surviving a crash

    private static Path stateFile() {
        return FabricLoader.getInstance().getGameDir().resolve("gate-rites-open.json");
    }

    /** The lit portal blocks are written down while a gate is open, so a crash cannot leave one burning with nobody it is for. */
    private void persist() {
        JsonArray entries = new JsonArray();
        for (Lit gate : lit) {
            for (BlockPos pos : gate.blocks) {
                JsonObject entry = new JsonObject();
                entry.addProperty("dimension", gate.level.dimension().identifier().toString());
                entry.addProperty("x", pos.getX());
                entry.addProperty("y", pos.getY());
                entry.addProperty("z", pos.getZ());
                entries.add(entry);
            }
        }
        try {
            if (entries.isEmpty()) {
                Files.deleteIfExists(stateFile());
            } else {
                Files.writeString(stateFile(), new Gson().toJson(entries));
            }
        } catch (IOException failure) {
            LOGGER.error("ALERT: could not record the open gate; a crash would leave its portal lit", failure);
        }
    }

    private void clearLeftovers() {
        Path file = stateFile();
        if (!Files.exists(file)) {
            return;
        }
        int removed = 0;
        try {
            for (var element : JsonParser.parseString(Files.readString(file)).getAsJsonArray()) {
                JsonObject entry = element.getAsJsonObject();
                Identifier dimension = Identifier.parse(entry.get("dimension").getAsString());
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.dimension().identifier().equals(dimension)) {
                        BlockPos pos = new BlockPos(entry.get("x").getAsInt(), entry.get("y").getAsInt(), entry.get("z").getAsInt());
                        if (com.silver.portals.Portals.isPortal(level.getBlockState(pos))) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                            removed++;
                        }
                    }
                }
            }
            Files.deleteIfExists(file);
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("ALERT: could not clear the gate left open before the last stop ({})", file, failure);
            return;
        }
        LOGGER.info("Cleared {} portal blocks left lit by the last run", removed);
    }
}
