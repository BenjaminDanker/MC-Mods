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
    private static final long RESUME_SECONDS = 15 * 60;
    private static final int GATHER_TICKS = 5 * 20;
    private static final int MAX_GROUP = 4;
    private static final BlockPos HALL = new BlockPos(0, 100, 0);
    private static final BlockPos START = new BlockPos(0, 100, 8);
    private static final BlockPos EXIT = new BlockPos(0, 100, -8);
    private HallDoors doors;
    /** View distance in the hall (chunks): the whole nave (~130 blocks from the arrival) and the colossus at its end. */
    private static final int HALL_VIEW = 9;

    private final MinecraftServer server;
    private final ArcadeConfig config;
    private final ArcadeDb db;
    private final List<Run> runs = new ArrayList<>();
    private final Map<UUID, String> origins = new HashMap<>();
    private final Map<UUID, Long> leavingAt = new HashMap<>();
    private long gatherStartedAt = -1;
    private GamemasterNpc gamemaster;
    private Previews previews;
    /** Players whose welcome waits until their intro scene has played (tick it is due). */
    private final Map<UUID, Long> welcomeAt = new HashMap<>();

    public RealmRole(MinecraftServer server, ArcadeConfig config, ArcadeDb db) {
        this.server = server;
        this.config = config;
        this.db = db;
    }

    public void register() {
        RoomCatalog.registerAll();
        Arena.clearLeftovers(level(), server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("arcade-arenas"));
        ensureHall();
        previews = new Previews(level());
        doors = new HallDoors(server, config, this::startRun);
        doors.register();
        gamemaster = new GamemasterNpc(level());
        gamemaster.register();
        // The whole hall stays loaded (about 40 chunks): its signs, Gamemaster and doors must exist before anyone walks near.
        for (int cx = -40 >> 4; cx <= 40 >> 4; cx++) {
            for (int cz = -20 >> 4; cz <= 135 >> 4; cz++) {
                level().setChunkForced(cx, cz, true);
            }
        }
        gamemaster.ensure();
        ServerTickEvents.END_SERVER_TICK.register(ticking -> tick());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, left) -> onLeave(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            Arena.stopping = true;
            runs.forEach(Run::abandon);
        });
        // Monsters a room spawned never outlive it: any found when a chunk loads are leftovers.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.entityTags().contains("arcade_room") && runs.isEmpty()) {
                entity.discard();
            }
        });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            Run run = runOf(sender.getUUID());
            return run == null || !run.chat(sender, message.signedContent());
        });
        // Nobody is hurt in the hall; in a room the room decides (players hurt each other only in versus rooms).
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer victim)) {
                return true;
            }
            Run run = runOf(victim.getUUID());
            if (run == null) {
                return false;
            }
            if (source.getEntity() instanceof ServerPlayer attacker) {
                return run.pvpAllowed(attacker.getUUID(), victim.getUUID());
            }
            return true;
        });
        // A death in a room is never a real death: the room counts it as a loss for that player.
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) {
                return true;
            }
            Run run = runOf(player.getUUID());
            if (run != null) {
                run.died(player);
            } else {
                player.setHealth(player.getMaxHealth());
            }
            return false;
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
        com.silver.arcade.selftest.SelfTest.install(server, "realm", this, config, db); // self-test hook: inert unless config/arcade-selftest.json exists
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
        ViewDistance.set(player, HALL_VIEW); // the hall and the rooms need no extended view
        Run waiting = runOf(player.getUUID());
        if (waiting != null && waiting.isAway(player.getUUID())) {
            // Back within the grace period: straight back into the paused room, kit and all.
            waiting.returned(player);
            return;
        }
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
                for (Run run : runs) {
                    if (run.canRejoin(id)) {
                        run.rejoinLate(online);
                        return;
                    }
                }
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

    /** The first visit plays the intro scene (Villager-Interface's cutscenes, called by name), then the welcome. */
    private int playIntro(ServerPlayer player) {
        try {
            Class<?> scenes = Class.forName("com.silver.villagerinterface.cutscene.Cutscenes");
            return (int) scenes.getMethod("play", ServerPlayer.class, String.class, double.class, double.class, double.class, float.class)
                .invoke(null, player, "arcade_intro", HALL.getX() + 0.5, HALL.getY() + 1.0, HALL.getZ() + 0.5, 0F);
        } catch (ReflectiveOperationException notHere) {
            return 0;
        }
    }

    private void greet(ServerPlayer player) {
        if (!player.entityTags().contains("arcade_met")) {
            int scene = playIntro(player);
            if (scene > 0) {
                welcomeAt.put(player.getUUID(), server.getTickCount() + (long) scene + 10);
                return;
            }
        }
        welcome(player);
    }

    private void welcome(ServerPlayer player) {
        if (player.addTag("arcade_met")) {
            Gamemaster.title(player, "The Arcade", "Every game is fair. Mostly.");
            Gamemaster.say(player, "A visitor! Welcome to my arcade. Walk through any of my doors to play: rooms get harder as you climb, "
                + "and after each one you may leave with a prize for your own world, or climb on and risk it. Bring friends if you like; "
                + "they share the climb, though not always the room. The crying obsidian takes you home.");
        } else {
            Gamemaster.say(player, "Back again? The doors are waiting.");
        }
    }

    private void onLeave(UUID id) {
        Run run = runOf(id);
        if (run != null) {
            run.left(id);
        }
        leavingAt.remove(id);
        welcomeAt.remove(id);
    }

    private void tick() {
        long tickNow = server.getTickCount();
        if (gamemaster != null) {
            gamemaster.tick(tickNow);
        }
        previews.tick(tickNow);
        if (!welcomeAt.isEmpty()) {
            welcomeAt.entrySet().removeIf(entry -> {
                if (tickNow < entry.getValue()) {
                    return false;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    welcome(player);
                }
                return true;
            });
        }
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
        List<ServerPlayer> free = new ArrayList<>();
        for (ServerPlayer player : level().players()) {
            if (runOf(player.getUUID()) != null || player.isSpectator()) {
                continue;
            }
            BlockPos below = player.blockPosition().below();
            if (near(below, EXIT)) {
                leave(player);
            } else {
                free.add(player);
            }
        }
        if (config.hall.doors.isEmpty()) {
            gather(free.stream().filter(p -> near(p.blockPosition().below(), START)).toList());
        } else {
            doors.tick(server.getTickCount(), free);
        }
    }

    private static boolean near(BlockPos pos, BlockPos centre) {
        int dx = pos.getX() - centre.getX();
        int dz = pos.getZ() - centre.getZ();
        return pos.getY() == centre.getY() && dx * dx + dz * dz <= 5;
    }

    /** Starts a climb for a group (together = a Grand Game: everyone always in the same room). */
    private void startRun(List<ServerPlayer> group, boolean together) {
        LOGGER.info("Arcade {} starts for {}", together ? "Grand Game" : "run", group.stream().map(p -> p.getName().getString()).toList());
        runs.add(new Run(server, level(), this, new ArrayList<>(group), 1, 0, false, together));
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
            onCircle.forEach(p -> Gamemaster.bar(p, "The game begins in " + ((left + 19) / 20) + "... (" + Math.min(onCircle.size(), MAX_GROUP) + " playing)"));
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
            Gamemaster.say(player, "Leaving so soon? Roll my die whenever you want to come back.");
            if (!Transfers.send(player, home, "", "arcade exit")) {
                LOGGER.error("ALERT: the arcade exit transfer for {} was not queued", player.getName().getString());
            }
        }
    }

    private void toHall(ServerPlayer player) {
        NameTags.set(player, false);
        ViewDistance.set(player, HALL_VIEW);
        player.getInventory().clearContent();
        player.setGameMode(GameType.ADVENTURE);
        player.removeAllEffects();
        player.teleportTo(level(), HALL.getX() + 0.5, HALL.getY() + 1, HALL.getZ() + 0.5, Set.of(), 0F, 0F, false);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
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
        Gamemaster.say(player, "The house wins this time. You reached tier " + tier + ". The doors are right there.");
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
