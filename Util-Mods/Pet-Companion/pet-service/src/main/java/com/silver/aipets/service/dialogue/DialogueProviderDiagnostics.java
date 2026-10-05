package com.silver.aipets.service.dialogue;

import java.util.Objects;
import java.util.function.Consumer;

/** Opt-in, per-thread provider metadata; normal service calls have no sink. */
public final class DialogueProviderDiagnostics {
    private static final ThreadLocal<Consumer<String>> SINK = new ThreadLocal<>();
    private DialogueProviderDiagnostics() { }

    public static AutoCloseable attach(Consumer<String> sink) {
        Objects.requireNonNull(sink);
        if (SINK.get() != null) throw new IllegalStateException("Provider diagnostics already attached");
        SINK.set(sink);
        return SINK::remove;
    }

    public static void emit(String message) {
        Consumer<String> sink = SINK.get();
        if (sink != null) sink.accept(message);
    }
}
