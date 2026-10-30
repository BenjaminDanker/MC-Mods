package com.silver.qaclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Chunk health: for every chunk within a radius of the player, whether the client has it and, per section, whether
 * it holds blocks and whether the renderer has a mesh for it. Written to the log as one "chunks" event; the
 * orchestrator compares it with the server's saved region data afterwards (a section the server has blocks in but the
 * client sees as air is a blank chunk; a section with blocks but no mesh was never drawn).
 * Section codes: '.' air only, 'B' blocks and a drawable mesh, 'E' blocks but the mesh is empty (nothing drawn),
 * 'U' blocks but not yet compiled, '?' chunk missing.
 */
public final class Chunks {
    private Chunks() {
    }

    public static void report(Bot b, String label, int radius) {
        String json = b.call(() -> build(b.mc, label, radius));
        Rec.event("chunks", "label", label, "data", new Rec.Raw(json));
    }

    private static String build(Minecraft mc, String label, int radius) {
        var level = mc.level;
        BlockPos pp = mc.player.blockPosition();
        int pcx = pp.getX() >> 4, pcz = pp.getZ() >> 4;
        StringBuilder sb = new StringBuilder("{\"dim\":").append(Rec.esc(level.dimension().identifier().toString()));
        sb.append(",\"player\":[").append(pp.getX()).append(',').append(pp.getY()).append(',').append(pp.getZ()).append(']');
        sb.append(",\"minSection\":").append(level.getMinSectionY()).append(",\"chunks\":[");
        boolean first = true;
        int missing = 0, blankSections = 0, noMesh = 0, blockSections = 0;
        var dispatcher = mc.levelRenderer.viewArea();
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                LevelChunk ch = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                StringBuilder codes = new StringBuilder();
                if (ch == null) {
                    missing++;
                    codes.append('?');
                } else {
                    LevelChunkSection[] sections = ch.getSections();
                    for (int i = 0; i < sections.length; i++) {
                        LevelChunkSection s = sections[i];
                        if (s == null || s.hasOnlyAir()) {
                            codes.append('.');
                            continue;
                        }
                        blockSections++;
                        char code = 'U';
                        try {
                            int sy = level.getMinSectionY() + i;
                            var rs = dispatcher == null ? null : dispatcher.getRenderSectionAt(new BlockPos(cx << 4, sy << 4, cz << 4));
                            if (rs != null) {
                                SectionMesh mesh = rs.getSectionMesh();
                                if (mesh == null || mesh == net.minecraft.client.renderer.chunk.CompiledSectionMesh.UNCOMPILED) {
                                    code = 'U';
                                } else if (mesh.hasRenderableLayers()) {
                                    code = 'B';
                                } else {
                                    code = 'E';
                                }
                            }
                        } catch (Throwable t) {
                            code = 'U';
                        }
                        if (code != 'B') {
                            noMesh++;
                        }
                        codes.append(code);
                    }
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append("{\"x\":").append(cx).append(",\"z\":").append(cz).append(",\"s\":").append(Rec.esc(codes.toString())).append('}');
            }
        }
        sb.append("],\"missing\":").append(missing).append(",\"blockSections\":").append(blockSections)
            .append(",\"notDrawn\":").append(noMesh).append('}');
        return sb.toString();
    }
}
