package com.silver.atlantis.construct;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockTypes;

/**
 * Converts barrier blocks to air during a paste.
 *
 * This avoids having to run a second full-region "replace barriers -> air" pass,
 * which is very expensive on multi-million-block slices.
 */
final class BarrierToAirExtent extends AbstractDelegateExtent {

    BarrierToAirExtent(Extent extent) {
        super(extent);
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block) throws WorldEditException {
        if (block != null && block.getBlockType() == BlockTypes.BARRIER) {
            if (block instanceof BaseBlock) {
                @SuppressWarnings("unchecked")
                T air = (T) BlockTypes.AIR.getDefaultState().toBaseBlock();
                return getExtent().setBlock(position, air);
            }

            @SuppressWarnings("unchecked")
            T air = (T) BlockTypes.AIR.getDefaultState();
            return getExtent().setBlock(position, air);
        }

        return getExtent().setBlock(position, block);
    }
}
