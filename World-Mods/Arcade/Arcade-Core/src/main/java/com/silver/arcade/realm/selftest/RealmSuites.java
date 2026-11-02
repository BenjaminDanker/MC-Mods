package com.silver.arcade.realm.selftest;

import com.silver.arcade.realm.Arena;
import com.silver.arcade.selftest.Hygiene;
import com.silver.arcade.selftest.Reflect;
import com.silver.arcade.selftest.SelfTest;
import com.silver.arcade.selftest.T;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/** The arcade server's suites. */
public final class RealmSuites {
    private RealmSuites() {
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
                return "realm";
            }

            public void run(T t) throws Exception {
                body.run(t);
            }
        };
    }

    public static void register() {
        SelfTest.register(suite("rooms", RoomsSuite::run));
        SelfTest.register(suite("runflow", RunflowSuite::run));
        SelfTest.register(suite("hall", HallSuite::run));
        SelfTest.register(suite("grand", GrandSuite::run));
        SelfTest.register(suite("disconnects", DisconnectsSuite::run));
        SelfTest.register(suite("cutscene", CutsceneSuite::run));
        Hygiene.probe("no arena slot is left taken", t -> {
            BitSet used = Reflect.getStatic(Arena.class, "USED");
            return used.isEmpty() ? null : "slots still taken: " + used;
        });
        Hygiene.probe("no ledger file is left in world/arcade-arenas", t -> {
            Path dir = t.server.getWorldPath(LevelResource.ROOT).resolve("arcade-arenas");
            try (var files = Files.isDirectory(dir) ? Files.list(dir) : java.util.stream.Stream.<Path>empty()) {
                var left = files.map(f -> f.getFileName().toString()).toList();
                return left.isEmpty() ? null : left.toString();
            } catch (java.io.IOException failure) {
                return failure.toString();
            }
        });
        Hygiene.probe("no room entities, arrows or drops are left in any arena slot", t -> {
            int n = t.on(() -> t.server.overworld().getEntities((Entity) null, new AABB(256, 0, -300, 512.0 * 40, 300, 300),
                e -> e.entityTags().contains("arcade_room") || e instanceof net.minecraft.world.entity.projectile.Projectile
                    || e instanceof net.minecraft.world.entity.item.ItemEntity).size());
            return n == 0 ? null : n + " entities";
        });
        Hygiene.probe("no climb is left running", t -> {
            int n = t.on(() -> Live.runs().size());
            return n == 0 ? null : n + " runs";
        });
        Hygiene.snapshot("the hall's blocks (x -45..45, y 98..125, z -30..110)", t -> t.on(() -> {
            long hash = 1125899906842597L;
            var level = t.server.overworld();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = -45; x <= 45; x++) {
                for (int y = 98; y <= 125; y++) {
                    for (int z = -30; z <= 110; z++) {
                        hash = 31 * hash + Block.getId(level.getBlockState(pos.set(x, y, z)));
                    }
                }
            }
            return Long.toHexString(hash);
        }));
        Hygiene.snapshot("entities in and around the hall (by type)", t -> t.on(() -> {
            TreeMap<String, Integer> counts = new TreeMap<>();
            for (Entity e : t.server.overworld().getEntities((Entity) null, new AABB(-120, 0, -120, 120, 320, 160),
                    x -> !(x instanceof net.minecraft.server.level.ServerPlayer) && !x.entityTags().contains("arcade_hall_sign"))) {
                counts.merge(e.getType().toShortString(), 1, Integer::sum);
            }
            return counts.toString();
        }));
    }
}
