package com.silver.atlantis.spawn.service;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.construct.OfflineChunkBlockReader;
import com.silver.atlantis.spawn.bounds.ActiveConstructBounds;
import com.silver.atlantis.spawn.bounds.ActiveConstructBoundsResolver;
import com.silver.atlantis.spawn.config.SpawnDifficultyConfig;
import com.silver.atlantis.spawn.config.SpawnMobConfig;
import com.silver.atlantis.spawn.marker.AtlantisMobMarkerState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Domain service for Atlantis proximity spawn marker generation and clearing.
 */
public final class ProximitySpawnService {

    private static final int OFFLINE_CHUNK_CACHE_LIMIT = 4096;
    private static final int DISCOVERY_OPS_MULTIPLIER = 12;
    private static final StructureMobMarkerDiscoveryService MARKER_DISCOVERY = new StructureMobMarkerDiscoveryService();
    private static final StructureMobMarkerSeedingService MARKER_SEEDING = new StructureMobMarkerSeedingService();
    private static final StructureMobCandidatePositionService CANDIDATE_POSITIONS = new StructureMobCandidatePositionService();
    private static final StructureMobMarkerSelectionPolicy MARKER_SELECTION = new StructureMobMarkerSelectionPolicy();

    private enum StructureMobPhase {
        DISCOVER_MARKERS,
        SEED_MARKERS,
        DONE
    }

    private static final class StructureMobJob {
        private final UUID requesterId;
        private final String requesterName;
        private final ServerWorld world;
        private final ActiveConstructBounds bounds;
        private final boolean dryRun;
        private final Random random;
        private final LongIterator interiorIterator;
        private final int easyY;

        private StructureMobPhase phase = StructureMobPhase.DISCOVER_MARKERS;

        private final List<SpawnMarker> discovered = new ArrayList<>();
        private List<SpawnMarker> selected = List.of();
        private int seedIndex;

        private int landMarkers;
        private int waterMarkers;
        private int bossMarkers;
        private int airMarkers;
        private final StructureMobMarkerDiscoveryService.AirSpawnStats airStats = new StructureMobMarkerDiscoveryService.AirSpawnStats();

        private long interiorScanned;
        private int rejectedOutOfBounds;
        private int rejectedFloorOutOfBounds;
        private int rejectedChunkNbtUnavailable;
        private int rejectedUnsupportedFloor;
        private int rejectedNoHeadroom;

