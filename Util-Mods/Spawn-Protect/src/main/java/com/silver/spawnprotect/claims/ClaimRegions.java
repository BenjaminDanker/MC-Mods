package com.silver.spawnprotect.claims;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Groups claimed chunks into connected regions. OpenPAC stores a separate list for every claim type (sub-claim and
 * forceload state), so one visible claim can be several lists; here they are merged by position. Two chunks belong to
 * the same region when they share an edge; chunks that only touch at a corner are separate regions.
 */
public final class ClaimRegions {
    /** One connected block of claimed chunks. Coordinates are chunk coordinates. */
    public record Region(int chunks, int minX, int minZ, int maxX, int maxZ, int centerX, int centerZ) {
    }

    private ClaimRegions() {
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }

    /** Connected regions of {@code chunks} (packed as x in the high word, z in the low word), largest first. */
    public static List<Region> connected(Set<Long> chunks) {
        Set<Long> remaining = new HashSet<>(chunks);
        List<Region> regions = new ArrayList<>();
        while (!remaining.isEmpty()) {
            long start = remaining.iterator().next();
            remaining.remove(start);
            List<Long> members = new ArrayList<>();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                long current = queue.poll();
                members.add(current);
                int x = unpackX(current);
                int z = unpackZ(current);
                for (long next : new long[] {pack(x + 1, z), pack(x - 1, z), pack(x, z + 1), pack(x, z - 1)}) {
                    if (remaining.remove(next)) {
                        queue.add(next);
                    }
                }
            }
            regions.add(describe(members));
        }
        regions.sort((a, b) -> a.chunks() != b.chunks() ? Integer.compare(b.chunks(), a.chunks())
            : a.minX() != b.minX() ? Integer.compare(a.minX(), b.minX()) : Integer.compare(a.minZ(), b.minZ()));
        return regions;
    }

    public static long key(int chunkX, int chunkZ) {
        return pack(chunkX, chunkZ);
    }

    private static Region describe(List<Long> members) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long member : members) {
            int x = unpackX(member);
            int z = unpackZ(member);
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
        }
        // The middle of the claim: the centre of its bounding box, or, when that spot is not claimed (an L or a ring),
        // the claimed chunk nearest to it, so the target is always inside the region.
        double averageX = (minX + maxX) / 2.0;
        double averageZ = (minZ + maxZ) / 2.0;
        long best = members.get(0);
        double bestDistance = Double.MAX_VALUE;
        for (long member : members) {
            double dx = unpackX(member) - averageX;
            double dz = unpackZ(member) - averageZ;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = member;
            }
        }
        return new Region(members.size(), minX, minZ, maxX, maxZ, unpackX(best), unpackZ(best));
    }
}
