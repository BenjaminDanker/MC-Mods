package com.silver.witherfight.config;

/**
 * Minimal configuration for the Wither Fight helper. Only portal redirect settings remain because the
 * mod no longer manages global End reset behaviour.
 */
public final class WitherControlConfig {
    public static WitherControlConfig createDefault() {
        return new WitherControlConfig(
            true,
            "proxycommand \"wl portal vanilla2 silver\""
        );
    }

    private final boolean portalRedirectEnabled;
    private final String portalRedirectCommand;

    public WitherControlConfig(
        boolean portalRedirectEnabled,
        String portalRedirectCommand
    ) {
        this.portalRedirectEnabled = portalRedirectEnabled;
        this.portalRedirectCommand = portalRedirectCommand;
    }

    public boolean portalRedirectEnabled() {
        return portalRedirectEnabled;
    }

    public String portalRedirectCommand() {
        return portalRedirectCommand;
    }
}
