package com.silver.witherfight.config;

/**
 * Minimal configuration for the Wither Fight helper. Only portal redirect settings remain because the
 * mod no longer manages global End reset behaviour.
 */
public final class WitherControlConfig {
    public static WitherControlConfig createDefault() {
        return new WitherControlConfig(
            true,
            "desert",
            "",
            "silver"
        );
    }

    private final boolean portalRedirectEnabled;
    private final String portalRedirectTargetServer;
    private final String portalRedirectTargetPortal;
    private final String portalRequestSecret;

    public WitherControlConfig(
        boolean portalRedirectEnabled,
        String portalRedirectTargetServer,
        String portalRedirectTargetPortal,
        String portalRequestSecret
    ) {
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
