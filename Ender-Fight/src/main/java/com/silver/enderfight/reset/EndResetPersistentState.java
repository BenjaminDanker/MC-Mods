package com.silver.enderfight.reset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.silver.enderfight.EnderFightMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * World-scoped storage for End reset metadata. The state is kept outside the vanilla persistent state
 * system so the mod remains compatible with runtime changes to that API.
 */
public final class EndResetPersistentState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "data/enderfight-end-reset.json";

    private long lastResetEpochMillis;
    private long currentEndSeed;
    private String activeDimensionId;
    private final Map<String, String> offlineEndPlayers;

    private EndResetPersistentState(long lastResetEpochMillis, long currentEndSeed, String activeDimensionId, Map<String, String> offlineEndPlayers) {
        this.lastResetEpochMillis = lastResetEpochMillis;
        this.currentEndSeed = currentEndSeed;
        this.activeDimensionId = activeDimensionId == null ? World.END.getValue().toString() : activeDimensionId;
        this.offlineEndPlayers = offlineEndPlayers == null ? new HashMap<>() : new HashMap<>(offlineEndPlayers);
    }

    public EndResetPersistentState() {
        this(0L, ThreadLocalRandom.current().nextLong(), World.END.getValue().toString(), new HashMap<>());
    }

    public long getLastResetEpochMillis() {
        return lastResetEpochMillis;
    }

    public long getCurrentEndSeed() {
        return currentEndSeed;
    }

    public RegistryKey<World> getActiveDimensionKey() {
        Identifier id = Identifier.tryParse(activeDimensionId);
        return id == null ? World.END : RegistryKey.of(RegistryKeys.WORLD, id);
    }

    public void updateOnReset(long epochMillis, long newSeed, RegistryKey<World> dimensionKey) {
        this.lastResetEpochMillis = epochMillis;
        this.currentEndSeed = newSeed;
        this.activeDimensionId = dimensionKey.getValue().toString();
    }

    public void recordPlayerLoggedOutInEnd(UUID playerId, RegistryKey<World> dimensionKey) {
        if (playerId == null || dimensionKey == null) {
            return;
        }
        offlineEndPlayers.put(playerId.toString(), dimensionKey.getValue().toString());
    }

    public RegistryKey<World> getRecordedEndDimension(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        String value = offlineEndPlayers.get(playerId.toString());
        if (value == null) {
            return null;
        }
        Identifier id = Identifier.tryParse(value);
        return id == null ? null : RegistryKey.of(RegistryKeys.WORLD, id);
    }

    public boolean clearRecordedPlayer(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return offlineEndPlayers.remove(playerId.toString()) != null;
    }

    public Map<String, String> getOfflineEndPlayers() {
        return Collections.unmodifiableMap(offlineEndPlayers);
    }

    public void save(MinecraftServer server) {
        Path path = resolveStatePath(server);
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                GSON.toJson(new SerializableState(lastResetEpochMillis, currentEndSeed, activeDimensionId, offlineEndPlayers), writer);
            }
        } catch (IOException ex) {
            EnderFightMod.LOGGER.error("Failed to persist End reset state to {}", path, ex);
        }
    }

    public static EndResetPersistentState load(MinecraftServer server) {
        Path path = resolveStatePath(server);
        if (!Files.exists(path)) {
            return new EndResetPersistentState();
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            SerializableState saved = GSON.fromJson(reader, SerializableState.class);
            if (saved == null) {
                return new EndResetPersistentState();
            }
            return new EndResetPersistentState(saved.lastResetEpochMillis, saved.currentEndSeed, saved.activeDimensionId, saved.offlineEndPlayers);
        } catch (IOException | JsonParseException ex) {
            EnderFightMod.LOGGER.warn("Unable to read End reset state from {}; using defaults", path, ex);
            return new EndResetPersistentState();
        }
    }

    private static Path resolveStatePath(MinecraftServer server) {
        Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
        return worldRoot.resolve(STORAGE_FILE);
    }

    private record SerializableState(long lastResetEpochMillis, long currentEndSeed, String activeDimensionId, Map<String, String> offlineEndPlayers) {
    }
}

