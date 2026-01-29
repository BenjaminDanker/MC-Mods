package com.silver.enderfight.config;

/**
 * Immutable snapshot of the End control configuration. The {@link ConfigManager} handles loading and
 * persisting this structure from disk.
 */
public final class EndControlConfig {
    public static final int DEFAULT_CUSTOM_BREATH_TRACKING_USES = 5;
    public static final String DEFAULT_CUSTOM_BREATH_ID = "enderfight:special_dragon_breath";

    public static EndControlConfig createDefault() {
        return new EndControlConfig(
            24.0,
            60,
            "The End will reset soon!",
            "You have been returned to the Overworld. The End is resetting!",
            true,
            DEFAULT_CUSTOM_BREATH_TRACKING_USES,
            DEFAULT_CUSTOM_BREATH_ID,
            true,
            "proxycommand \"wl portal destion_server token\""
        );
    }

    private final double resetIntervalHours;
    private final int resetWarningSeconds;
    private final String warningMessage;
    private final String teleportMessage;
    private final boolean customBreathEnabled;
    private final int customBreathTrackingUsesDefault;
    private final String customBreathId;
    private final boolean portalRedirectEnabled;
    private final String portalRedirectCommand;

    public EndControlConfig(
        double resetIntervalHours,
        int resetWarningSeconds,
        String warningMessage,
        String teleportMessage,
        boolean customBreathEnabled,
        int customBreathTrackingUsesDefault,
        String customBreathId,
        boolean portalRedirectEnabled,
        String portalRedirectCommand
    ) {
        this.resetIntervalHours = resetIntervalHours;
        this.resetWarningSeconds = resetWarningSeconds;
        this.warningMessage = warningMessage;
        this.teleportMessage = teleportMessage;
        this.customBreathEnabled = customBreathEnabled;
        this.customBreathTrackingUsesDefault = customBreathTrackingUsesDefault;
        this.customBreathId = customBreathId;
        this.portalRedirectEnabled = portalRedirectEnabled;
        this.portalRedirectCommand = portalRedirectCommand;
    }

    public double resetIntervalHours() {
        return resetIntervalHours;
    }

    public int resetWarningSeconds() {
        return resetWarningSeconds;
    }

    public String warningMessage() {
        return warningMessage;
    }

    public String teleportMessage() {
        return teleportMessage;
    }

    public boolean customBreathEnabled() {
        return customBreathEnabled;
    }

    public int customBreathTrackingUsesDefault() {
        return customBreathTrackingUsesDefault;
    }

    public String customBreathId() {
        return customBreathId;
    }

    public boolean portalRedirectEnabled() {
        return portalRedirectEnabled;
    }

    public String portalRedirectCommand() {
        return portalRedirectCommand;
    }

    public long resetIntervalTicks() {
        return (long) (resetIntervalHours * 3600.0 * 20.0);
    }

    public long warningDelayTicks() {
        return resetWarningSeconds * 20L;
    }
}
