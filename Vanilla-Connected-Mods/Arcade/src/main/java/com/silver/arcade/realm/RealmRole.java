package com.silver.arcade.realm;

import com.silver.arcade.ArcadeConfig;
import com.silver.arcade.ArcadeDb;
import com.silver.arcade.Transfers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The arcade server. Players arrive in the hall (around 0, 101, 0 of the void world). Stepping onto the start circle
 * gathers everyone standing on it within a few seconds into one run (up to four); the exit pad sends a player home.
 * Runs climb tier by tier; a cash-out records the prize for the player's home world.
 */
public final class RealmRole implements Run.Host {
    private static final Logger LOGGER = LoggerFactory.getLogger("arcade");
    /** Seconds a run, left mid-way, can be resumed. */
    private static final long RESUME_SECONDS = 30 * 60;
    private static final int GATHER_TICKS = 5 * 20;
    private static final int MAX_GROUP = 4;
    private static final BlockPos HALL = new BlockPos(0, 100, 0);
    private static final BlockPos START = new BlockPos(0, 100, 8);
    private static final BlockPos EXIT = new BlockPos(0, 100, -8);

    private final MinecraftServer server;
    private final ArcadeConfig config;
    private final ArcadeDb db;
    private final List<Run> runs = new ArrayList<>();
    private final Map<UUID, String> origins = new HashMap<>();
    private final Map<UUID, Long> leavingAt = new HashMap<>();
    private long gatherStartedAt = -1;

    public RealmRole(MinecraftServer server, ArcadeConfig config, ArcadeDb db) {
        this.server = server;
        this.config = config;
        this.db = db;
    }

