package com.silver.structurereset;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds tracked structures when players reach them, snapshots each (blocks, block entities, mobs) as vanilla
 * structure templates split by chunk column, notices when one is beaten, and a day later ejects anyone still
 * inside, clears leftover mobs and pastes the snapshot back. All work runs on the server thread, one chunk
 * column per tick.
 */
final class ResetManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("structure-reset");
    private static final int WAIT_LIMIT_TICKS = 1200;
    /** Snapshot columns are read this many columns ahead of the one being placed. */
    private static final int READ_AHEAD = 4;
    /** A snapshot column is a chunk-sized template; far below this. Guards against a corrupt or huge file. */
    private static final long MAX_COLUMN_BYTES = 256L * 1024 * 1024;
    /** Snapshot files are read and written here, so a tick never waits for the disk. */
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "structure-reset-io");
        thread.setDaemon(true);
        return thread;
    });
    /** Update clients, skip neighbour reactions. */
    private static final int PLACE_FLAGS = 2 | 16;

    private final MinecraftServer server;
    private final ResetConfig config;
    private final StructureFilter filter;
    private final Path root;
    private final Path indexFile;
    private final Index index;
    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private final Set<String> rejected = new HashSet<>();
    private long tickCount;
    private boolean dirty;

    ResetManager(MinecraftServer server, ResetConfig config) {
        this.server = server;
        this.config = config;
        this.filter = new StructureFilter(config.structures);
        this.root = server.getWorldPath(LevelResource.ROOT).resolve("structure-reset");
        this.indexFile = root.resolve("index.json");
        this.index = Index.load(indexFile);
        releaseStaleForcedChunks();
        // A snapshot interrupted by a restart is incomplete: forget it so the next visit takes a fresh one.
        index.instances.values().removeIf(instance -> instance.state == Instance.State.SNAPSHOTTING);
        dirty = true;
        LOGGER.info("Structure Reset tracking {} pattern(s); {} instance(s) known", filter.patterns().size(), index.instances.size());
    }

    // ------------------------------------------------------------------ ticking

    void tick() {
        tickCount++;
        Job job = jobs.peek();
        if (job != null) {
            try {
                if (job.step()) {
                    jobs.poll();
                    queued.remove(job.instance.key);
                    dirty = true;
                }
            } catch (RuntimeException failure) {
                LOGGER.error("ALERT: {} failed for {}", job.name(), job.instance.key, failure);
                job.release();
                jobs.poll();
                queued.remove(job.instance.key);
                if (job instanceof SnapshotJob) {
                    index.instances.remove(job.instance.key);
                }
                dirty = true;
            }
        }
        if (tickCount % Math.max(1, config.scanTicks) == 0) {
            scan();
        }
        if (tickCount % 100 == 0) {
            checkLoot();
        }
        if (tickCount % 20 == 0) {
            queueDueResets();
        }
        if (dirty && tickCount % 200 == 0) {
            save();
        }
    }

    void save() {
        index.save(indexFile);
        dirty = false;
    }

    // ------------------------------------------------------------------ discovery

    private void scan() {
        if (filter.patterns().isEmpty()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                discoverAt(level, player.blockPosition());
            }
        }
    }

    /** Starts tracking any tracked structure that has a piece at this position. Returns the instance, if any. */
    Instance discoverAt(ServerLevel level, BlockPos pos) {
        StructureManager structures = level.structureManager();
        Instance found = null;
        for (Structure structure : structures.getAllStructuresAt(pos).keySet()) {
            Identifier id = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure);
            if (id == null || !filter.matches(id.toString())) {
                continue;
            }
            StructureStart start = structures.getStructureWithPieceAt(pos, structure);
            if (start == null || !start.isValid()) {
                continue;
            }
            ChunkPos origin = start.getChunkPos();
            String key = dimensionId(level) + "|" + id + "|" + origin.x() + "," + origin.z();
            Instance known = index.instances.get(key);
            if (known != null) {
                found = known;
                continue;
            }
            BoundingBox box = start.getBoundingBox();
            Instance instance = new Instance();
            instance.key = key;
            instance.dimension = dimensionId(level);
            instance.structure = id.toString();
            // Pieces do not always cover everything built around them (a pyramid's trap room lies below it).
            instance.minX = box.minX() - config.paddingHorizontal;
            instance.minY = Math.max(level.getMinY(), box.minY() - config.paddingDown);
            instance.minZ = box.minZ() - config.paddingHorizontal;
            instance.maxX = box.maxX() + config.paddingHorizontal;
            instance.maxY = Math.min(level.getMaxY(), box.maxY() + config.paddingUp);
            instance.maxZ = box.maxZ() + config.paddingHorizontal;
            instance.discoveredAt = System.currentTimeMillis();
            if (instance.zone().volume() > config.maxVolume) {
                if (rejected.add(key)) {
                    LOGGER.info("Not tracking {}: {} blocks is over maxVolume", key, instance.zone().volume());
                }
                continue;
            }
            index.instances.put(key, instance);
            enqueue(new SnapshotJob(instance));
            LOGGER.info("Tracking {} ({} blocks); taking its snapshot", key, instance.zone().volume());
            found = instance;
        }
        return found;
    }

    Instance containing(ServerLevel level, BlockPos pos) {
        String dimension = dimensionId(level);
        for (Instance instance : index.instances.values()) {
            if (instance.dimension.equals(dimension) && instance.zone().contains(pos.getX(), pos.getY(), pos.getZ())) {
                return instance;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ beaten

    void onDeath(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || index.instances.isEmpty()) {
            return;
        }
        Instance instance = containing(level, entity.blockPosition());
        if (instance == null || instance.state != Instance.State.READY || !isBoss(entity)) {
            return;
        }
        beat(instance, "defeated " + entity.getType().builtInRegistryHolder().key().identifier());
    }

    private boolean isBoss(LivingEntity entity) {
        String type = entity.getType().builtInRegistryHolder().key().identifier().toString();
        if (config.bossTypes.contains(type)) {
            return true;
        }
        for (String tag : config.bossTags) {
            if (entity.entityTags().contains(tag)) {
                return true;
            }
        }
        return entity.getMaxHealth() >= config.bossMinHealth;
    }

    private void checkLoot() {
        for (Instance instance : index.instances.values()) {
            if (instance.state != Instance.State.READY || instance.lootPositions.isEmpty()) {
                continue;
            }
            ServerLevel level = levelOf(instance);
            if (level == null) {
                continue;
            }
            for (long packed : instance.lootPositions) {
                if (instance.opened.contains(packed)) {
                    continue;
                }
                BlockPos pos = BlockPos.of(packed);
                if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
                    continue;
                }
                BlockEntity entity = level.getBlockEntity(pos);
                boolean untouched = entity instanceof RandomizableContainer container && container.getLootTable() != null;
                if (!untouched && instance.markOpened(packed)) {
                    beat(instance, "every loot container was opened");
                    break;
                }
                dirty |= !untouched;
            }
        }
    }

    void beat(Instance instance, String why) {
        instance.markBeaten(System.currentTimeMillis());
        dirty = true;
        LOGGER.info("{} is beaten ({}); it resets in {} hour(s)", instance.key, why, config.intervalHours);
    }

    private void queueDueResets() {
        long now = System.currentTimeMillis();
        for (Instance instance : index.instances.values()) {
            if (instance.dueForReset(now, config.intervalMillis()) && !queued.contains(instance.key)) {
                enqueue(new ResetJob(instance));
            }
        }
    }

    /** Staff command: make it due right now. */
    boolean forceReset(Instance instance) {
        if (instance.state == Instance.State.SNAPSHOTTING || queued.contains(instance.key)) {
            return false;
        }
        instance.markBeaten(0);
        dirty = true;
        return true;
    }

    boolean forget(Instance instance) {
        if (queued.contains(instance.key)) {
            return false;
        }
        index.instances.remove(instance.key);
        deleteFolder(root.resolve(instance.folder()));
        dirty = true;
        return true;
    }

    String status() {
        int ready = 0;
        int beaten = 0;
        for (Instance instance : index.instances.values()) {
            if (instance.state == Instance.State.READY) {
                ready++;
            } else if (instance.state == Instance.State.BEATEN) {
                beaten++;
            }
        }
        return index.instances.size() + " tracked (" + ready + " intact, " + beaten + " beaten); " + jobs.size() + " job(s) queued";
    }

    String describe(Instance instance) {
        long hoursLeft = instance.state == Instance.State.BEATEN
            ? Math.max(0, (instance.beatenAt + config.intervalMillis() - System.currentTimeMillis()) / 3_600_000L) : -1;
        return instance.structure + " " + instance.state + " (resets so far " + instance.resets
            + ", loot opened " + instance.opened.size() + "/" + instance.lootPositions.size()
            + (hoursLeft >= 0 ? ", restores in ~" + hoursLeft + "h" : "") + ")";
    }

    // ------------------------------------------------------------------ jobs

    private void enqueue(Job job) {
        if (queued.add(job.instance.key)) {
            jobs.add(job);
            dirty = true;
        }
    }

    private abstract class Job {
        final Instance instance;
        final ServerLevel level;
        final Zone zone;
        final List<Zone.Slice> slices;
        final Path folder;
        final List<ChunkPos> forcedHere = new ArrayList<>();
        int next;
        int waited;
        boolean prepared;

        Job(Instance instance) {
            this.instance = instance;
            this.level = levelOf(instance);
            this.zone = instance.zone();
            this.slices = zone.slices();
            this.folder = root.resolve(instance.folder());
            if (level == null) {
                throw new IllegalStateException("dimension " + instance.dimension + " is not loaded");
            }
        }

        abstract String name();

        abstract boolean step();

        /** Forces every chunk column of the zone so it loads and stays loaded for the job. */
        void prepare() {
            for (Zone.Slice slice : slices) {
                if (level.setChunkForced(slice.chunkX(), slice.chunkZ(), true)) {
                    forcedHere.add(new ChunkPos(slice.chunkX(), slice.chunkZ()));
                    index.forced.add(dimensionId(level) + "|" + slice.chunkX() + "|" + slice.chunkZ());
                }
            }
            prepared = true;
            dirty = true;
        }

        void release() {
            for (ChunkPos pos : forcedHere) {
                level.setChunkForced(pos.x(), pos.z(), false);
                index.forced.remove(dimensionId(level) + "|" + pos.x() + "|" + pos.z());
            }
            forcedHere.clear();
            dirty = true;
        }

        LevelChunk chunkOf(Zone.Slice slice) {
            return level.getChunkSource().getChunkNow(slice.chunkX(), slice.chunkZ());
        }

        boolean timedOut() {
            if (++waited > WAIT_LIMIT_TICKS) {
                throw new IllegalStateException("chunks did not load in time");
            }
            return false;
        }

        Path file(Zone.Slice slice) {
            return folder.resolve(slice.chunkX() + "_" + slice.chunkZ() + ".nbt");
        }

        BlockPos origin(Zone.Slice slice) {
            return new BlockPos(slice.x(), zone.minY(), slice.z());
        }
    }

    private final class SnapshotJob extends Job {
        SnapshotJob(Instance instance) {
            super(instance);
        }

        @Override
        String name() {
            return "snapshot";
        }

        private final List<CompletableFuture<Void>> writes = new ArrayList<>();

        @Override
        boolean step() {
            if (!prepared) {
                prepare();
                try {
                    Files.createDirectories(folder);
                } catch (IOException failure) {
                    throw new IllegalStateException("cannot create " + folder, failure);
                }
                return false;
            }
            if (next >= slices.size()) {
                return finishWhenWritten();
            }
            Zone.Slice slice = slices.get(next);
            LevelChunk chunk = chunkOf(slice);
            if (chunk == null) {
                return timedOut();
            }
            waited = 0;
            StructureTemplate template = new StructureTemplate();
            template.fillFromWorld(level, origin(slice), new Vec3i(slice.sizeX(), zone.maxY() - zone.minY() + 1, slice.sizeZ()), true, List.of());
            // Capturing the blocks needs the server thread; compressing and writing the file does not.
            CompoundTag tag = template.save(new CompoundTag());
            Path target = file(slice);
            writes.add(CompletableFuture.runAsync(() -> {
                try {
                    NbtIo.writeCompressed(tag, target);
                } catch (IOException failure) {
                    throw new IllegalStateException("cannot write " + target, failure);
                }
            }, IO));
            for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                BlockPos pos = entry.getKey();
                if (zone.contains(pos.getX(), pos.getY(), pos.getZ())
                        && entry.getValue() instanceof RandomizableContainer container && container.getLootTable() != null) {
                    instance.lootPositions.add(pos.asLong());
                }
            }
            ++next;
            return false;
        }

        /** The snapshot counts as ready only once every column file is on disk; a failed write fails the job. */
        private boolean finishWhenWritten() {
            for (CompletableFuture<Void> write : writes) {
                if (!write.isDone()) {
                    return false;
                }
                write.join();
            }
            instance.state = Instance.State.READY;
            release();
            LOGGER.info("Snapshot of {} done: {} column(s), {} loot container(s)", instance.key, slices.size(), instance.lootPositions.size());
            return true;
        }
    }

    private final class ResetJob extends Job {
        private boolean cleared;
        private final Map<Integer, CompletableFuture<CompoundTag>> reads = new java.util.HashMap<>();

        /** Starts reading the next few column files in the background. */
        private void readAhead() {
            for (int i = next; i < Math.min(slices.size(), next + READ_AHEAD); i++) {
                Path source = file(slices.get(i));
                reads.computeIfAbsent(i, ignored -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return NbtIo.readCompressed(source, NbtAccounter.create(MAX_COLUMN_BYTES));
                    } catch (IOException failure) {
                        throw new java.io.UncheckedIOException(failure);
                    }
                }, IO));
            }
        }

        ResetJob(Instance instance) {
            super(instance);
        }

        @Override
        String name() {
            return "reset";
        }

        @Override
        boolean step() {
            if (!prepared) {
                prepare();
                return false;
            }
            if (!cleared) {
                for (Zone.Slice slice : slices) {
                    if (chunkOf(slice) == null) {
                        return timedOut();
                    }
                }
                ejectPlayers();
                clearMobs();
                cleared = true;
                waited = 0;
                readAhead();
                return false;
            }
            readAhead();
            CompletableFuture<CompoundTag> read = reads.get(next);
            if (!read.isDone()) {
                return timedOut();
            }
            waited = 0;
            reads.remove(next);
            Zone.Slice slice = slices.get(next);
            try {
                CompoundTag tag = read.join();
                StructureTemplate template = new StructureTemplate();
                template.load(BuiltInRegistries.BLOCK, tag);
                template.placeInWorld(level, origin(slice), origin(slice), new StructurePlaceSettings().setIgnoreEntities(false),
                    level.getRandom(), PLACE_FLAGS);
            } catch (RuntimeException failure) { // includes a failed read (CompletionException)
                LOGGER.error("ALERT: snapshot column {} of {} is unreadable; leaving that column as it is", file(slice), instance.key, failure);
            }
            if (++next < slices.size()) {
                return false;
            }
            instance.markRestored();
            release();
            LOGGER.info("{} restored (reset #{})", instance.key, instance.resets);
            return true;
        }

        private void ejectPlayers() {
            int count = 0;
            for (ServerPlayer player : new ArrayList<>(level.players())) {
                BlockPos at = player.blockPosition();
                if (!zone.contains(at.getX(), at.getY(), at.getZ())) {
                    continue;
                }
                int[] out = zone.ejectPoint(at.getX(), at.getZ(), config.ejectPadding);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, out[0], out[1]);
                player.teleportTo(level, out[0] + 0.5, y, out[1] + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
                PetEscort.follow(player, level, out[0] + 0.5, y, out[1] + 0.5);
                player.sendSystemMessage(Component.literal("The ground shifts: this place is being restored.").withStyle(ChatFormatting.GRAY));
                count++;
            }
            if (count > 0) {
                LOGGER.info("Ejected {} player(s) from {}", count, instance.key);
            }
        }

        private void clearMobs() {
            AABB box = new AABB(zone.minX(), zone.minY(), zone.minZ(), zone.maxX() + 1, zone.maxY() + 1, zone.maxZ() + 1);
            int removed = 0;
            for (Entity entity : level.getEntitiesOfClass(Entity.class, box)) {
                boolean pet = entity instanceof TamableAnimal tamable && (tamable.isTame() || PetEscort.isPet(tamable));
                boolean clutter = entity instanceof Mob mob && !pet && !mob.isLeashed()
                    || entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof Projectile;
                if (clutter) {
                    entity.discard();
                    removed++;
                } else if (pet) {
                    // Strays inside the zone would end up inside the restored blocks.
                    int[] out = zone.ejectPoint(entity.getBlockX(), entity.getBlockZ(), config.ejectPadding);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, out[0], out[1]);
                    entity.teleportTo(level, out[0] + 0.5, y, out[1] + 0.5, Set.of(), entity.getYRot(), entity.getXRot(), false);
                }
            }
            LOGGER.info("Cleared {} leftover entities from {}", removed, instance.key);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String dimensionId(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    private ServerLevel levelOf(Instance instance) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(instance.dimension)));
    }

    private void releaseStaleForcedChunks() {
        for (String entry : index.forced) {
            String[] parts = entry.split("\\|");
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(parts[0])));
            if (level != null) {
                level.setChunkForced(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), false);
            }
        }
        index.forced.clear();
    }

    private static void deleteFolder(Path folder) {
        try (var walk = Files.walk(folder)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // nothing to delete
        }
    }
}
