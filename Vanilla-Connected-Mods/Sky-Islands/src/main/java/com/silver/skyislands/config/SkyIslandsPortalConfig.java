package com.silver.skyislands.config;

public final class SkyIslandsPortalConfig {
    public static final String DEFAULT_TARGET_SERVER = "vanilla1";
    public static final String DEFAULT_PORTAL_REQUEST_SECRET = "silver";

    public static SkyIslandsPortalConfig createDefault() {
        return new SkyIslandsPortalConfig(true, DEFAULT_TARGET_SERVER, DEFAULT_PORTAL_REQUEST_SECRET);
    }

    private final boolean portalRedirectEnabled;
    private final String portalRedirectTargetServer;
    private final String portalRequestSecret;

    public SkyIslandsPortalConfig(
        boolean portalRedirectEnabled,
        String portalRedirectTargetServer,
        String portalRequestSecret
    ) {
        this.portalRedirectEnabled = portalRedirectEnabled;
        this.portalRedirectTargetServer = portalRedirectTargetServer;
        this.portalRequestSecret = portalRequestSecret;
    }

    public boolean portalRedirectEnabled() {
        return portalRedirectEnabled;
    }

    public String portalRedirectTargetServer() {
        return portalRedirectTargetServer;
    }

    public String portalRequestSecret() {
        return portalRequestSecret;
    }
}