package com.silver.atlantis.spawn.service;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.spawn.bounds.ActiveConstructBounds;
import com.silver.atlantis.spawn.config.SpawnMobConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class StructureMobMarkerCache {

    /** 2: the header also stores a hash of the schematic the markers were found in. */
    private static final int VERSION = 2;
    private static final String CACHE_FILE = "structuremob_markers.bin";
    private static final SpawnMobConfig.SpawnType[] SPAWN_TYPES = SpawnMobConfig.SpawnType.values();

    private StructureMobMarkerCache() {
    }

    static List<ProximitySpawnService.SpawnMarker> load(ActiveConstructBounds bounds) {
        if (bounds == null || bounds.runId() == null || bounds.runId().isBlank()) {
            return List.of();
        }

        Path cacheFile = cacheFile(bounds);
        if (!Files.exists(cacheFile)) {
            return List.of();
        }

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(cacheFile)))) {
            int version = in.readInt();
            if (version != VERSION) {
                AtlantisMod.LOGGER.info("[SpawnDiag] structuremob cache version mismatch runId={} expected={} actual={}", bounds.runId(), VERSION, version);
                return List.of();
            }

 
            // The cache is reused across runs (positions are translated to the new build), so it must come from the
            // same schematic: a changed schematic would place markers in the wrong spots.
            in.readUTF(); // runId (not compared)
            in.readUTF(); // dimensionId (not compared)
            String cachedSchematic = in.readUTF();
            String currentSchematic = schematicHash();
            if (currentSchematic == null || !currentSchematic.equals(cachedSchematic)) {
                AtlantisMod.LOGGER.info("[SpawnDiag] structuremob cache is for a different schematic; rediscovering markers");
                return List.of();
            }

            // Read the bounds that the cache was originally generated for
            int cachedMinX = in.readInt();
            int cachedMinY = in.readInt();
            int cachedMinZ = in.readInt();
            int cachedMaxX = in.readInt();
            int cachedMaxY = in.readInt();
            int cachedMaxZ = in.readInt();
            if (cachedMaxX - cachedMinX != bounds.maxX() - bounds.minX()
                || cachedMaxY - cachedMinY != bounds.maxY() - bounds.minY()
                || cachedMaxZ - cachedMinZ != bounds.maxZ() - bounds.minZ()) {
                AtlantisMod.LOGGER.info("[SpawnDiag] structuremob cache bounds have a different size; rediscovering markers");
                return List.of();
            }

            int count = in.readInt();
            if (count <= 0) {
                return List.of();
            }

            // Calculate the offset between the current bounds and the cached bounds
            int offsetX = bounds.minX() - cachedMinX;
            int offsetY = bounds.minY() - cachedMinY;
            int offsetZ = bounds.minZ() - cachedMinZ;

            List<ProximitySpawnService.SpawnMarker> markers = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                long posKey = in.readLong();
                int ordinal = in.readByte() & 0xFF;
                if (ordinal >= SPAWN_TYPES.length) {
                    continue;
                }
                
                BlockPos cachedPos = BlockPos.of(posKey);
                // Apply the offset to translate the cached marker to the new build location
                BlockPos translatedPos = cachedPos.offset(offsetX, offsetY, offsetZ);
                
                markers.add(new ProximitySpawnService.SpawnMarker(translatedPos, SPAWN_TYPES[ordinal]));
            }

            AtlantisMod.LOGGER.info("[SpawnDiag] structuremob cache hit runId={} markers={} file={} (translated by x={}, y={}, z={})", 
                bounds.runId(), markers.size(), cacheFile, offsetX, offsetY, offsetZ);
            return markers;
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("[SpawnDiag] failed reading structuremob cache runId={}: {}", bounds.runId(), e.getMessage());
            return List.of();
        }
    }

    static void save(ActiveConstructBounds bounds, List<ProximitySpawnService.SpawnMarker> markers) {
        if (bounds == null || bounds.runId() == null || bounds.runId().isBlank() || markers == null || markers.isEmpty()) {
            return;
        }

        Path cacheFile = cacheFile(bounds);
        try {
            List<ProximitySpawnService.SpawnMarker> sanitized = new ArrayList<>(markers.size());
            for (ProximitySpawnService.SpawnMarker marker : markers) {
                if (marker == null || marker.pos() == null || marker.spawnType() == null) {
                    continue;
                }
                sanitized.add(marker);
            }
            if (sanitized.isEmpty()) {
                return;
            }
            String schematic = schematicHash();
            if (schematic == null) {
                AtlantisMod.LOGGER.warn("[SpawnDiag] schematic unreadable; not caching structuremob markers");
                return;
            }

            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(com.silver.atlantis.util.AtomicFiles.openReplacing(cacheFile)))) {
                out.writeInt(VERSION);
                out.writeUTF(bounds.runId() == null ? "" : bounds.runId());
                out.writeUTF(bounds.dimensionId() == null ? "" : bounds.dimensionId());
                out.writeUTF(schematic);
                out.writeInt(bounds.minX());
                out.writeInt(bounds.minY());
                out.writeInt(bounds.minZ());
                out.writeInt(bounds.maxX());
                out.writeInt(bounds.maxY());
                out.writeInt(bounds.maxZ());
                out.writeInt(sanitized.size());

                for (ProximitySpawnService.SpawnMarker marker : sanitized) {
                    out.writeLong(marker.pos().asLong());
                    out.writeByte(marker.spawnType().ordinal());
                }
            }

            AtlantisMod.LOGGER.info("[SpawnDiag] structuremob cache saved runId={} markers={} file={}", bounds.runId(), sanitized.size(), cacheFile);
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("[SpawnDiag] failed writing structuremob cache runId={}", bounds.runId(), e);
        }
    }

    /** SHA-256 of the Atlantis schematic file, or null when it can't be read. */
    private static String schematicHash() {
        try (var in = Files.newInputStream(com.silver.atlantis.construct.AtlantisSchematicPaths.schematicFile())) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            for (int read; (read = in.read(buffer)) > 0; ) {
                digest.update(buffer, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            return null;
        }
    }

    private static Path cacheFile(ActiveConstructBounds bounds) {
        Path atlantisConfigDir = FabricLoader.getInstance().getConfigDir().resolve("atlantis");
        return atlantisConfigDir.resolve(CACHE_FILE);
    }

}
