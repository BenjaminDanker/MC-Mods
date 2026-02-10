package com.silver.elderguardianfight.config;

/**
 * Minimal configuration for Elder Guardian Fight teleport handling.
 */
public final class ElderGuardianControlConfig {
    public static ElderGuardianControlConfig createDefault() {
        return new ElderGuardianControlConfig(
            true,
            "elderguardian",
            "",
            "silver"
        );
    }

    private final boolean portalRedirectEnabled;
    private final String portalRedirectTargetServer;
    private final String portalRedirectTargetPortal;
    private final String portalRequestSecret;

    public ElderGuardianControlConfig(boolean portalRedirectEnabled, String portalRedirectTargetServer, String portalRedirectTargetPortal, String portalRequestSecret) {
        this.portalRedirectEnabled = portalRedirectEnabled;
        this.portalRedirectTargetServer = portalRedirectTargetServer;
        this.portalRedirectTargetPortal = portalRedirectTargetPortal;
        this.portalRequestSecret = portalRequestSecret;
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

    public String portalRequestSecret() {
        return portalRequestSecret;
    }
}
