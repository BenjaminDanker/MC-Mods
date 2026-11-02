package com.silver.aipets.service.consolidation;

import java.util.function.Consumer;

/** Opt-in test trace sink; absent in the ordinary service and scheduler. */
public final class ConsolidationDiagnostics {
    private static final ThreadLocal<Consumer<String>> SINK = new ThreadLocal<>();
    private ConsolidationDiagnostics() { }
    public static AutoCloseable attach(Consumer<String> sink) {
        if (SINK.get() != null) throw new IllegalStateException("Diagnostic scope already active");
        SINK.set(sink);
        return SINK::remove;
    }
    public static void note(String message) {
        var sink=SINK.get();
        if (sink != null) sink.accept(message);
    }
}
