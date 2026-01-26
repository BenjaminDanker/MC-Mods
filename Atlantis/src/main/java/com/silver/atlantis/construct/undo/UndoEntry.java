package com.silver.atlantis.construct.undo;

/**
 * A single block state to restore at a world position.
 *
 * @param blockString WorldEdit block string (includes properties)
 * @param nbtSnbt Optional SNBT for block entity NBT (WorldEdit LinBus SNBT format)
 */
public record UndoEntry(int x, int y, int z, String blockString, String nbtSnbt) {
}
