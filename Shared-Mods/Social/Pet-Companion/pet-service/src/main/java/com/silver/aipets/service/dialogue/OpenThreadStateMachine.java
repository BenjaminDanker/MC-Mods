package com.silver.aipets.service.dialogue;

import java.time.Instant;
import java.util.Objects;

/** State transitions are explicit; a stale caller cannot advance a newer thread version. */
public final class OpenThreadStateMachine {
    public enum State { OPEN, ACTIVE, PAUSED, COMPLETED, FORGOTTEN }
    public enum Event { CUE_FOUND, INTERRUPTED, RESUMED, OWNER_COMPLETED, ABANDONED, EXPIRED }
    private OpenThreadStateMachine() { }
    public static ThreadState transition(ThreadState current, Event event, long expectedVersion, Instant now) {
        Objects.requireNonNull(current); Objects.requireNonNull(event); Objects.requireNonNull(now);
        if (current.version()!=expectedVersion) return current;
        State next=switch(event) {
            case CUE_FOUND -> current.state()==State.OPEN ? State.ACTIVE : current.state();
            case RESUMED -> current.state()==State.PAUSED ? State.ACTIVE : current.state();
            case INTERRUPTED -> current.state()==State.ACTIVE ? State.PAUSED : current.state();
            case OWNER_COMPLETED -> current.state()==State.OPEN||current.state()==State.ACTIVE||current.state()==State.PAUSED ? State.COMPLETED : current.state();
            case ABANDONED,EXPIRED -> current.state()==State.OPEN||current.state()==State.PAUSED ? State.FORGOTTEN : current.state();
        };
        if(next==current.state()) return current;
        return new ThreadState(next,current.createdAt(),current.version()+1,now);
    }
    public record ThreadState(State state, Instant createdAt, long version, Instant lastReferencedAt) {
        public ThreadState { Objects.requireNonNull(state);Objects.requireNonNull(createdAt);Objects.requireNonNull(lastReferencedAt);if(version<1)throw new IllegalArgumentException("version must be positive"); }
    }
}
