package com.silver.qaclient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** Reading the client's world, like looking around. */
public final class W {
    private W() {
    }

    public static String id(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
    }

    public static String idAt(Bot b, BlockPos p) {
        return b.call(() -> id(b.mc.level.getBlockState(p)));
    }

    /** All blocks within the box around the player whose registry path satisfies the test. */
    public static List<BlockPos> scan(Bot b, int rx, int ry, int rz, Predicate<String> test) {
        return b.call(() -> {
            Minecraft mc = b.mc;
            List<BlockPos> out = new ArrayList<>();
            BlockPos c = mc.player.blockPosition();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int x = c.getX() - rx; x <= c.getX() + rx; x++) {
                for (int z = c.getZ() - rz; z <= c.getZ() + rz; z++) {
                    for (int y = c.getY() - ry; y <= c.getY() + ry; y++) {
                        m.set(x, y, z);
                        BlockState s = mc.level.getBlockState(m);
                        if (!s.isAir() && test.test(id(s))) {
                            out.add(m.immutable());
                        }
                    }
                }
            }
            return out;
        });
    }

    public static List<BlockPos> scanExact(Bot b, int rx, int ry, int rz, String name) {
        return scan(b, rx, ry, rz, name::equals);
    }
}