    public void register() {
        RoomCatalog.registerAll();
        ensureHall();
        ServerTickEvents.END_SERVER_TICK.register(ticking -> tick());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, left) -> onLeave(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> runs.forEach(Run::abandon));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            Run run = runOf(sender.getUUID());
            return run == null || !run.chat(sender, message.signedContent());
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(player instanceof ServerPlayer user)) {
                return InteractionResult.PASS;
            }
            Run run = runOf(user.getUUID());
            return run != null && run.use(user, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!(player instanceof ServerPlayer user)) {
                return InteractionResult.PASS;
            }
            Run run = runOf(user.getUUID());
            return run != null && run.hit(user, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        LOGGER.info("Arcade realm open: {} room types", Rooms.all().size());
    }

    private ServerLevel level() {
        return server.overworld();
    }

    private Run runOf(UUID player) {
        for (Run run : runs) {
            if (run.has(player)) {
                return run;
            }
        }
        return null;
    }

    /** A plain hall if none is built yet (the real one is built over it). */
    private void ensureHall() {
        ServerLevel level = level();
        level.getChunk(HALL.getX() >> 4, HALL.getZ() >> 4);
        if (!level.getBlockState(HALL).isAir()) {
            return;
        }
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                level.setBlock(HALL.offset(x, 0, z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x * x + z * z <= 5) {
                    level.setBlock(START.offset(x, 0, z), Blocks.GLAZED_TERRACOTTA.purple().defaultBlockState(), 2);
                    level.setBlock(EXIT.offset(x, 0, z), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
                }
            }
        }
        for (int[] c : new int[][] {{-12, -12}, {12, -12}, {-12, 12}, {12, 12}}) {
            level.setBlock(HALL.offset(c[0], 1, c[1]), Blocks.LANTERN.defaultBlockState(), 2);
        }
        LOGGER.info("Built a plain arcade hall at {}", HALL);
    }

    private void onJoin(ServerPlayer player) {
        player.setGameMode(GameType.ADVENTURE);
        player.getInventory().clearContent();
        UUID id = player.getUUID();
        db.originOf(id).thenCombine(db.takeSuspended(id), (origin, suspended) -> new Object[] {origin, suspended})
            .whenComplete((both, failure) -> server.execute(() -> {
                ServerPlayer online = server.getPlayerList().getPlayer(id);
                if (online == null) {
                    return;
                }
                if (failure != null) {
                    LOGGER.error("ALERT: could not read arcade records for {}", online.getName().getString(), failure);
                    origins.put(id, "vanilla");
                    return;
                }
                @SuppressWarnings("unchecked")
                var origin = (java.util.Optional<String>) both[0];
                @SuppressWarnings("unchecked")
                var suspended = (java.util.Optional<ArcadeDb.Suspended>) both[1];
                origins.put(id, origin.orElse("vanilla"));
                if (suspended.isPresent() && suspended.get().ageSeconds() <= RESUME_SECONDS) {
                    ArcadeDb.Suspended s = suspended.get();
                    Gamemaster.say(online, "You came back. Your climb is where you left it: tier " + s.prizeTier() + " is yours to keep, tier "
                        + s.tier() + " is next.");
                    runs.add(new Run(server, level(), this, List.of(online), s.tier(), s.prizeTier(), true));
                    return;
                }
                if (suspended.isPresent()) {
                    Gamemaster.say(online, "You were gone too long. That climb is mine now.");
                }
                greet(online);
            }));
    }

    private void greet(ServerPlayer player) {
        if (player.addTag("arcade_met")) {
            Gamemaster.title(player, "The Arcade", "Every game is fair. Mostly.");
            Gamemaster.say(player, "A visitor! Welcome to my arcade. Step onto the purple circle to play: rooms get harder as you climb, "
                + "and after each one you may leave with a prize for your own world, or climb on and risk it. Bring friends if you like; "
                + "they share the climb, though not always the room. The crying obsidian takes you home.");
        } else {
            Gamemaster.say(player, "Back again? The circle is waiting.");
        }
    }

    private void onLeave(UUID id) {
        Run run = runOf(id);
        if (run != null) {
            run.left(id);
        }
        leavingAt.remove(id);
    }

    private void tick() {
        for (Iterator<Run> it = runs.iterator(); it.hasNext();) {
            Run run = it.next();
            run.tick();
            if (run.done()) {
                it.remove();
            }
        }
        if (server.getTickCount() % 5 != 0) {
            return;
        }
        List<ServerPlayer> onCircle = new ArrayList<>();
        for (ServerPlayer player : level().players()) {
            if (runOf(player.getUUID()) != null) {
                continue;
            }
            BlockPos below = player.blockPosition().below();
            if (near(below, START)) {
                onCircle.add(player);
            } else if (near(below, EXIT)) {
                leave(player);
            }
        }
        gather(onCircle);
    }

    private static boolean near(BlockPos pos, BlockPos centre) {
        int dx = pos.getX() - centre.getX();
        int dz = pos.getZ() - centre.getZ();
        return pos.getY() == centre.getY() && dx * dx + dz * dz <= 5;
    }

    private void gather(List<ServerPlayer> onCircle) {
        long now = server.getTickCount();
        if (onCircle.isEmpty()) {
            gatherStartedAt = -1;
            return;
        }
        if (gatherStartedAt < 0) {
            gatherStartedAt = now;
        }
        long left = GATHER_TICKS - (now - gatherStartedAt);
        if (left > 0) {
            onCircle.forEach(p -> Gamemaster.bar(p, "The game begins in " + (left / 20 + 1) + "... (" + Math.min(onCircle.size(), MAX_GROUP) + " playing)"));
            return;
        }
        List<ServerPlayer> group = onCircle.subList(0, Math.min(MAX_GROUP, onCircle.size()));
        LOGGER.info("Arcade run starts for {}", group.stream().map(p -> p.getName().getString()).toList());
        runs.add(new Run(server, level(), this, new ArrayList<>(group), 1, 0, false));
        gatherStartedAt = -1;
    }

    private void leave(ServerPlayer player) {
        long now = server.getTickCount();
        Long since = leavingAt.putIfAbsent(player.getUUID(), now);
        if (since != null && now - since > 40) {
            leavingAt.remove(player.getUUID());
            String home = origins.getOrDefault(player.getUUID(), "vanilla");
            Gamemaster.say(player, "Leaving so soon? Come back any time you find my door.");
            if (!Transfers.send(player, home, "", "arcade exit")) {
                LOGGER.error("ALERT: the arcade exit transfer for {} was not queued", player.getName().getString());
            }
        }
    }

    private void toHall(ServerPlayer player) {
        player.getInventory().clearContent();
        player.teleportTo(level(), HALL.getX() + 0.5, HALL.getY() + 1, HALL.getZ() + 0.5, Set.of(), 0F, 0F, false);
        player.setHealth(player.getMaxHealth());
    }

    @Override
    public void cashOut(ServerPlayer player, int prizeTier) {
        String home = origins.getOrDefault(player.getUUID(), "vanilla");
        toHall(player);
        if (prizeTier <= 0) {
            return;
        }
        db.addPrize(player.getUUID(), home, prizeTier, "run").whenComplete((ok, failure) -> server.execute(() -> {
            if (failure != null) {
                LOGGER.error("ALERT: could not record {}'s tier {} prize", player.getName().getString(), prizeTier, failure);
                player.sendSystemMessage(Component.literal("Something went wrong recording your prize. Tell an admin.").withStyle(ChatFormatting.RED));
                return;
            }
            Gamemaster.say(player, "Tier " + prizeTier + "! Your prize waits for you at home, in " + home + ".");
            LOGGER.info("{} cashed out at tier {} (prize for {})", player.getName().getString(), prizeTier, home);
        }));
    }

    @Override
    public void lost(ServerPlayer player, int tier) {
        toHall(player);
        Gamemaster.say(player, "The house wins this time. You reached tier " + tier + ". The circle is right there.");
    }

    @Override
    public void suspended(UUID player, int tier, int prizeTier) {
        String home = origins.getOrDefault(player, "vanilla");
        db.suspend(player, tier, prizeTier, home).whenComplete((ok, failure) -> {
            if (failure != null) {
                LOGGER.error("ALERT: could not keep the run of {} (tier {})", player, tier, failure);
            }
        });
    }
}
