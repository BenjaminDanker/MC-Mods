package com.silver.viewextend;

final class ViewDistancePolicy {
    static final int MAX_DISTANCE = 127;
    private ViewDistancePolicy() {}

    static int effective(int normal, int extension, int requested, int cap) {
        int total = (int) Math.min(MAX_DISTANCE, (long) normal + extension);
        // Client settings use a signed byte. Do not reinterpret negative values as 128..255.
        int client = requested > 0 ? Math.min(requested, MAX_DISTANCE) : total;
        return Math.max(Math.min(normal, MAX_DISTANCE), Math.min(total, Math.min(client, cap)));
    }
}