        private final Map<Long, Optional<OfflineChunkBlockReader.ChunkSnapshot>> offlineChunkCache = new LinkedHashMap<>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Optional<OfflineChunkBlockReader.ChunkSnapshot>> eldest) {
                return size() > OFFLINE_CHUNK_CACHE_LIMIT;
            }
        };

        private int seeded;
        private int skipped;
        private int replaced;
        private int specialTagged;
        private int specialTotal;

        private StructureMobJob(
            UUID requesterId,
            String requesterName,
            ServerWorld world,
            ActiveConstructBounds bounds,
            boolean dryRun,
            Random random,
            LongIterator interiorIterator,
            int easyY
        ) {
            this.requesterId = requesterId;
            this.requesterName = requesterName;
            this.world = world;
            this.bounds = bounds;
            this.dryRun = dryRun;
            this.random = random;
            this.interiorIterator = interiorIterator;
            this.easyY = easyY;
        }
    }

    private static final Object JOB_LOCK = new Object();
    private static StructureMobJob activeJob;
    private static boolean tickRegistered;
    private static final ProximitySpawnService TICK_HELPER = new ProximitySpawnService(false);

    static record SpawnMarker(BlockPos pos, SpawnMobConfig.SpawnType spawnType) {
    }

    public ProximitySpawnService() {
        this(true);
    }

    private ProximitySpawnService(boolean registerHooks) {
        if (!registerHooks) {
            return;
        }
        registerTickOnce();
    }

    private static void registerTickOnce() {
        synchronized (JOB_LOCK) {
            if (tickRegistered) {
                return;
            }
            tickRegistered = true;
        }

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server == null) {
                return;
            }
            tickActiveJob(server);
        });
    }

    public boolean isStructureMobRunning() {
        synchronized (JOB_LOCK) {
            return activeJob != null;
        }
    }

    public int runStructureMob(ServerCommandSource source, boolean dryRun) {
        MinecraftServer server = source.getServer();

        ActiveConstructBounds activeBounds = ActiveConstructBoundsResolver.tryResolveLatest();
        if (activeBounds == null) {
            source.sendFeedback(() -> Text.literal("No active construct bounds found; /structuremob only seeds markers within the current cycle build. Run /construct first."), false);
            return 0;
        }

        ServerWorld world = resolveWorld(server, activeBounds.dimensionId());
        if (world == null) {
            source.sendFeedback(() -> Text.literal("No world loaded for active construct dimension."), false);
            return 0;
        }

        List<SpawnMarker> cachedMarkers = StructureMobMarkerCache.load(activeBounds);
        boolean cacheHit = cachedMarkers != null && !cachedMarkers.isEmpty();

        LongSet candidatePositions = null;
        if (!cacheHit) {
            candidatePositions = CANDIDATE_POSITIONS.loadCandidatePositions(activeBounds);
            if (candidatePositions == null || candidatePositions.isEmpty()) {
                source.sendFeedback(() -> Text.literal("No placed-block index found for active construct run; cannot seed structure mobs."), false);
                return 0;
            }
        }

        synchronized (JOB_LOCK) {
            if (activeJob != null) {
                source.sendFeedback(() -> Text.literal("structuremob is already running."), false);
                return 0;
            }

            UUID requesterId = source.getPlayer() != null ? source.getPlayer().getUuid() : null;
            String requesterName = source.getName();
            Random random = new Random(world.getRandom().nextLong());
            int easyY = resolveEasyY(activeBounds);

            LongIterator candidateIterator = cacheHit
                ? LongSets.EMPTY_SET.iterator()
                : candidatePositions.iterator();

            activeJob = new StructureMobJob(
                requesterId,
                requesterName,
                world,
                activeBounds,
                dryRun,
                random,
                candidateIterator,
                easyY
            );

            if (cacheHit) {
                activeJob.discovered.addAll(cachedMarkers);
                activeJob.selected = MARKER_SELECTION.selectMarkers(activeJob.discovered, random);
                activeJob.phase = activeJob.dryRun ? StructureMobPhase.DONE : StructureMobPhase.SEED_MARKERS;

                if (activeJob.dryRun) {
                    int land = 0;
                    int water = 0;
                    int air = 0;
                    int boss = 0;
                    for (SpawnMarker marker : activeJob.selected) {
                        switch (marker.spawnType()) {
                            case LAND -> land++;
                            case WATER -> water++;
                            case AIR -> air++;
                            case BOSS -> boss++;
                        }
                    }

                    String dryRunMessage = String.format(
                        Locale.ROOT,
                        "structuremob cache hit: would seed %d marker mob(s) (land=%d water=%d air=%d boss=%d).",
                        activeJob.selected.size(),
                        land,
                        water,
                        air,
                        boss
                    );

                    source.sendFeedback(() -> Text.literal(dryRunMessage), false);

                    activeJob = null;
                    return 1;
                }
            }
        }

        if (cacheHit) {
            source.sendFeedback(() -> Text.literal("structuremob started from cache (multi-tick seed only)."), false);
        } else {
            source.sendFeedback(() -> Text.literal("structuremob started (multi-tick scan + seed)."), false);
        }
        return 1;
    }

    private static void tickActiveJob(MinecraftServer server) {
        StructureMobJobRunner.tick(server);
    }

    private static final class StructureMobJobRunner {
        private StructureMobJobRunner() {
        }

        private static void tick(MinecraftServer server) {
            StructureMobJob job;
            synchronized (JOB_LOCK) {
                job = activeJob;
            }

            if (job == null) {
                return;
            }

            long deadline = System.nanoTime() + Math.max(1_000_000L, SpawnMobConfig.SPAWN_TICK_BUDGET_NANOS);
            int maxOps = Math.max(1, SpawnMobConfig.MAX_SPAWN_ATTEMPTS_PER_TICK);
            if (job.phase == StructureMobPhase.DISCOVER_MARKERS) {
                maxOps = Math.max(maxOps, SpawnMobConfig.MAX_SPAWN_ATTEMPTS_PER_TICK * DISCOVERY_OPS_MULTIPLIER);
            }
            int ops = 0;

            while (System.nanoTime() < deadline && ops < maxOps && job.phase != StructureMobPhase.DONE) {
                switch (job.phase) {
                    case DISCOVER_MARKERS -> {
                        if (handleDiscoverPhase(job, server)) {
                            break;
                        }
                        ops++;
                    }
                    case SEED_MARKERS -> {
                        if (handleSeedPhase(job, server)) {
                            break;
                        }
                        ops++;
                    }
                    case DONE -> {
                        break;
                    }
                }
            }

            if (job.phase == StructureMobPhase.DONE) {
                synchronized (JOB_LOCK) {
                    if (activeJob == job) {
                        activeJob = null;
                    }
                }
            }
        }

        private static boolean handleDiscoverPhase(StructureMobJob job, MinecraftServer server) {
            ProximitySpawnService service = TICK_HELPER;
            if (!job.interiorIterator.hasNext()) {
                AtlantisMod.LOGGER.info(
                    "[structuremob:{}] discovery summary: scannedInterior={} discovered={} rejected(outOfBounds={} floorOutOfBounds={} chunkNbtUnavailable={} unsupportedFloor={} noHeadroom={})",
                    job.requesterName,
                    job.interiorScanned,
                    job.discovered.size(),
                    job.rejectedOutOfBounds,
                    job.rejectedFloorOutOfBounds,
                    job.rejectedChunkNbtUnavailable,
                    job.rejectedUnsupportedFloor,
                    job.rejectedNoHeadroom
                );

                if (SpawnMobConfig.DIAGNOSTIC_LOGS) {
                    AtlantisMod.LOGGER.info(
                        "[SpawnDiag] markers land={} water={} boss={} air={} airAttempts={} airNoGlass={} airTooShort={} airNotFound={} minAirStretch={} maxAirPerRun= {}",
                        job.landMarkers,
                        job.waterMarkers,
                        job.bossMarkers,
                        job.airMarkers,
                        job.airStats.attempts,
                        job.airStats.noGlass,
                        job.airStats.tooShort,
                        job.airStats.notFound,
                        Math.max(2, SpawnMobConfig.MIN_AIR_STRETCH_TO_GLASS_BLOCKS),
                        SpawnMobConfig.MAX_AIR_PER_RUN
                    );
                }

                if (job.discovered.isEmpty()) {
                    service.send(job, server, String.format(
                        Locale.ROOT,
                        "No spawn markers found. scannedInterior=%d rejected(outOfBounds=%d floorOutOfBounds=%d chunkNbtUnavailable=%d unsupportedFloor=%d noHeadroom=%d)",
                        job.interiorScanned,
                        job.rejectedOutOfBounds,
                        job.rejectedFloorOutOfBounds,
                        job.rejectedChunkNbtUnavailable,
                        job.rejectedUnsupportedFloor,
                        job.rejectedNoHeadroom
                    ));
                    job.phase = StructureMobPhase.DONE;
                    return true;
                }

                StructureMobMarkerCache.save(job.bounds, job.discovered);

                job.selected = MARKER_SELECTION.selectMarkers(job.discovered, job.random);
                if (job.dryRun) {
                    int land = 0;
                    int water = 0;
                    int air = 0;
                    int boss = 0;
                    for (SpawnMarker marker : job.selected) {
                        switch (marker.spawnType()) {
                            case LAND -> land++;
                            case WATER -> water++;
                            case AIR -> air++;
                            case BOSS -> boss++;
                        }
                    }

                    service.send(job, server, String.format(
                        Locale.ROOT,
                        "Dry-run: would seed %d marker mob(s) (land=%d water=%d air=%d boss=%d).",
                        job.selected.size(),
                        land,
                        water,
                        air,
                        boss
                    ));
                    job.phase = StructureMobPhase.DONE;
                    return true;
                }

                job.phase = StructureMobPhase.SEED_MARKERS;
                return true;
            }

            long candidatePosKey = job.interiorIterator.nextLong();
            service.tryDiscoverMarker(job, candidatePosKey);
            if (SpawnMobConfig.DIAGNOSTIC_LOGS && job.interiorScanned > 0 && (job.interiorScanned % 50_000L) == 0L) {
                AtlantisMod.LOGGER.info(
                    "[structuremob:{}] discovery progress: scannedCandidates={} discovered={} rejected(chunkNbtUnavailable={} unsupportedFloor={} noHeadroom={})",
                    job.requesterName,
                    job.interiorScanned,
                    job.discovered.size(),
                    job.rejectedChunkNbtUnavailable,
                    job.rejectedUnsupportedFloor,
                    job.rejectedNoHeadroom
                );
            }
            return false;
        }

        private static boolean handleSeedPhase(StructureMobJob job, MinecraftServer server) {
            ProximitySpawnService service = TICK_HELPER;
            if (job.seedIndex >= job.selected.size()) {
                service.send(job, server, String.format(
                    Locale.ROOT,
                    "Seeded %d proximity marker mob(s), skipped=%d, replaced=%d, specialTagged=%d, specialItems=%d.",
                    job.seeded,
                    job.skipped,
                    job.replaced,
                    job.specialTagged,
                    job.specialTotal
                ));

                if (SpawnMobConfig.DIAGNOSTIC_LOGS) {
                    AtlantisMod.LOGGER.info(
                        "[SpawnDiag] seeded markers={} skipped={} replaced={} dim={} bounds=({},{},{})..({},{},{})",
                        job.seeded,
                        job.skipped,
                        job.replaced,
                        job.world.getRegistryKey().getValue(),
                        job.bounds.minX(), job.bounds.minY(), job.bounds.minZ(),
                        job.bounds.maxX(), job.bounds.maxY(), job.bounds.maxZ()
                    );
                }

                job.phase = StructureMobPhase.DONE;
                return true;
            }

            SpawnMarker marker = job.selected.get(job.seedIndex++);
            service.seedSingleMarker(job, marker);
            return false;
        }
    }

    public int checkSpawnPause(ServerCommandSource source) {
        Set<String> tokens = ProximityMobManager.getInstance().getActivePauseTokens();
        if (tokens.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No active spawn pause tokens."), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal(String.format(
            "Active spawn pause tokens (%d): %s",
            tokens.size(),
            String.join(", ", tokens)
        )), false);
        return tokens.size();
    }

    public int clearSpawnPause(ServerCommandSource source) {
        Set<String> tokens = ProximityMobManager.getInstance().getActivePauseTokens();
        if (tokens.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No active spawn pause tokens to clear."), false);
            return 0;
        }

        ProximityMobManager.getInstance().clearAllExternalPauses();
        source.sendFeedback(() -> Text.literal(String.format(
            "Cleared %d spawn pause token(s): %s",
            tokens.size(),
            String.join(", ", tokens)
        )), false);
        return tokens.size();
    }

    public int clearStructureMob(ServerCommandSource source) {
        MinecraftServer server = source.getServer();

        ActiveConstructBounds activeBounds = ActiveConstructBoundsResolver.tryResolveLatest();
        if (activeBounds == null) {
            source.sendFeedback(() -> Text.literal("No active construct bounds found; nothing to clear."), false);
            return 0;
        }

        ServerWorld world = resolveWorld(server, activeBounds.dimensionId());
        if (world == null) {
            source.sendFeedback(() -> Text.literal("No world loaded for active construct dimension; nothing to clear."), false);
            return 0;
        }

        AtlantisMobMarkerState state = AtlantisMobMarkerState.get(world);
        int beforeMarkers = state.getTotalMarkers();

        ProximityMobManager.getInstance().clearWithinBounds(world, activeBounds);

        int afterMarkers = state.getTotalMarkers();
        int removedMarkers = Math.max(0, beforeMarkers - afterMarkers);
        int activeAfter = ProximityMobManager.getInstance().countActive(world);

        source.sendFeedback(() -> Text.literal(String.format(
            Locale.ROOT,
            "Cleared Atlantis structuremob in bounds: removedMarkers=%d activeRemaining=%d.",
            removedMarkers,
            activeAfter
        )), false);

        return removedMarkers;
    }

    private void send(StructureMobJob job, MinecraftServer server, String message) {
        if (job == null || message == null) {
            return;
        }

        if (job.requesterId != null && server != null) {
            var player = server.getPlayerManager().getPlayer(job.requesterId);
            if (player != null) {
                player.sendMessage(Text.literal(message), false);
                return;
            }
        }

        AtlantisMod.LOGGER.info("[structuremob:{}] {}", job.requesterName, message);
    }

    private ServerWorld resolveWorld(MinecraftServer server, String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return server.getWorld(World.OVERWORLD);
        }

        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey().getValue().toString().equals(dimensionId)) {
                return world;
            }
        }
        return null;
    }

    private void tryDiscoverMarker(StructureMobJob job, long candidatePosKey) {
        if (job == null) {
            return;
        }

        job.interiorScanned++;

        StructureMobMarkerDiscoveryService.DiscoveryResult result = MARKER_DISCOVERY.discoverMarker(
            candidatePosKey,
            job.bounds,
            job.easyY,
            job.world,
            job.offlineChunkCache,
            job.airStats
        );

        switch (result.rejectReason()) {
            case NONE -> {
            }
            case OUT_OF_BOUNDS -> {
                job.rejectedOutOfBounds++;
                return;
            }
            case FLOOR_OUT_OF_BOUNDS -> {
                job.rejectedFloorOutOfBounds++;
                return;
            }
            case CHUNK_NBT_UNAVAILABLE -> {
                job.rejectedChunkNbtUnavailable++;
                return;
            }
            case UNSUPPORTED_FLOOR -> {
                job.rejectedUnsupportedFloor++;
                return;
            }
            case NO_HEADROOM -> {
                job.rejectedNoHeadroom++;
                return;
            }
        }

        for (SpawnMarker marker : result.markers()) {
            job.discovered.add(marker);
            switch (marker.spawnType()) {
                case LAND -> job.landMarkers++;
                case WATER -> job.waterMarkers++;
                case BOSS -> job.bossMarkers++;
                case AIR -> job.airMarkers++;
            }
        }
    }

    private void seedSingleMarker(StructureMobJob job, SpawnMarker marker) {
        if (job == null || marker == null) {
            return;
        }

        StructureMobMarkerSeedingService.SeedResult result = MARKER_SEEDING.seedMarker(job.world, job.bounds, job.random, marker);
        if (result.skipped()) {
            job.skipped++;
            return;
        }

        if (result.replaced()) {
            job.replaced++;
        }
        if (result.specialTagged()) {
            job.specialTagged++;
            job.specialTotal += result.specialAmount();
        }

        if (result.seeded()) {
            job.seeded++;
        }
    }

    private static int resolveEasyY(ActiveConstructBounds bounds) {
        int relativeEasy = SpawnDifficultyConfig.SCHEMATIC_EASIEST_Y_REL;
        int relativeMin = SpawnDifficultyConfig.SCHEMATIC_MIN_Y_REL;
        int relativeMax = SpawnDifficultyConfig.SCHEMATIC_MAX_Y_REL;

        int relativeClamped = Math.min(relativeMax, Math.max(relativeMin, relativeEasy));
        int offset = relativeClamped - relativeMin;
        int easyY = bounds.minY() + offset;
        if (easyY < bounds.minY()) {
            return bounds.minY();
        }
        if (easyY > bounds.maxY()) {
            return bounds.maxY();
        }
        return easyY;
    }
}
