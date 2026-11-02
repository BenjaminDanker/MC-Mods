package com.silver.portals.velocity;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Portal secrets, read from plugins/portals/config.yml (written by mc-net):
 * {@code backend_portal_request_secrets.<backend>} (or {@code "*"}) signs the portals:transfer requests.
 */
public record PortalsConfig(Map<String, String> backendRequestSecrets) {
    private static final String FILE = "config.yml";

    private static final String DEFAULT_YAML = """
# Backend -> proxy portal request authentication.
# Each backend server that can initiate a portal transfer MUST have a shared secret here.
# Key: the Velocity server name (e.g. "vanilla"). Value: a long random string.
# You can also use a wildcard entry "*" to apply the same secret to all backends.
backend_portal_request_secrets: {}
""";

    public PortalsConfig {
        backendRequestSecrets = Map.copyOf(backendRequestSecrets);
    }

    public static void ensureDefault(Path dataDir, Logger log) throws IOException {
        Files.createDirectories(dataDir);
        Path file = dataDir.resolve(FILE);
        if (!Files.exists(file)) {
            Files.writeString(file, DEFAULT_YAML, StandardCharsets.UTF_8);
            log.info("[Portals] Created default configuration at {}", file);
        }
    }

    public static PortalsConfig load(Path dataDir) throws IOException {
        Map<String, Object> root;
        try (Reader reader = Files.newBufferedReader(dataDir.resolve(FILE), StandardCharsets.UTF_8)) {
            root = new Yaml().load(reader);
        }
        if (root == null) {
            root = Map.of();
        }
        return new PortalsConfig(secrets(root.get("backend_portal_request_secrets")));
    }

    private static Map<String, String> secrets(Object value) {
        Map<String, String> result = new HashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                result.put(entry.getKey().toString(), Objects.toString(entry.getValue(), "").trim());
            }
        }
        return result;
    }
}
