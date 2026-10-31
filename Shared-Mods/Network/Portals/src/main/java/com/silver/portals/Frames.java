package com.silver.portals;

import com.silver.authorization.PermissionNodes;
import com.silver.authorization.fabric.AuthorizationChecks;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Portal frames: each configured portal has its own frame block. Using the portal's item on the inside of a frame
 * (at least 2 wide and 3 tall inside, like a nether portal) fills it with {@link PortalBlock}.
 */
final class Frames {
    private static final Map<Block, PortalConfig.Portal> BY_FRAME = new HashMap<>();
    private static final Map<Item, PortalConfig.Portal> BY_ITEM = new HashMap<>();
    private static final int WIDTH = 2;
    private static final int HEIGHT = 3;

    private Frames() {
    }

    static void register(PortalConfig.Portal portal) {
        Block frame = portal.frameBlock();
        Item item = portal.lightItem();
        if (frame == null || item == null) {
            PortalsMod.LOGGER.error("ALERT: portal {} has an unknown frame block or item", portal.name);
            return;
        }
        BY_FRAME.put(frame, portal);
        BY_ITEM.put(item, portal);
    }

    static void clear() {
        BY_FRAME.clear();
        BY_ITEM.clear();
    }

    static boolean lightsSomething(Item item) {
        return BY_ITEM.containsKey(item);
    }

    /** The portal whose frame touches the portal block at {@code pos}, or null. */
    static PortalConfig.Portal portalAt(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            PortalConfig.Portal portal = BY_FRAME.get(level.getBlockState(pos.relative(direction)).getBlock());
            if (portal != null) {
                return portal;
            }
        }
        // inside a wide portal: walk to the frame along the portal's plane
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PortalBlock) {
            Direction.Axis axis = state.getValue(PortalBlock.AXIS);
            for (Direction direction : Direction.values()) {
                if (direction.getAxis() != axis && direction.getAxis() != Direction.Axis.Y) {
                    continue;
                }
                BlockPos at = pos.relative(direction);
                for (int step = 0; step < 21 && level.getBlockState(at).getBlock() instanceof PortalBlock; step++) {
                    at = at.relative(direction);
                }
                PortalConfig.Portal portal = BY_FRAME.get(level.getBlockState(at).getBlock());
                if (portal != null) {
                    return portal;
                }
            }
        }
        return null;
    }

    /**
     * Lights the frame around {@code inside} with {@code item}; {@code player} null means the server lights it (a mod
     * opening a gate it built). True when a portal was made.
     */
    static boolean light(Level level, BlockPos inside, Item item, Player player) {
        PortalConfig.Portal portal = BY_ITEM.get(item);
        if (portal == null || !touchesFrame(level, inside, portal.frameBlock())) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (!portal.anyoneCanLight && !AuthorizationChecks.has(serverPlayer, PermissionNodes.PORTAL_USE)) {
                PortalsMod.LOGGER.info("{} may not light portal {}", serverPlayer.getName().getString(), portal.name);
                return false;
            }
        } else if (!portal.anyoneCanLight) {
            // the server itself (a mod opening a gate it built) may light only portals anyone may light
            PortalsMod.LOGGER.info("Portal {} can't be lit by the server (anyoneCanLight is false)", portal.name);
            return false;
        }
        for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
            for (int along = -WIDTH; along <= 1; along++) {
                for (int up = -HEIGHT + 1; up <= 1; up++) {
                    BlockPos corner = inside.relative(axis, along).above(up);
                    if (fill(level, corner, axis, portal.frameBlock())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean touchesFrame(Level level, BlockPos pos, Block frame) {
        if (level.getBlockState(pos).getBlock() == frame) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).getBlock() == frame) {
                return true;
            }
        }
        return false;
    }

    /** Fills the opening whose bottom-left inside corner is {@code corner}, if it is empty and fully framed. */
    private static boolean fill(Level level, BlockPos corner, Direction.Axis axis, Block frame) {
        for (int i = 0; i < WIDTH; i++) {
            for (int j = 0; j < HEIGHT; j++) {
                BlockState inside = level.getBlockState(corner.relative(axis, i).above(j));
                if (!inside.isAir() && !inside.canBeReplaced()) {
                    return false;
                }
            }
        }
        for (int i = -1; i <= WIDTH; i++) {
            if (level.getBlockState(corner.relative(axis, i).below()).getBlock() != frame
                    || level.getBlockState(corner.relative(axis, i).above(HEIGHT)).getBlock() != frame) {
                return false;
            }
        }
        for (int j = 0; j < HEIGHT; j++) {
            if (level.getBlockState(corner.relative(axis, -1).above(j)).getBlock() != frame
                    || level.getBlockState(corner.relative(axis, WIDTH).above(j)).getBlock() != frame) {
                return false;
            }
        }
        BlockState portal = PortalsMod.portalBlock().defaultBlockState().setValue(PortalBlock.AXIS, axis);
        for (int i = 0; i < WIDTH; i++) {
            for (int j = 0; j < HEIGHT; j++) {
                level.setBlock(corner.relative(axis, i).above(j), portal, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        return true;
    }
}
