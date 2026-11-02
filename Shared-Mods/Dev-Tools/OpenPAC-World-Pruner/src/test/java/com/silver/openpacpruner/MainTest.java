package com.silver.openpacpruner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @Test void chunkToMcaRegionUsesFloorDivision() {
        assertEquals(new Main.Region(0, 0), Main.regionForChunk(0, 0));
        assertEquals(new Main.Region(0, 0), Main.regionForChunk(31, 31));
        assertEquals(new Main.Region(1, 1), Main.regionForChunk(32, 32));
        assertEquals(new Main.Region(-1, -1), Main.regionForChunk(-1, -1));
        assertEquals(new Main.Region(-1, -1), Main.regionForChunk(-32, -32));
        assertEquals(new Main.Region(-2, -2), Main.regionForChunk(-33, -33));
    }

    @Test void aClaimRetainsAllThreeMcaKindsInItsRegion(@TempDir Path world) throws Exception {
        writeClaims(world, "minecraft:overworld", List.of(new int[] { 31, 31 }, new int[] { 32, 32 }));
        for (String kind : List.of("region", "entities", "poi")) {
            touch(world.resolve(kind).resolve("r.0.0.mca"));
            touch(world.resolve(kind).resolve("r.1.1.mca"));
            touch(world.resolve(kind).resolve("r.2.2.mca"));
        }
        Main.Plan plan = Main.plan(world.toRealPath(), options());
        assertEquals(3, plan.candidates.size());
        assertTrue(plan.candidates.stream().allMatch(c -> c.path().getFileName().toString().equals("r.2.2.mca")));
    }

    @Test void customDimensionIsNormalizedLikeFilesystem(@TempDir Path world) throws Exception {
        writeClaims(world, "example:space/moon", List.of(new int[] { -33, -33 }));
        touch(world.resolve("dimensions/example/space/moon/region/r.-2.-2.mca"));
        touch(world.resolve("dimensions/example/space/moon/entities/r.-2.-2.mca"));
        touch(world.resolve("dimensions/example/space/moon/poi/r.0.0.mca"));
        Main.Plan plan = Main.plan(world.toRealPath(), options());
        assertEquals(1, plan.candidates.size());
        assertEquals("r.0.0.mca", plan.candidates.getFirst().path().getFileName().toString());
    }

    @Test void malformedClaimPositionFailsClosed(@TempDir Path world) throws Exception {
        writeMalformedClaims(world);
        touch(world.resolve("region/r.0.0.mca"));
        assertThrows(RuntimeException.class, () -> Main.plan(world.toRealPath(), options()));
    }

    @Test void emptyClaimsNeedExplicitOptInAndIgnoredDimensionsHaveNoCandidates(@TempDir Path world) throws Exception {
        writeClaims(world, "minecraft:the_end", List.of());
        touch(world.resolve("DIM1/region/r.0.0.mca"));
        assertThrows(RuntimeException.class, () -> Main.plan(world.toRealPath(), options()));
        Main.Options allowEmpty = new Main.Options(world, Set.of("minecraft:the_end"), true, true, List.of());
        assertTrue(Main.plan(world.toRealPath(), allowEmpty).candidates.isEmpty());
    }

    private static Main.Options options() { return new Main.Options(Path.of("."), Set.of(), false, true, List.of()); }
    private static void touch(Path path) throws IOException { Files.createDirectories(path.getParent()); Files.write(path, new byte[] { 0 }); }

    private static void writeClaims(Path world, String dimension, List<int[]> positions) throws IOException {
        Path file = world.resolve("data/openpartiesandclaims/player-claims/test.nbt"); Files.createDirectories(file.getParent());
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeByte(10); out.writeUTF(""); out.writeByte(10); out.writeUTF("dimensions"); out.writeByte(10); out.writeUTF(dimension);
            out.writeByte(9); out.writeUTF("claims"); out.writeByte(10); out.writeInt(positions.isEmpty() ? 0 : 1);
            if (!positions.isEmpty()) { out.writeByte(9); out.writeUTF("positions"); out.writeByte(10); out.writeInt(positions.size()); for (int[] p : positions) { out.writeByte(3); out.writeUTF("x"); out.writeInt(p[0]); out.writeByte(3); out.writeUTF("z"); out.writeInt(p[1]); out.writeByte(0); } out.writeByte(0); }
            out.writeByte(0); out.writeByte(0); out.writeByte(0);
        }
    }

    private static void writeMalformedClaims(Path world) throws IOException {
        Path file = world.resolve("data/openpartiesandclaims/player-claims/bad.nbt"); Files.createDirectories(file.getParent());
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeByte(10); out.writeUTF(""); out.writeByte(10); out.writeUTF("dimensions"); out.writeByte(10); out.writeUTF("minecraft:overworld"); out.writeByte(9); out.writeUTF("claims"); out.writeByte(10); out.writeInt(1); out.writeByte(9); out.writeUTF("positions"); out.writeByte(10); out.writeInt(1); out.writeByte(3); out.writeUTF("x"); out.writeInt(0); out.writeByte(0); out.writeByte(0); out.writeByte(0); out.writeByte(0);
        }
    }
}
