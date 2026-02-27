package com.silver.atlantis.spawn.service;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.protect.ProtectionEntry;
import com.silver.atlantis.protect.ProtectionFileIO;
import com.silver.atlantis.protect.ProtectionPaths;
import com.silver.atlantis.spawn.bounds.ActiveConstructBounds;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;

final class StructureMobCandidatePositionService {

    LongSet loadCandidatePositions(ActiveConstructBounds bounds) {
        if (bounds == null || bounds.runId() == null || bounds.runId().isBlank()) {
            return null;
        }

        Path protectionFile = ProtectionPaths.protectionFileForRun(bounds.runId());
        if (!Files.exists(protectionFile)) {
            return null;
        }

        try {
            ProtectionEntry entry = ProtectionFileIO.read(protectionFile);
            if (entry == null || entry.placedPositions() == null || entry.placedPositions().isEmpty()) {
                return null;
            }

            LongOpenHashSet candidates = new LongOpenHashSet(entry.placedPositions().size());
            LongIterator iterator = entry.placedPositions().iterator();
            while (iterator.hasNext()) {
                BlockPos pos = BlockPos.fromLong(iterator.nextLong());
                if (!bounds.contains(pos)) {
                    continue;
                }
                if ((pos.getY() + 2) > bounds.maxY()) {
                    continue;
                }

                candidates.add(pos.asLong());
            }

            AtlantisMod.LOGGER.info(
                "[SpawnDiag] candidate prefilter runId={} totalPlaced={} inBounds={}",
                bounds.runId(),
                entry.placedPositions().size(),
                candidates.size()
            );

            return candidates;
        } catch (Exception exception) {
            AtlantisMod.LOGGER.warn("[SpawnDiag] failed reading placed positions for runId={}: {}", bounds.runId(), exception.getMessage());
            return null;
        }
    }
}
