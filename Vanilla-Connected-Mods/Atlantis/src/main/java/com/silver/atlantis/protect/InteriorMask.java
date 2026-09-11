package com.silver.atlantis.protect;

import net.minecraft.util.math.BlockPos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.BitSet;

/**
 * Compact world-space mask for interior-air markers in one schematic.
 *
 * The mask is indexed in schematic-local order: (y * length + z) * width + x.
 * Ordinary exterior air is never added to this mask.
 */
public final class InteriorMask {

    private static final long MAX_VOLUME = Integer.MAX_VALUE;

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int width;
    private final int height;
    private final int length;
    private final long volume;
    private final BitSet bits;

    private InteriorMask(
        int minX,
        int minY,
        int minZ,
        int width,
        int height,
        int length,
        BitSet bits
    ) {
        validateDimensions(width, height, length);
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.width = width;
        this.height = height;
        this.length = length;
        this.volume = (long) width * height * length;
        this.bits = (BitSet) bits.clone();
    }

    public static Builder builder(int minX, int minY, int minZ, int width, int height, int length) {
        return new Builder(minX, minY, minZ, width, height, length);
    }

    public static InteriorMask empty() {
        return new InteriorMask(0, 0, 0, 0, 0, 0, new BitSet());
    }

    public boolean contains(BlockPos pos) {
        return pos != null && contains(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean contains(int x, int y, int z) {
        int index = indexOf(x, y, z);
        return index >= 0 && bits.get(index);
    }

    public boolean isEmpty() {
        return bits.isEmpty();
    }

    public int markedCount() {
        return bits.cardinality();
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int length() {
        return length;
    }

    void write(DataOutput out) throws IOException {
        out.writeInt(minX);
        out.writeInt(minY);
        out.writeInt(minZ);
        out.writeInt(width);
        out.writeInt(height);
        out.writeInt(length);

        long[] words = bits.toLongArray();
        out.writeInt(words.length);
        for (long word : words) {
            out.writeLong(word);
        }
    }

    static InteriorMask read(DataInput in) throws IOException {
        int minX = in.readInt();
        int minY = in.readInt();
        int minZ = in.readInt();
        int width = in.readInt();
        int height = in.readInt();
        int length = in.readInt();
        validateDimensions(width, height, length);

        long volume = (long) width * height * length;
        long maxWords = (volume + 63L) / 64L;
        int wordCount = in.readInt();
        if (wordCount < 0 || wordCount > maxWords) {
            throw new IOException("Invalid interior mask word count: " + wordCount);
        }

        long[] words = new long[wordCount];
        for (int i = 0; i < wordCount; i++) {
            words[i] = in.readLong();
        }

        BitSet bits = BitSet.valueOf(words);
        if (bits.length() > volume) {
            throw new IOException("Interior mask contains bits outside its bounds");
        }
        return new InteriorMask(minX, minY, minZ, width, height, length, bits);
    }

    private int indexOf(int x, int y, int z) {
        if (width <= 0 || height <= 0 || length <= 0) {
            return -1;
        }

        long localX = (long) x - minX;
        long localY = (long) y - minY;
        long localZ = (long) z - minZ;
        if (localX < 0 || localX >= width
            || localY < 0 || localY >= height
            || localZ < 0 || localZ >= length) {
            return -1;
        }

        long index = ((localY * length) + localZ) * width + localX;
        return (int) index;
    }

    private static void validateDimensions(int width, int height, int length) {
        if (width < 0 || height < 0 || length < 0) {
            throw new IllegalArgumentException("Interior mask dimensions cannot be negative");
        }
        if ((width == 0 || height == 0 || length == 0)
            && (width != 0 || height != 0 || length != 0)) {
            throw new IllegalArgumentException("Interior mask dimensions must all be zero or all be positive");
        }
        long volume = (long) width * height * length;
        if (volume > MAX_VOLUME) {
            throw new IllegalArgumentException("Interior mask is too large: " + volume + " cells");
        }
    }

    public static final class Builder {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int width;
        private final int height;
        private final int length;
        private final BitSet bits;

        private Builder(int minX, int minY, int minZ, int width, int height, int length) {
            validateDimensions(width, height, length);
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.width = width;
            this.height = height;
            this.length = length;
            this.bits = new BitSet();
        }

        public void set(int localX, int localY, int localZ) {
            if (localX < 0 || localX >= width
                || localY < 0 || localY >= height
                || localZ < 0 || localZ >= length) {
                throw new IllegalArgumentException("Interior mask coordinate is outside its bounds");
            }

            long index = ((long) localY * length + localZ) * width + localX;
            bits.set((int) index);
        }

        public InteriorMask build() {
            return new InteriorMask(minX, minY, minZ, width, height, length, bits);
        }
    }
}
