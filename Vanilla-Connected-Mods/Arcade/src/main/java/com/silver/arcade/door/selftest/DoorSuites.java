package com.silver.arcade.door.selftest;

import com.silver.arcade.selftest.Bots;
import com.silver.arcade.selftest.Hygiene;
import com.silver.arcade.selftest.LogScan;
import com.silver.arcade.selftest.PacketLog;
import com.silver.arcade.selftest.Reflect;
import com.silver.arcade.selftest.SelfTest;
import com.silver.arcade.selftest.T;
import com.silver.arcade.selftest.TestDb;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The world-side suites (door role): doors, the Die, the altar, prizes on join; and the scripted NPC dialogues. */
public final class DoorSuites {
    private DoorSuites() {
    }

    @FunctionalInterface
    interface Body {
        void run(T t) throws Exception;
    }

    private static SelfTest.Suite suite(String name, Body body) {
        return new SelfTest.Suite() {
            public String name() {
                return name;
            }

            public String role() {
                return "door";
            }

            public void run(T t) throws Exception {
                body.run(t);
            }
        };
    }

    public static void register() {
        SelfTest.register(suite("world", WorldSuite::run));
        SelfTest.register(suite("dialogues", DialoguesSuite::run));
        Hygiene.probe("no arcade door entities left standing by the test", t -> {
            int n = t.on(() -> t.server.overworld().getEntities((Entity) null, new AABB(-30_000_000, -64, -30_000_000, 30_000_000, 400, 30_000_000),
                e -> e.entityTags().contains("arcade_door")).size());
            // a door the product opened for the test is closed by the product when its time is up; wait for it
            return n == 0 ? null : n + " door parts still standing (they close by themselves when their lifetime ends)";
        });
    }

    // helpers shared by the two suites

    static final String PREFIX = Bots.PREFIX;

    static Vec3 altarAt(T t) {
        Object altar = Reflect.get(com.silver.arcade.selftest.SelfTest.ENV.roleObject, "altar");
        return Reflect.get(altar, "at");
    }

    static List<Entity> near(T t, Vec3 at, double r, String tag) {
        return t.on(() -> t.server.overworld().getEntities((Entity) null, new AABB(at, at).inflate(r), e -> e.entityTags().contains(tag)));
    }

    static UUID uuid(T t, String name) {
        return t.on(() -> t.server.getPlayerList().getPlayerByName(name).getUUID());
    }

    static ServerPlayer p(T t, String name) {
        return t.server.getPlayerList().getPlayerByName(name);
    }

    static int rows(String table, UUID id) throws Exception {
        try (TestDb db = new TestDb()) {
            return db.count(table, id);
        }
    }

    static boolean logged(String text) {
        return !LogScan.grep(text).isEmpty();
    }

    /**
     * A standing spot near {@code around} from which the door code can find room for a door (open ground 6 to 14 blocks away),
     * so the door tests do not depend on what happens to be built next to the altar. Null when there is none in reach.
     */
    static Vec3 openSpot(T t, Vec3 around) {
        return t.on(() -> {
            var level = t.server.overworld();
            Vec3 best = null;
            int bestCount = 0;
            for (int dx = -48; dx <= 48; dx += 6) {
                for (int dz = -48; dz <= 48; dz += 6) {
                    int cx = (int) Math.floor(around.x) + dx;
                    int cz = (int) Math.floor(around.z) + dz;
                    if (!level.hasChunk(cx >> 4, cz >> 4)) {
                        continue;
                    }
                    int cy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
                    int count = 0;
                    for (int k = 0; k < 24; k++) {
                        double angle = Math.PI * 2 * k / 24;
                        double dist = 6 + 8.0 * ((k * 7) % 5) / 4.0;
                        int x = (int) Math.floor(cx + Math.cos(angle) * dist);
                        int z = (int) Math.floor(cz + Math.sin(angle) * dist);
                        if (!level.hasChunk(x >> 4, z >> 4)) {
                            continue;
                        }
                        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                        if (Math.abs(y - cy) <= 6 && Reflect.<Boolean>call(com.silver.arcade.door.DoorRole.class, "clear", level, x, y, z)) {
                            count++;
                        }
                    }
                    if (count > bestCount) {
                        bestCount = count;
                        best = new Vec3(cx + 0.5, cy, cz + 0.5);
                    }
                }
            }
            return bestCount >= 8 ? best : null;
        });
    }

    static String chat(String name, long mark) {
        return PacketLog.since(name, mark).stream().filter(r -> r.type().equals("ClientboundSystemChatPacket")).map(PacketLog.Rec::info).toList().toString();
    }

    static String describe(List<PacketLog.Rec> recs, String type) {
        return recs.stream().filter(r -> r.type().equals(type)).map(PacketLog.Rec::info).limit(5).toList().toString();
    }
}
