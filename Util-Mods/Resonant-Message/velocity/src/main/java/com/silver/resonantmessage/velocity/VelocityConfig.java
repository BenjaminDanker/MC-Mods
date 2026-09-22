package com.silver.resonantmessage.velocity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;

final class VelocityConfig {
    static final Set<String> REQUIRED_BACKENDS = Set.of(
            "waiting_lobby", "vanilla1", "sky-island", "ocean", "desert", "cave", "magic");

    private VelocityConfig() {}

    static Map<String, byte[]> loadBackendKeys(Path dataDirectory, Logger logger) {
        Path pluginsDirectory = dataDirectory.getParent();
        if (pluginsDirectory == null) {
            logger.error("[ResonantMessage] Could not locate the existing Network Authorization key file; cross-world chat is disabled");
            return Map.of();
        }
        Path file = pluginsDirectory.resolve("wakeuplobby")
                .resolve("authorization-backend-keys.properties");
        try {
            if (!Files.isRegularFile(file)) {
                logger.error("[ResonantMessage] Existing Network Authorization backend keys are missing; cross-world chat is disabled");
                return Map.of();
            }

            Properties properties = new Properties();
            try (var input = Files.newInputStream(file)) { properties.load(input); }
            Map<String, byte[]> parsed = new HashMap<>();
            for (String id : REQUIRED_BACKENDS) {
                String encoded = properties.getProperty(id, "").trim();
                if (encoded.isEmpty()) {
                    logger.error("[ResonantMessage] Missing HMAC key for backend {}; its messages are disabled", id);
                    continue;
                }
                try {
                    byte[] key = Base64.getDecoder().decode(encoded);
                    if (key.length < 32) throw new IllegalArgumentException("key shorter than 32 bytes");
                    parsed.put(id, key);
                } catch (IllegalArgumentException invalid) {
                    logger.error("[ResonantMessage] Invalid HMAC key for backend {}; its messages are disabled", id);
                }
            }
            Set<String> duplicates = new HashSet<>();
            var entries = parsed.entrySet().stream().toList();
            for (int left = 0; left < entries.size(); left++) {
                for (int right = left + 1; right < entries.size(); right++) {
                    if (Arrays.equals(entries.get(left).getValue(), entries.get(right).getValue())) {
                        duplicates.add(entries.get(left).getKey());
                        duplicates.add(entries.get(right).getKey());
                    }
                }
            }
            duplicates.forEach(id -> {
                parsed.remove(id);
                logger.error("[ResonantMessage] Duplicate backend key disables {}", id);
            });
            Map<String, byte[]> result = new HashMap<>();
            parsed.forEach((id, key) -> result.put(id, key.clone()));
            return Map.copyOf(result);
        } catch (IOException failure) {
            logger.error("[ResonantMessage] Could not read existing Network Authorization backend keys; cross-world messages are disabled", failure);
            return Map.of();
        }
    }
}
