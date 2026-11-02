package com.silver.elderguardianfight.config;

/**
 * Minimal configuration for Elder Guardian Fight teleport handling.
 */
public final class ElderGuardianControlConfig {
    public static ElderGuardianControlConfig createDefault() {
        return new ElderGuardianControlConfig(
            true,
            "elderguardian",
            ""
        );
    }

    private final boolean portalRedirectEnabled;
    private final String portalRedirectTargetServer;
    private final String portalRedirectTargetPortal;

    public ElderGuardianControlConfig(boolean portalRedirectEnabled, String portalRedirectTargetServer, String portalRedirectTargetPortal) {
        this.portalRedirectEnabled = portalRedirectEnabled;
        this.portalRedirectTargetServer = portalRedirectTargetServer;
        this.portalRedirectTargetPortal = portalRedirectTargetPortal;
    }

    public boolean portalRedirectEnabled() {
        return portalRedirectEnabled;
    }

    public String portalRedirectTargetServer() {
        return portalRedirectTargetServer;
    }

    public String portalRedirectTargetPortal() {
        return portalRedirectTargetPortal;
    }
}
