package com.silver.ondemand;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;

/** Starts backend servers when a player needs them and stops them when they are empty; other plugins use {@link OnDemandServers}. */
@Plugin(id = "on-demand-servers", name = "On-Demand Servers", version = "0.1.0")
public final class OnDemandServersPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;
    private OnDemandService service;

    @Inject
    public OnDemandServersPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        try {
            service = new OnDemandService(proxy, logger, dataDir);
            OnDemandServers.install(service);

            proxy.getScheduler().buildTask(this, service::refreshAdmission)
                    .repeat(Duration.ofSeconds(1))
                    .schedule();
            proxy.getScheduler().buildTask(this, service::refreshHealth)
                    .repeat(Duration.ofSeconds(2))
                    .schedule();
            proxy.getScheduler().buildTask(this, service::tick)
                    .delay(Duration.ofSeconds(2))
                    .repeat(Duration.ofSeconds(2))
                    .schedule();
            logger.info("[OnDemand] On-Demand Servers ready");
        } catch (Exception ex) {
            logger.error("Failed to initialize On-Demand Servers", ex);
        }
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        OnDemandServers.uninstall();
        if (service != null) {
            service.close();
        }
    }
}
