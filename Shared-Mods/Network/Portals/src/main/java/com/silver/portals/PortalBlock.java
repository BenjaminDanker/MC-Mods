package com.silver.portals;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;

/**
 * The inside of a lit portal. Vanilla clients see a nether portal (or an end portal for a flat one). Standing in it
 * doesn't move anyone itself: vanilla's portal timer asks {@link #getPortalDestination}, which hands the player to
 * {@link Travel} (the proxy moves them) and returns null so vanilla does nothing.
 */
final class PortalBlock extends Block implements Portal, PolymerBlock {
    static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    private static final VoxelShape ALONG_X = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape ALONG_Z = Block.box(6, 0, 0, 10, 16, 16);
    private static final VoxelShape FLAT = Block.box(0, 6, 0, 16, 10, 16);

    PortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case Z -> ALONG_Z;
            case Y -> FLAT;
            default -> ALONG_X;
        };
    }

    /** A portal never breaks itself when its neighbours change (only when its frame is broken by hand). */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return state;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return 1;
    }

    @Override
    public TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        PortalConfig.Portal portal = Frames.portalAt(level, pos);
        if (portal != null && entity instanceof ServerPlayer player) {
            Travel.steppedIn(player, portal);
        }
        return null;
    }

    // ---- what vanilla clients see

    @Override
    public Block getPolymerReplacement(Block block, PacketContext context) {
        return Blocks.NETHER_PORTAL;
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
        Direction.Axis axis = state.getValue(AXIS);
        if (axis == Direction.Axis.Y) {
            return Blocks.END_PORTAL.defaultBlockState();
        }
        return Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);
    }

    @Override
    public void onPolymerBlockSend(BlockState state, BlockPos.MutableBlockPos pos, ServerPlayer player) {
        if (state.getValue(AXIS) == Direction.Axis.Y) {
            player.connection.send(PolymerBlockUtils.createBlockEntityPacket(pos,
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("end_portal")), new CompoundTag()));
        }
    }
}
