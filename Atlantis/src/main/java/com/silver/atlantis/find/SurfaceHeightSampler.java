package com.silver.atlantis.find;

/**
 * Returns a terrain surface height for an (x,z) column.
 */
public interface SurfaceHeightSampler {
    int sampleSurfaceY(int x, int z);
}
