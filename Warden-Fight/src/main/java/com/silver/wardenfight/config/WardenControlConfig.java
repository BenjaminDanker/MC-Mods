package com.silver.wardenfight.config;

/**
 * Minimal configuration for Warden Fight teleport handling.
 */
public final class WardenControlConfig {
    public static WardenControlConfig createDefault() {
        return new WardenControlConfig(
            true,
            10,
            "proxycommand \"wl portal warden silver\""
        );
    }

    private final boolean portalRedirectEnabled;
    private final int portalRedirectRange;
    private final String portalRedirectCommand;

    public WardenControlConfig(boolean portalRedirectEnabled, int portalRedirectRange, String portalRedirectCommand) {
        this.portalRedirectEnabled = portalRedirectEnabled;
        this.portalRedirectRange = portalRedirectRange;
        this.portalRedirectCommand = portalRedirectCommand;
    }

    public boolean portalRedirectEnabled() {
        return portalRedirectEnabled;
    }

    public int portalRedirectRange() {
        return portalRedirectRange;
    }

    public String portalRedirectCommand() {
        return portalRedirectCommand;
    }
}
