package com.silver.viewextend;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ViewExtendConfigTest {
    @Test void v2MigrationPreservesCustomDistancesAndBudgets() {
        Properties old = new Properties();
        old.setProperty("config-version", "2");
        old.setProperty("unsimulated-view-distance", "96");
        old.setProperty("global-packet-template-cache-max-mib", "1024");
        old.setProperty("max-nbt-reads-per-tick", "333");
        ViewExtendConfig.migrate(old);
        var config = ViewExtendConfig.fromProperties(old);
        assertEquals(96, config.unsimulatedViewDistance());
        assertEquals(256, config.globalPacketTemplateCacheMaxMiB());
        assertEquals(333, config.maxNbtReadsPerTick());
    }
    @Test void defaultsReach127AndDoNotStripTerrain() {
        var config = ViewExtendConfig.fromProperties(new Properties());
        assertEquals(127, ViewDistancePolicy.effective(10, config.unsimulatedViewDistance(), 127, config.clientReportedViewDistanceHardCap()));
        assertTrue(config.lod1StartDistance() > 127);
        assertEquals(64, config.maxChunksPerPlayerPerTick());
        assertEquals(256L * 1024 * 1024, config.globalPacketTemplateCacheMaxBytes());
    }
    @Test void legacyMigrationRemovesSlowDefaultsAndUnusedKnobs() {
        Properties old = new Properties();
        old.setProperty("max-chunks-per-player-per-tick", "16");
        old.setProperty("client-reported-view-distance-hard-cap", "96");
        old.setProperty("tick-interval", "2");
        old.setProperty("payload-cache-max-entries-per-player", "1024");
        ViewExtendConfig.migrate(old);
        var config = ViewExtendConfig.fromProperties(old);
        assertEquals(64, config.maxChunksPerPlayerPerTick());
        assertEquals(127, config.clientReportedViewDistanceHardCap());
        assertFalse(old.containsKey("tick-interval"));
        assertFalse(old.containsKey("payload-cache-max-entries-per-player"));
    }
    @Test void migrationPreservesExplicitDisable() {
        Properties old = new Properties();
        old.setProperty("unsimulated-view-distance", "0");
        ViewExtendConfig.migrate(old);
        assertEquals(0, ViewExtendConfig.fromProperties(old).unsimulatedViewDistance());
    }
    @Test void signedClientDistanceNeverAllocates255Radius() {
        assertEquals(127, ViewDistancePolicy.effective(10, 127, -1, 127));
        assertEquals(32, ViewDistancePolicy.effective(10, 127, 32, 127));
        assertEquals(10, ViewDistancePolicy.effective(10, 0, 127, 127));
        assertEquals(127, ViewDistancePolicy.effective(10, Integer.MAX_VALUE, Integer.MAX_VALUE, 127));
    }
}
