package com.silver.skyislands.config;

public final class SkyIslandsPortalConfig {
    public static final String DEFAULT_TARGET_SERVER = "vanilla";

    public static SkyIslandsPortalConfig createDefault() {
        return new SkyIslandsPortalConfig(true, DEFAULT_TARGET_SERVER);
    }

    private final boolean portalRedirectEnabled;
    private final String portalRedirectTargetServer;

    public SkyIslandsPortalConfig(
        boolean portalRedirectEnabled,
        String portalRedirectTargetServer
    ) {
        this.portalRedirectEnabled = portalRedirectEnabled;
        this.portalRedirectTargetServer = portalRedirectTargetServer;
    }

    public boolean portalRedirectEnabled() {
        return portalRedirectEnabled;
    }

    public String portalRedirectTargetServer() {
        return portalRedirectTargetServer;
    }
}