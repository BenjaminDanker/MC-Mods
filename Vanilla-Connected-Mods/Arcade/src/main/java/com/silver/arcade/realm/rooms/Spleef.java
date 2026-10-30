package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Spleef, a versus room: everyone stands on a snow floor over a pit; hit the snow to break it under the others. Who
 * falls is out; the last one standing clears. If time runs out, everyone still standing clears.
 */
public final class Spleef extends BaseRoom {
    private final Random random = new Random();
    private int radius;

    public String id() { return "spleef"; }
    public String title() { return "Spleef"; }
    public String explain() {
        return "Dig the snow with your shovel to break it. Break it under the others, keep it under yourself. Fall and you're out; "
            + "the last one standing wins.";
    }

    @Override
    public int[] bounds() {
        return new int[] {-44, -30, -44, 44, 40, 70};
    }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(Math.max(90, 120 - tier * 2)); // 15 levels; a short clock only helps survivors
    }

    @Override
    protected void build() {
        radius = Math.max(5, 9 - tier / 3);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) {
                    continue;
                }
                // Higher tiers start with a few holes already.
                // Holes never in the middle, nor on the ring where players start (radius - 2, +-1).
                double d = Math.sqrt(x * x + z * z);
                if (tier >= 3 && random.nextInt(100) < Math.min(20, 2 * tier) && d > 3 && Math.abs(d - (radius - 2)) > 1.5) {
                    continue;
                }
                arena.set(x, 0, z, Blocks.SNOW_BLOCK.defaultBlockState());
            }
        }
        // The disc's frame hangs below the "out" line (y -5), so it never catches anyone: a ring of blue ice and sea
        // lanterns that outlines the arena from every view, and a frosted crystal hub under the centre.
        for (int x = -radius - 3; x <= radius + 3; x++) {
            for (int z = -radius - 3; z <= radius + 3; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > radius + 0.5 && d <= radius + 2.5) {
                    boolean lamp = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(z, x))), 30) < 6;
                    arena.set(x, -5, z, lamp ? Blocks.SEA_LANTERN.defaultBlockState()
                        : (d > radius + 1.5 ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.BLUE_ICE.defaultBlockState()));
                }
                if (d <= 2.5) {
                    arena.set(x, -6, z, d <= 1.2 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.BLUE_ICE.defaultBlockState());
                }
            }
        }
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.IcePalaceLook.build(arena, tier, radius));
        List<ServerPlayer> list = List.copyOf(players.values());
        for (int i = 0; i < list.size(); i++) {
            double angle = Math.PI * 2 * i / list.size();
            ServerPlayer player = list.get(i);
            arena.put(player, Math.floor(Math.cos(angle) * (radius - 2)) + 0.5, 1, Math.floor(Math.sin(angle) * (radius - 2)) + 0.5, 0F); // centred on a block
            // Adventure mode: the client only digs blocks the tool is allowed to break.
            give(player, com.silver.arcade.realm.PlayerKit.digger(player, new ItemStack(Items.DIAMOND_SHOVEL), Blocks.SNOW_BLOCK));
        }
    }

    @Override
    public void tick(long tickInRoom) {
        List<ServerPlayer> standing = undecided();
        for (ServerPlayer player : standing) {
            if (heightOf(player) < -3) {
                out(player, "Out!");
            }
        }
        standing = undecided();
        if (players.size() > 1 && standing.size() == 1) {
            clear(standing.get(0));
            Gamemaster.bar(standing.get(0), "Last one standing!");
        }
    }

    @Override
    public java.util.Set<java.util.UUID> cleared() {
        // Time is up: everyone still standing clears.
        undecided().forEach(this::clear);
        return super.cleared();
    }

    @Override
    public boolean hit(ServerPlayer player, BlockPos pos) {
        if (pos.getY() != arena.origin().getY() || !arena.level().getBlockState(pos).is(Blocks.SNOW_BLOCK)) {
            return false;
        }
        if (!decided(player.getUUID()) && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 30) {
            arena.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            arena.level().playSound(null, pos, SoundEvents.POWDER_SNOW_BREAK, SoundSource.BLOCKS, 1F, 1F);
        }
        return true;
    }
}
