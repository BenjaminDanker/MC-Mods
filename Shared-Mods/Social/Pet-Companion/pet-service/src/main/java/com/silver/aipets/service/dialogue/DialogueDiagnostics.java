package com.silver.aipets.service.dialogue;

import java.util.function.BiConsumer;

/** Optional per-thread diagnostic sink used only by the isolated Dev Player CLI. */
public final class DialogueDiagnostics {
    private static final ThreadLocal<BiConsumer<String,Long>> SINK = new ThreadLocal<>();
    private DialogueDiagnostics() { }
    public static AutoCloseable attach(BiConsumer<String,Long> sink) {
        if (SINK.get() != null) throw new IllegalStateException("Diagnostic scope already active");
        SINK.set(sink);
        return SINK::remove;
    }
    public static void elapsed(String stage,long beganNanos) {
        var sink=SINK.get();
        if (sink != null) sink.accept(stage,(System.nanoTime()-beganNanos)/1_000_000);
    }
    public static void event(String stage, java.util.UUID correlation, String detail) {
        System.getLogger(DialogueDiagnostics.class.getName()).log(System.Logger.Level.INFO,
                "dialogue_trace stage=" + stage + " correlation=" + correlation + " " + detail);
    }
}
