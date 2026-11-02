package com.silver.ondemand;

import com.silver.ondemand.admission.AdmissionClient;
import com.silver.ondemand.config.OnDemandSettings;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Static API of the on-demand-servers plugin for the other proxy plugins (player-routing, waiting-lobby). Every call is safe before the plugin has
 * started: queries answer "unknown/no", requests do nothing.
 */
public final class OnDemandServers {
    private static volatile OnDemandService service;

    private OnDemandServers() {
    }

    static void install(OnDemandService next) {
        service = next;
    }

    static void uninstall() {
        service = null;
    }

    /** True when the last ping (or a not-yet-contradicted answer) says the backend is up. Unknown servers are offline. */
    public static boolean isOnline(String server) {
        OnDemandService s = service;
        return s != null && s.isOnline(server);
    }

    /** Someone is waiting for this server: start it if it is managed and down, and keep it running. Call every second while waiting. */
    public static void request(String server) {
        OnDemandService s = service;
        if (s != null && server != null) {
            s.request(server);
        }
    }

    /** True when on-demand start/stop is enabled and manages this server. */
    public static boolean manages(String server) {
        OnDemandService s = service;
        return s != null && s.manages(server);
    }

    /** True while the last start attempt for this server was held back for lack of room on the host. */
    public static boolean waitingForRoom(String server) {
        OnDemandService s = service;
        return s != null && s.waitingForRoom(server);
    }

    /** Marks a backend offline until the next ping says otherwise (e.g. it just kicked a player). */
    public static void markOffline(String server) {
        OnDemandService s = service;
        if (s != null) {
            s.markOffline(server);
        }
    }

    /** True once the capacity service's join gate reports OPEN (fails closed: false before the plugin is up). */
    public static boolean isAdmissionOpen() {
        OnDemandService s = service;
        return s != null && s.admission().isOpen();
    }

    /** False while the server reports ticks slower than the capacity service's limit. Unknown means yes. */
    public static boolean isAccepting(String server) {
        OnDemandService s = service;
        return s == null || s.admission().isAccepting(server);
    }

    /** Ask the capacity service for room to start a server (blocking); null when it did not answer or the plugin is not up. */
    public static AdmissionClient.Permit reserveStart(String server) {
        OnDemandService s = service;
        return s == null ? null : s.admission().reserveStart(server);
    }

    /** True when the plugin is running and enabled. */
    public static boolean enabled() {
        OnDemandService s = service;
        return s != null && s.enabled();
    }

    /** The server that holds players in the waiting lobby (never pinged, never managed). Set by player-routing on every config load. */
    public static void setHoldingServer(Supplier<String> holdingServer) {
        OnDemandService s = service;
        if (s != null) {
            s.setHoldingServer(holdingServer);
        }
    }

    /** Servers players are waiting for right now (the offline waiters' preferred servers); asked on every 2 s tick. */
    public static void setWaitingServers(Supplier<Collection<String>> waitingServers) {
        OnDemandService s = service;
        if (s != null) {
            s.setWaitingServers(waitingServers);
        }
    }

    /** Sends the Wake-on-LAN packet for the machine that runs this server (player-routing knows the MAC addresses). */
    public static void setWakeHost(Consumer<String> wakeHost) {
        OnDemandService s = service;
        if (s != null) {
            s.setWakeHost(wakeHost);
        }
    }

    /** Refreshes the admission and health caches right now (called once after the holding server is known). */
    public static void prime() {
        OnDemandService s = service;
        if (s != null) {
            s.prime();
        }
    }

    /** Re-reads plugins/on-demand-servers/config.yml. */
    public static void reload() throws java.io.IOException {
        OnDemandService s = service;
        if (s != null) {
            s.reload();
        }
    }

    /** The settings currently in force. */
    public static OnDemandSettings settings() {
        OnDemandService s = service;
        return s == null ? OnDemandSettings.DISABLED : s.settings();
    }
}
