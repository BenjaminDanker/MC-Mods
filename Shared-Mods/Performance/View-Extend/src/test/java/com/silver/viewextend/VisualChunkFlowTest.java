package com.silver.viewextend;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class VisualChunkFlowTest {
    @Test void twentyClientsHaveIndependentQuotasAndStalledClientsStop() {
        for (int client = 0; client < 20; client++) {
            float quota = client == 0 ? 64 : 9;
            int outstanding = client == 19 ? 10 : 0;
            boolean open = false;
            int sent = 0;
            while (VisualChunkFlow.canSend(quota, outstanding, 10, open)) {
                if (!open) { outstanding++; open = true; }
                quota--; sent++;
            }
            assertEquals(client == 19 ? 0 : client == 0 ? 64 : 9, sent);
            assertFalse(VisualChunkFlow.canSend(quota, outstanding, 10, open));
        }
    }
    @Test void existingBatchMayFinishButCannotExceedQuota() {
        assertFalse(VisualChunkFlow.canSend(9, 1, 1, false));
        assertTrue(VisualChunkFlow.canSend(9, 1, 1, true));
        assertFalse(VisualChunkFlow.canSend(.9f, 0, 1, false));
        assertFalse(VisualChunkFlow.canSend(Float.NaN, 0, 1, false));
    }
}
