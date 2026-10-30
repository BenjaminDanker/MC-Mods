package com.silver.arcade.selftest;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;

/**
 * What a suite sees. Suites run on the self-test's own worker thread and read or change the game only through
 * {@link #on}, which runs the work on the server thread and waits for it; {@link #ticks} and {@link #until} wait in
 * server ticks (so they follow the tick rate), never wall time.
 */
public final class T {
    public final MinecraftServer server;
    public final Report report;
    public final Options options;
    public String suite = "";
    /** True while cleaning up: the abort flag no longer stops work. */
    public static volatile boolean cleaning;
    private long mark = System.nanoTime();

    public T(MinecraftServer server, Report report, Options options) {
        this.server = server;
        this.report = report;
        this.options = options;
    }

    /** Runs work on the server thread, waiting for its result (30 s at most). */
    public <V> V on(Callable<V> work) {
        if (SelfTest.aborted != null && !cleaning && !server.isSameThread()) {
            throw new IllegalStateException("aborted: " + SelfTest.aborted);
        }
        if (server.isSameThread()) {
            try {
                return work.call(); // already on the server thread (a condition or step calling a helper)
            } catch (RuntimeException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
        try {
            return server.submit(() -> {
                try {
                    return work.call();
                } catch (RuntimeException failure) {
                    throw failure;
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }).get(60, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            if (cause instanceof RuntimeException r) {
                throw r;
            }
            throw new IllegalStateException(cause);
        } catch (InterruptedException | java.util.concurrent.TimeoutException failure) {
            throw new IllegalStateException("server thread did not answer: " + failure);
        }
    }

    public void run(Runnable work) {
        on(() -> {
            work.run();
            return null;
        });
    }

    public int tick() {
        return server.getTickCount();
    }

    /** Waits {@code n} server ticks. */
    public void ticks(int n) {
        noWaitOnServerThread();
        int end = tick() + n;
        long give = System.nanoTime() + (n * 50L * 4 + 5000) * 1_000_000L; // a paused server does not tick
        while (tick() < end && System.nanoTime() < give) {
            sleep(2);
        }
    }

    /** Polls {@code cond} (on the server thread, once per tick) for up to {@code maxTicks}; true as soon as it holds. */
    public boolean until(BooleanSupplier cond, int maxTicks) {
        noWaitOnServerThread();
        int end = tick() + maxTicks;
        int last = -1;
        long give = System.nanoTime() + (maxTicks * 50L * 4 + 5000) * 1_000_000L;
        long lastEval = 0;
        while (true) {
            if (System.nanoTime() > give) {
                return on(cond::getAsBoolean);
            }
            if (tick() != last || System.nanoTime() - lastEval > 100_000_000L) {
                last = tick();
                lastEval = System.nanoTime();
                if (on(cond::getAsBoolean)) {
                    return true;
                }
            }
            if (tick() >= end) {
                return false;
            }
            sleep(2);
        }
    }

    /** Waiting on the server thread would stall the server (and trip its watchdog): refuse. */
    private void noWaitOnServerThread() {
        if (server.isSameThread()) {
            throw new IllegalStateException("a self-test wait was called on the server thread");
        }
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted");
        }
    }

    // --- results ---

    public void check(String name, boolean ok, String detail) {
        long now = System.nanoTime();
        report.add(suite, name, ok, (now - mark) / 1_000_000, detail);
        mark = now;
        SelfTest.progress(this, name, ok);
    }

    public void ok(String name) {
        check(name, true, "");
    }

    public void ok(String name, String detail) {
        check(name, true, detail);
    }

    public void fail(String name, String detail) {
        check(name, false, detail);
    }

    /** A check that records an exception from the body as a failure. */
    public void guard(String name, ThrowingRunnable body) {
        try {
            body.run();
        } catch (Throwable failure) {
            fail(name, "exception: " + failure + " @ " + (failure.getStackTrace().length > 0 ? failure.getStackTrace()[0] : ""));
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** What the trigger asked for. */
    public static final class Options {
        public boolean force;
        public boolean quick;
        public java.util.List<Integer> tiers = java.util.List.of();
        public java.util.List<String> rooms = java.util.List.of();
        public float tickRate = 20F;
        public int maxRoomSeconds = 240;
    }
}
