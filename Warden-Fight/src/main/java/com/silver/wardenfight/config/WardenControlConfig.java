package com.silver.wardenfight.config;

/**
 * Minimal configuration for Warden Fight teleport handling.
 */
public final class WardenControlConfig {
    public static WardenControlConfig createDefault() {
        return new WardenControlConfig(
            true,
            10,
            "warden",
            "",
            "silver"
        );
    }

    private final boolean portalRedirectEnabled;
    private final int portalRedirectRange;
    private final String portalRedirectTargetServer;
    private final String portalRedirectTargetPortal;
    private final String portalRequestSecret;

    public WardenControlConfig(boolean portalRedirectEnabled, int portalRedirectRange, String portalRedirectTargetServer, String portalRedirectTargetPortal, String portalRequestSecret) {
        this.portalRedirectEnabled = portalRedirectEnabled;
        this.portalRedirectRange = portalRedirectRange;
        this.portalRedirectTargetServer = portalRedirectTargetServer;
        this.portalRedirectTargetPortal = portalRedirectTargetPortal;
        this.portalRequestSecret = portalRequestSecret;
    }

    public boolean portalRedirectEnabled() {
        return portalRedirectEnabled;
    }

    public int portalRedirectRange() {
        return portalRedirectRange;
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
