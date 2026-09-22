package com.silver.resonantmessage.fabric;

import com.silver.resonantmessage.common.ResonanceProtocol;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;

record BackendConfig(String backendId, Optional<byte[]> key) {
    static BackendConfig load(Path configDirectory, Logger logger) {
        Path path = configDirectory.resolve("network-authorization.properties");
        try {
            if (!Files.isRegularFile(path)) {
                logger.error("[ResonantMessage] Network Authorization config is missing; cross-world chat is disabled");
                return new BackendConfig("", Optional.empty());
            }

            Properties properties = new Properties();
            try (var input = Files.newInputStream(path)) { properties.load(input); }
            String id = properties.getProperty("server_id", "").trim();
            if (!ResonanceProtocol.validBackendId(id)) {
                logger.error("[ResonantMessage] Invalid Network Authorization server_id; cross-world chat is disabled");
                return new BackendConfig("", Optional.empty());
            }
            String encoded = properties.getProperty("backend_key_base64", "").trim();
            if (encoded.isEmpty()) {
                logger.error("[ResonantMessage] Network Authorization backend key is missing; cross-world chat is disabled");
                return new BackendConfig(id, Optional.empty());
            }
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length < 32) throw new IllegalArgumentException("key shorter than 32 bytes");
            return new BackendConfig(id, Optional.of(decoded));
        } catch (IOException | IllegalArgumentException failure) {
            logger.error("[ResonantMessage] Could not load valid Network Authorization backend credentials; cross-world chat is disabled: {}",
                    failure.toString());
            return new BackendConfig("", Optional.empty());
        }
    }

    BackendConfig {
        key = key == null ? Optional.empty() : key.map(byte[]::clone);
    }

    @Override public Optional<byte[]> key() { return key.map(byte[]::clone); }
}
