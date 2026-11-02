package com.silver.networkaccess.plugin;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The network whitelist (checked at login) and the per-server ones (checked when a player moves to that server). Each
 * is enforced only while its switch file exists: whitelist-on for the network, servers/&lt;name&gt;/whitelist-on for a server.
 */
public final class Whitelists {
    private final Path dataDir;
    private final Logger logger;
    private final WhitelistStore network;
    private final Map<String, WhitelistStore> servers = new ConcurrentHashMap<>();

    public Whitelists(Path dataDir, WhitelistStore network, Logger logger) {
        this.dataDir = dataDir;
        this.network = network;
        this.logger = logger;
    }

    /** {@code server == null} means the network whitelist. */
    public WhitelistStore store(String server) throws IOException {
        if (server == null) {
            return network;
        }
        WhitelistStore store = servers.get(server);
        if (store == null) {
            store = WhitelistStore.atFile(serverDir(server).resolve("whitelist.json"), logger);
            store.ensureFileAndLoad();
            servers.put(server, store);
        }
        return store;
    }

    public Path switchFile(String server) {
        return server == null ? dataDir.resolve("whitelist-on") : serverDir(server).resolve("whitelist-on");
    }

    public boolean isOn(String server) {
        return Files.exists(switchFile(server));
    }

    /** Whether the player may enter this server under its own whitelist (the network one is checked at login). */
    public boolean allowedOn(String server, UUID player) {
        if (!isOn(server)) {
            return true;
        }
        try {
            return store(server).isWhitelisted(player);
        } catch (IOException failure) {
            logger.error("[NetworkAccess] Could not read the {} whitelist; keeping the player out", server, failure);
            return false;
        }
    }

    private Path serverDir(String server) {
        return dataDir.resolve("servers").resolve(server.toLowerCase(java.util.Locale.ROOT));
    }
}
