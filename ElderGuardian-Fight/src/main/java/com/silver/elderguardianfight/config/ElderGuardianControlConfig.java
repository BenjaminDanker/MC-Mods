package com.silver.elderguardianfight.config;

/**
 * Minimal configuration for Elder Guardian Fight teleport handling.
 */
public final class ElderGuardianControlConfig {
    public static ElderGuardianControlConfig createDefault() {
        return new ElderGuardianControlConfig(
            true,
            "proxycommand \"wl portal elderguardian silver\""
        );
    }

    private final boolean portalRedirectEnabled;
    private final String portalRedirectCommand;

    public ElderGuardianControlConfig(boolean portalRedirectEnabled, String portalRedirectCommand) {
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
