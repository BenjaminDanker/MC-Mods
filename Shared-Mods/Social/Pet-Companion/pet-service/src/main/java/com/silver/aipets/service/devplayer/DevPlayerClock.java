package com.silver.aipets.service.devplayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Persistent, operator-controlled UTC clock. It never changes production timestamps. */
public final class DevPlayerClock extends Clock {
    private final Path state;
    private Instant now;

    public DevPlayerClock(Path state) throws IOException {
        this.state = state.toAbsolutePath();
        Files.createDirectories(this.state.getParent());
        now = Files.exists(state) ? Instant.parse(Files.readString(state).strip()) : Instant.now();
        save();
    }

    public void reset() throws IOException { now = Instant.now(); save(); }
    public void advance(Duration amount) throws IOException {
        if (amount.isNegative() || amount.isZero() || amount.compareTo(Duration.ofDays(365)) > 0)
            throw new IllegalArgumentException("Advance must be positive and at most one year");
        now = now.plus(amount); save();
    }
    private void save() throws IOException { Files.writeString(state, now.toString() + "\n"); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) {
        if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException("Dev clock is UTC");
        return this;
    }
    @Override public Instant instant() { return now; }
}
