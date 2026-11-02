package com.silver.ondemand;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.silver.ondemand.config.OnDemandSettings;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CapacityHostTest {
    private static OnDemandSettings target(String sshTarget) {
        return new OnDemandSettings(true, sshTarget, "/k", 10, Set.of("magic"));
    }

    @Test
    void theHostIsTheSshTargetsHost() {
        assertEquals("primary-ram.local", OnDemandService.capacityHost(target("silver@primary-ram.local")));
        assertEquals("10.0.0.5", OnDemandService.capacityHost(target("root@10.0.0.5")));
        assertEquals("host-only", OnDemandService.capacityHost(target("host-only")));
        assertEquals("box.lan", OnDemandService.capacityHost(target("  me@box.lan  ")));
    }

    @Test
    void aMissingTargetFallsBackToThePrimaryHost() {
        assertEquals("primary-ram.local", OnDemandService.capacityHost(target("")));
        assertEquals("primary-ram.local", OnDemandService.capacityHost(target("   ")));
        assertEquals("primary-ram.local", OnDemandService.capacityHost(target(null)));
        assertEquals("primary-ram.local", OnDemandService.capacityHost(null));
        assertEquals("primary-ram.local", OnDemandService.capacityHost(OnDemandSettings.DISABLED));
        assertEquals("primary-ram.local", OnDemandService.capacityHost(target("silver@")));
    }
}
