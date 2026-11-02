package com.silver.aipets.service.provider;

import com.silver.aipets.service.metrics.PetOperationalMetrics;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiFallbackReporterTest {
    @Test
    void theLogLineIsTheAlertAndNeverLeaksSecretsOrMessages() {
        Logger logger = Logger.getLogger(AiFallbackReporter.class.getName());
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(handler);
        try {
            AiFallbackReporter reporter = new AiFallbackReporter(new PetOperationalMetrics());
            UUID id = UUID.randomUUID();
            reporter.fallbackUsed(AiFallbackReporter.Operation.CHAT, "gpt-6-luna", "transport failed", Optional.of(id));
            reporter.fallbackFailed(AiFallbackReporter.Operation.EMBEDDING, "text-embedding-3-small", "both down", Optional.empty());
            reporter.moderationBypassed("MODERATION_UNAVAILABLE");
            assertEquals(3, records.size());
            for (LogRecord record : records) {
                assertTrue(record.getMessage().startsWith("ALERT AI_"), record.getMessage());
            }
            assertEquals(Level.WARNING, records.get(0).getLevel());
            assertEquals(Level.SEVERE, records.get(1).getLevel());
            assertEquals(Level.WARNING, records.get(2).getLevel());
            assertTrue(records.get(0).getMessage().startsWith("ALERT AI_FALLBACK_USED {"));
            assertTrue(records.get(0).getMessage().contains(id.toString()));
            assertTrue(records.get(1).getMessage().startsWith("ALERT AI_FALLBACK_FAILED {"));
            assertTrue(records.get(2).getMessage().startsWith("ALERT AI_MODERATION_BYPASSED {"));
            assertFalse(records.get(0).getMessage().toLowerCase().contains("sk-"));
        } finally {
            logger.removeHandler(handler);
        }
    }
}
