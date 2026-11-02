package com.silver.ondemand.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

/** Reads plugins/on-demand-servers/config.yml (the {@code on_demand} section, same keys as it had in wakeuplobby's config.yml). */
public final class OnDemandConfig {
    private static final String CONFIG_FILE_NAME = "config.yml";

    private final Logger log;
    private final Path dataDir;

    public OnDemandConfig(Logger log, Path dataDir) {
        this.log = Objects.requireNonNull(log, "log");
        this.dataDir = Objects.requireNonNull(dataDir, "dataDir");
    }

    public Path configPath() {
        return dataDir.resolve(CONFIG_FILE_NAME);
    }

    /** Writes a default (disabled) config when there is none. */
    public void ensureDefaultConfig() throws IOException {
        Files.createDirectories(dataDir);
        Path cfg = configPath();
        if (Files.exists(cfg)) {
            return;
        }
        String defaultYaml = """
# Start backend servers when a player needs them and stop them when they have been empty for a while.
# enabled: false (or no section) = the proxy only sends the Wake-on-LAN packet, as before.
# ssh_target also names the machine that runs the capacity service (port 25580).
on_demand:
    enabled: false
    ssh_target: "user@host"
    ssh_key: "/path/to/key"
    idle_minutes: 10
    servers: []
    keep_running: []   # optional: started whenever down, never idle-stopped
""";
        Files.writeString(cfg, defaultYaml, StandardCharsets.UTF_8);
        log.info("[OnDemand] Created default configuration at {}", cfg);
    }

    public OnDemandSettings load() throws IOException {
        Object root;
        try (Reader reader = Files.newBufferedReader(configPath(), StandardCharsets.UTF_8)) {
            root = new Yaml().load(reader);
        }
        return root instanceof Map<?, ?> map ? OnDemandSettings.parse(map.get("on_demand")) : OnDemandSettings.DISABLED;
    }
}
