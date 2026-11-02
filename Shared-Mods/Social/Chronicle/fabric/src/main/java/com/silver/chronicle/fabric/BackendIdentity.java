package com.silver.chronicle.fabric;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

record BackendIdentity(String backendId, Optional<byte[]> key) {
    static BackendIdentity load(Logger log) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("network-authorization.properties");
        try {
            Properties p = new Properties();
            try (var input = Files.newInputStream(path)) { p.load(input); }
            String id = p.getProperty("server_id", "").strip();
            if (!id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("invalid backend_id");
            byte[] key = Base64.getDecoder().decode(p.getProperty("backend_key_base64", "").strip());
            if (key.length < 32) throw new IllegalArgumentException("existing HMAC key is shorter than 256 bits");
            log.info("[Chronicle] Reusing Resonant Message backend identity for {}", id);
            return new BackendIdentity(id, Optional.of(key));
        } catch (IOException | IllegalArgumentException failure) {
            log.error("[Chronicle] Could not load the existing Resonant Message identity; Chronicle network actions are disabled: {}", failure.getMessage());
            return new BackendIdentity("", Optional.empty());
        }
    }
    BackendIdentity { key = key == null ? Optional.empty() : key.map(byte[]::clone); }
    @Override public Optional<byte[]> key() { return key.map(byte[]::clone); }
}
