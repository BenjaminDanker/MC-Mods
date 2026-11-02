package com.silver.viewextend;

import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/** Expected generation states are separate from actual read/encoding failures. */
enum VisualChunkFailure {
    MISSING(1200), UNFINISHED(100), TIMEOUT(100), OVERLOADED(20), IO(100), PREPARATION(100);

    final int retryTicks;
    VisualChunkFailure(int retryTicks) { this.retryTicks = retryTicks; }

    static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    static VisualChunkFailure classify(boolean missing, Throwable error) {
        if (missing) return MISSING;
        error = unwrap(error);
        if (error instanceof UnfinishedChunk) return UNFINISHED;
        if (error instanceof TimeoutException) return TIMEOUT;
        if (error instanceof RejectedExecutionException) return OVERLOADED;
        if (error instanceof IOException) return IO;
        return PREPARATION;
    }

    static final class UnfinishedChunk extends IllegalArgumentException {
        UnfinishedChunk(String status) { super("Saved chunk has generation status " + status + "; waiting for minecraft:full"); }
    }
}
