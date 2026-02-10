package com.silver.atlantis.find;

import net.minecraft.util.math.BlockPos;

/**
 * Incremental evaluation of a window's avg(|h - mean(h)|) using two passes:
 * (1) sample + mean, (2) abs deviation.
 */
final class WindowEvaluationJob {

    private enum Pass {
        MEAN,
        DEVIATION,
        DONE
    }

    private final BlockPos center;
    private final int windowSize;
    private final int step;
    private final int attempt;

    private final int half;
    private final int startX;
    private final int startZ;

    private final int xCount;
    private final int zCount;
    private final int total;

    private Pass pass = Pass.MEAN;

    private int index = 0;
    private final int[] heights;

    private long sum = 0;
    private int min = Integer.MAX_VALUE;
    private int max = Integer.MIN_VALUE;

    private double mean;
    private double devSum;

    private FlatAreaSearchResult result;

    private WindowEvaluationJob(BlockPos center, int windowSize, int step, int attempt) {
        this.center = center;
        this.windowSize = windowSize;
        this.step = step;
        this.attempt = attempt;

        this.half = windowSize / 2;
        this.startX = center.getX() - half;
        this.startZ = center.getZ() - half;

        this.xCount = (int) Math.ceil(windowSize / (double) step);
        this.zCount = (int) Math.ceil(windowSize / (double) step);
        this.total = xCount * zCount;
        this.heights = new int[total];
    }

    static WindowEvaluationJob create(BlockPos center, int windowSize, int step, int attempt) {
        return new WindowEvaluationJob(center, windowSize, step, attempt);
    }

    /**
     * Advances the evaluation until either finished or the nano deadline is hit.
     *
     * @return true if finished (result available), false if more work remains
     */
    boolean advance(SurfaceHeightSampler sampler, long nanoDeadline, int maxAvgAbsDeviation) {
        while (System.nanoTime() < nanoDeadline && pass != Pass.DONE) {
            if (pass == Pass.MEAN) {
                if (index >= total) {
                    mean = sum / (double) total;
                    index = 0;
                    devSum = 0.0;
                    pass = Pass.DEVIATION;
                    continue;
                }

                int xIndex = index / zCount;
                int zIndex = index % zCount;
                int x = startX + (xIndex * step);
                int z = startZ + (zIndex * step);

                int y = sampler.sampleSurfaceY(x, z);
                heights[index] = y;
                sum += y;
                if (y < min) min = y;
                if (y > max) max = y;
                index++;
                continue;
            }

            if (pass == Pass.DEVIATION) {
                if (index >= total) {
                    double avgAbsDev = devSum / total;
                    BlockPos yAdjustedCenter = new BlockPos(center.getX(), (int) Math.round(mean), center.getZ());
                    result = new FlatAreaSearchResult(yAdjustedCenter, total, mean, avgAbsDev, min, max, attempt);
                    pass = Pass.DONE;
                    break;
                }

                devSum += Math.abs(heights[index] - mean);
                index++;

                // Early fail: remaining samples can't reduce devSum.
                if (maxAvgAbsDeviation >= 0 && devSum > (long) maxAvgAbsDeviation * (long) total) {
                    double avgAbsDevLowerBound = devSum / total;
                    BlockPos yAdjustedCenter = new BlockPos(center.getX(), (int) Math.round(mean), center.getZ());
                    result = new FlatAreaSearchResult(yAdjustedCenter, total, mean, avgAbsDevLowerBound, min, max, attempt);
                    pass = Pass.DONE;
                    break;
                }
            }
        }

        return pass == Pass.DONE;
    }

    FlatAreaSearchResult finishResult() {
        return result;
    }

    int attempt() {
        return attempt;
    }

    int step() {
        return step;
    }

    int totalSamples() {
        return total;
    }

    int completedSamplesThisPass() {
        return Math.min(index, total);
    }

    String passName() {
        return switch (pass) {
            case MEAN -> "MEAN";
            case DEVIATION -> "DEVIATION";
            case DONE -> "DONE";
        };
    }

    double progressPercent() {
        if (pass == Pass.DONE) {
            return 100.0;
        }

        // Report progress within the current pass only.
        double perPass = (total == 0) ? 1.0 : (completedSamplesThisPass() / (double) total);
        return Math.min(99.9, perPass * 100.0);
    }
}
