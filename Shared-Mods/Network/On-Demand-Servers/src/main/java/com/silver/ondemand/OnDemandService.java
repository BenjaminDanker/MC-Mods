package com.silver.ondemand;

import com.silver.ondemand.admission.AdmissionClient;
import com.silver.ondemand.admission.BackendHealthMonitor;
import com.silver.ondemand.config.OnDemandConfig;
import com.silver.ondemand.config.OnDemandSettings;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;

/** The running pieces behind {@link OnDemandServers}: capacity client, health cache and the start/stop controller. */
final class OnDemandService {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;
    private final OnDemandConfig config;
    private final AdmissionClient admissionClient;
    private final BackendHealthMonitor backendHealthMonitor;
    private final OnDemandController onDemand;

    private volatile Supplier<String> holdingServer;
    private volatile Supplier<Collection<String>> waitingServers = List::of;
    private volatile Consumer<String> wakeHost = server -> { };

    OnDemandService(ProxyServer proxy, Logger logger, Path dataDir) throws IOException {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
        this.config = new OnDemandConfig(logger, dataDir);
        config.ensureDefaultConfig();
        OnDemandSettings settings = config.load();
        this.admissionClient = new AdmissionClient(capacityHost(settings));
        this.backendHealthMonitor = new BackendHealthMonitor(proxy, logger);
        this.onDemand = new OnDemandController(logger, server -> wakeHost.accept(server));
        onDemand.setStartPermit(server -> {
            AdmissionClient.Permit permit = admissionClient.reserveStart(server);
            // no answer at all: start anyway, as before the capacity service existed
            return permit == null || permit.granted() ? Optional.empty() : Optional.of(permit.reason());
        });
        onDemand.setPlayerCount(name -> proxy.getServer(name).map(server -> server.getPlayersConnected().size()).orElse(0));
        onDemand.configure(settings);
    }

    /** The capacity service runs on the machine that runs the servers: the host of the on-demand ssh target. */
    static String capacityHost(OnDemandSettings settings) {
        String target = settings == null || settings.sshTarget() == null ? "" : settings.sshTarget().trim();
        String host = target.substring(target.indexOf('@') + 1);
        return host.isBlank() ? "primary-ram.local" : host;
    }

    AdmissionClient admission() {
        return admissionClient;
    }

    boolean isOnline(String server) {
        return backendHealthMonitor.isOnline(server);
    }

    void markOffline(String server) {
        backendHealthMonitor.markOffline(server);
    }

    void request(String server) {
        onDemand.want(server);
    }

    boolean manages(String server) {
        return onDemand.manages(server);
    }

    boolean waitingForRoom(String server) {
        return onDemand.waitingForRoom(server);
    }

    boolean enabled() {
        return onDemand.enabled();
    }

    OnDemandSettings settings() {
        return onDemand.settings();
    }

    void setHoldingServer(Supplier<String> holdingServer) {
        this.holdingServer = holdingServer;
    }

    void setWaitingServers(Supplier<Collection<String>> waitingServers) {
        this.waitingServers = waitingServers;
    }

    void setWakeHost(Consumer<String> wakeHost) {
        this.wakeHost = wakeHost;
    }

    void reload() throws IOException {
        OnDemandSettings settings = config.load();
        onDemand.configure(settings);
        admissionClient.configure(capacityHost(settings));
    }

    /** Prime both caches immediately (the schedulers keep them fresh afterwards). */
    void prime() {
        admissionClient.refresh();
        refreshHealth();
    }

    void refreshAdmission() {
        admissionClient.refresh();
    }

    /** Ping every registered backend except the holding server; waits until the holding server is known. */
    void refreshHealth() {
        Supplier<String> holding = holdingServer;
        if (holding != null) {
            backendHealthMonitor.refresh(holding.get());
        }
    }

    /** What the controller needs every couple of seconds: who is up, who is on them, who is waiting for them. */
    void tick() {
        Supplier<String> holding = holdingServer;
        if (!onDemand.enabled() || holding == null) {
            return;
        }
        try {
            for (String waiting : waitingServers.get()) {
                request(waiting);
            }
            startRequestedByFile();
            Map<String, OnDemandPlanner.Observed> observed = new HashMap<>();
            for (var registered : proxy.getAllServers()) {
                String name = registered.getServerInfo().getName();
                if (name.equalsIgnoreCase(holding.get())) {
                    continue;
                }
                observed.put(name, new OnDemandPlanner.Observed(backendHealthMonitor.isOnline(name), registered.getPlayersConnected().size()));
            }
            onDemand.tick(observed);
        } catch (RuntimeException failure) {
            logger.warn("[OnDemand] tick failed: {}", failure.toString());
        }
    }

    /**
     * Lets an admin start a server without joining it: creating plugins/on-demand-servers/start-<server> asks for that server once and
     * the file is removed. It is how the start path is exercised without a Minecraft client.
     */
    private void startRequestedByFile() {
        try (var files = Files.list(dataDir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                if (name.startsWith("start-") && onDemand.manages(name.substring("start-".length()))) {
                    request(name.substring("start-".length()));
                    Files.deleteIfExists(file);
                    logger.info("[OnDemand] start of '{}' requested by file", name.substring("start-".length()));
                }
            }
        } catch (IOException failure) {
            logger.warn("[OnDemand] could not look for start requests: {}", failure.toString());
        }
    }

    void close() {
        onDemand.close();
    }
}
