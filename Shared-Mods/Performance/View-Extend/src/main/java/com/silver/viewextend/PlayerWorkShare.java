package com.silver.viewextend;

/** Bounded deficit credits. A costly packet can incur debt but cannot starve permanently. */
final class PlayerWorkShare {
    private long bytes, nanos;
    private long byteQuantum, timeQuantum;
    void accrue(long byteQuantum, long timeQuantum) {
        this.byteQuantum = Math.max(1, byteQuantum);
        this.timeQuantum = Math.max(1, timeQuantum);
        bytes = Math.min(Math.max(1_048_576, byteQuantum * 4), bytes + byteQuantum);
        nanos = Math.min(Math.max(500_000, timeQuantum * 4), nanos + timeQuantum);
    }
    boolean available() { return bytes > 0 && nanos > 0; }
    void charge(long sentBytes, long elapsedNanos) {
        bytes = Math.max(-Math.max(1_048_576, byteQuantum * 4), bytes - sentBytes);
        nanos = Math.max(-Math.max(500_000, timeQuantum * 4), nanos - elapsedNanos);
    }
}
