package com.silver.viewextend;

/** Vanilla replenishes quota and processes ACKs. Extended batches only consume the remainder. */
final class VisualChunkFlow {
    static boolean canSend(float quota, int outstanding, int maximum, boolean open) {
        return quota >= 1 && (open || outstanding < maximum);
    }
}
