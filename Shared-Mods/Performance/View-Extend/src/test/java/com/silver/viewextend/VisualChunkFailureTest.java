package com.silver.viewextend;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class VisualChunkFailureTest {
    @Test void classificationUnwrapsAsyncErrorsWithoutLosingRootCause() {
        var root = new IOException("disk");
        var wrapped = new CompletionException(new ExecutionException(root));
        assertSame(root, VisualChunkFailure.unwrap(wrapped));
        assertEquals(VisualChunkFailure.IO, VisualChunkFailure.classify(false, wrapped));
        assertEquals(VisualChunkFailure.TIMEOUT, VisualChunkFailure.classify(false, new TimeoutException()));
        assertEquals(VisualChunkFailure.OVERLOADED, VisualChunkFailure.classify(false, new RejectedExecutionException()));
    }
    @Test void expectedGenerationStatesRemainDistinctFromBrokenData() {
        assertEquals(VisualChunkFailure.MISSING, VisualChunkFailure.classify(true, null));
        assertEquals(VisualChunkFailure.UNFINISHED, VisualChunkFailure.classify(false,
                new VisualChunkFailure.UnfinishedChunk("minecraft:noise")));
        assertEquals(VisualChunkFailure.PREPARATION, VisualChunkFailure.classify(false,
                new IllegalArgumentException("bad palette")));
    }
}
