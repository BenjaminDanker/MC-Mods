package com.silver.atlantis.construct;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.RunContext;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.silver.atlantis.construct.undo.UndoCollector;
import com.silver.atlantis.construct.undo.UndoEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

final class WorldEditSchematicPaster {

    private WorldEditSchematicPaster() {
    }

    static Clipboard loadClipboard(Path schemFile) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(schemFile.toFile());
        if (format == null) {
            throw new IOException("Unknown schematic format for file: " + schemFile);
        }

        try (InputStream in = Files.newInputStream(schemFile);
             ClipboardReader reader = format.getReader(in)) {
            return reader.read();
        }
    }

    static PastePlacement computePlacement(Clipboard clipboard, BlockPos pasteToBlockPos) {
        Region region = clipboard.getRegion();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        // IMPORTANT:
        // Do NOT override clipboard origin here.
        // Many slice pipelines rely on the schematic's stored origin/offset so that
        // slice_000/slice_001/etc line up when pasted at the same anchor.
        BlockVector3 to = BlockVector3.at(pasteToBlockPos.getX(), pasteToBlockPos.getY(), pasteToBlockPos.getZ());
        BlockVector3 shift = to.subtract(clipboard.getOrigin());
        BlockVector3 worldMin = min.add(shift);
        BlockVector3 worldMax = max.add(shift);

        return new PastePlacement(to, new PasteRegion(worldMin, worldMax));
    }

    static PastePlacement computePlacement(Clipboard clipboard, BlockVector3 to) {
        Region region = clipboard.getRegion();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        BlockVector3 shift = to.subtract(clipboard.getOrigin());
        BlockVector3 worldMin = min.add(shift);
        BlockVector3 worldMax = max.add(shift);

        return new PastePlacement(to, new PasteRegion(worldMin, worldMax));
    }

    /**
     * Computes a paste anchor ('to') that will:
     * - center the clipboard's X/Z center on the given target center, and
     * - align the clipboard's ORIGIN Y to the target Y.
     *
     * This is computed ONCE from a reference clipboard (typically slice_000) and then
     * reused for all slices so they keep their relative offsets.
     */
    static BlockVector3 computeCenteredAnchorTo(Clipboard referenceClipboard, BlockPos targetCenter) {
        Region region = referenceClipboard.getRegion();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        // Region center in clipboard coordinates.
        int centerX = (int) Math.floor((min.x() + max.x()) / 2.0);
        int centerZ = (int) Math.floor((min.z() + max.z()) / 2.0);

        BlockVector3 origin = referenceClipboard.getOrigin();

        // World center = to + (center - origin) => to = worldCenter - (center - origin)
        int toX = targetCenter.getX() - (centerX - origin.x());
        int toZ = targetCenter.getZ() - (centerZ - origin.z());

        // Align ORIGIN Y: worldOriginY = toY + (originY - originY) = toY
        // This is what you want when the schematic has underground parts: the entrance/reference level
        // (origin) matches the /findflat Y, while anything below origin stays underground.
        int toY = targetCenter.getY();

        return BlockVector3.at(toX, toY, toZ);
    }

    static InProgressPaste beginPasteIgnoreAir(ServerWorld world, Clipboard clipboard, BlockVector3 to) {
        // WorldEdit-Fabric adapter.
        com.sk89q.worldedit.world.World weWorld = FabricAdapter.adapt(world);

        EditSession editSession = WorldEdit.getInstance()
            .newEditSessionBuilder()
            .world(weWorld)
            .build();

        // Reduce side effects / physics updates while pasting.
        // (Fluids will still settle later when chunks tick, but this avoids the worst
        // immediate cascades during the edit itself.)
        editSession.setFastMode(true);

        ClipboardHolder holder = new ClipboardHolder(clipboard);

        // Convert barriers to air during the paste so we don't need a second full-region pass.
        var extent = new BarrierToAirExtent(editSession);

        Operation paste = holder
            .createPaste(extent)
            .to(to)
            .ignoreAirBlocks(true)
            .build();

        return new InProgressPaste(editSession, paste, computePlacement(clipboard, to).region());
    }

    static PasteResult finishPaste(InProgressPaste inProgress) {
        List<UndoEntry> undoEntries = UndoCollector.collectPreviousBlocks(inProgress.editSession().getChangeSet());
        return new PasteResult(inProgress.region(), undoEntries);
    }

    static boolean resume(Operation op, RunContext runContext) throws Exception {
        // Backwards-compat shim (not used right now); kept for clarity.
        return op.resume(runContext) == null;
    }

    static ReplaceResult replaceBarriers(ServerWorld world, PasteRegion pastedRegion) throws Exception {
        com.sk89q.worldedit.world.World weWorld = FabricAdapter.adapt(world);

        try (EditSession editSession = WorldEdit.getInstance()
            .newEditSessionBuilder()
            .world(weWorld)
            .build()) {

            // Keep cleanup consistent with paste: avoid expensive side effects.
            editSession.setFastMode(true);

            CuboidRegion region = new CuboidRegion(weWorld, pastedRegion.worldMin(), pastedRegion.worldMax());
            Set<BaseBlock> from = Set.of(BlockTypes.BARRIER.getDefaultState().toBaseBlock());
            int replaced = editSession.replaceBlocks(region, from, BlockTypes.AIR.getDefaultState());
            List<UndoEntry> undoEntries = UndoCollector.collectPreviousBlocks(editSession.getChangeSet());
            return new ReplaceResult(replaced, undoEntries);
        }
    }

    record PasteResult(PasteRegion region, List<UndoEntry> undoEntries) {
    }

    record InProgressPaste(EditSession editSession, Operation operation, PasteRegion region) {
    }

    record ReplaceResult(int replacedCount, List<UndoEntry> undoEntries) {
    }

    record PasteRegion(BlockVector3 worldMin, BlockVector3 worldMax) {
    }

    record PastePlacement(BlockVector3 to, PasteRegion region) {
    }
}
