package com.silver.networkaccess;

import com.google.inject.Inject;
import com.silver.authorization.velocity.DatabaseSettings;
import com.silver.authorization.velocity.ProxyAuthorization;
import com.silver.networkaccess.plugin.Maintenance;
import com.silver.networkaccess.plugin.NetworkBans;
import com.silver.networkaccess.plugin.SecurityManager;
import com.silver.networkaccess.plugin.WhitelistCommand;
import com.silver.networkaccess.plugin.ServerWhitelistCheck;
import com.silver.networkaccess.plugin.WhitelistStore;
import com.silver.networkaccess.plugin.Whitelists;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;

/** Who may join the network: rate limit / greylist / whitelist, network bans and maintenance mode. */
@Plugin(id = "network-access", name = "Network Access", version = "0.1.0",
        dependencies = @Dependency(id = "network-authorization"))
public final class NetworkAccessVelocity {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;
    private HikariDataSource dataSource;

    @Inject
    public NetworkAccessVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        try {
            dataSource = openPool(DatabaseSettings.loadDefault());
            WhitelistStore whitelistStore = new WhitelistStore(dataDir, logger);
            whitelistStore.ensureFileAndLoad();
            SecurityManager securityManager = new SecurityManager(logger, whitelistStore, dataDir);
            proxy.getEventManager().register(this, securityManager);
            // network-wide bans (table net_bans, edited by the home page's admin side): read now, then every 10 s
            NetworkBans networkBans = new NetworkBans(dataSource, proxy, logger);
            networkBans.refresh();
            proxy.getEventManager().register(this, networkBans);
            proxy.getScheduler().buildTask(this, networkBans::refresh).delay(Duration.ofSeconds(10)).repeat(Duration.ofSeconds(10)).schedule();
            // maintenance mode (table net_maintenance, set from the home page's admin side): admins and test bots only
            Maintenance maintenance = new Maintenance(dataSource, proxy, this, logger, ProxyAuthorization::isAdminOn);
            maintenance.refresh();
            proxy.getEventManager().register(this, maintenance);
            proxy.getScheduler().buildTask(this, maintenance::refresh).delay(Duration.ofSeconds(5)).repeat(Duration.ofSeconds(5)).schedule();
            // the network whitelist is checked at login (SecurityManager), each server's own one when a player goes there
            Whitelists whitelists = new Whitelists(dataDir, whitelistStore, logger);
            proxy.getEventManager().register(this, new ServerWhitelistCheck(whitelists, logger));
            // the proxy's /whitelist replaces the vanilla one on the servers behind it
            proxy.getCommandManager().register("whitelist", new WhitelistCommand(proxy, whitelists, logger));
            logger.info("[NetworkAccess] Network Access ready");
        } catch (Exception ex) {
            logger.error("Failed to initialize Network Access", ex);
        }
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (dataSource != null) dataSource.close();
    }

    private static HikariDataSource openPool(DatabaseSettings settings) {
        HikariConfig config = new HikariConfig();
        // Shaded Velocity plugins do not reliably preserve JDBC's service-loader metadata; name the bundled driver.
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.user());
        config.setPassword(settings.password());
        config.setPoolName("NetworkAccess");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5_000);
        config.setValidationTimeout(2_000);
        config.setAutoCommit(true);
        return new HikariDataSource(config);
    }
}
