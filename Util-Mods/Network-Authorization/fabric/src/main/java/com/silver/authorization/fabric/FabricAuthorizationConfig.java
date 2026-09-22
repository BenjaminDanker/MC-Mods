package com.silver.authorization.fabric;

import com.silver.authorization.ServerId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;

record FabricAuthorizationConfig(Optional<ServerId> serverId, Optional<byte[]> backendKey,
                                 String catalogHost, int catalogPort) {
    static FabricAuthorizationConfig load(Path configDir, Logger log) {
        Path path = configDir.resolve("network-authorization.properties");
        try {
            Files.createDirectories(configDir);
            if (Files.notExists(path)) {
                try {
                    Files.createFile(path, PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")));
                } catch (UnsupportedOperationException unsupported) {
                    Files.createFile(path);
                }
                Files.writeString(path, "# Explicit canonical Velocity server ID; never infer this from a directory name.\n"
                        + "server_id=\n# Unique random Base64 key, at least 32 decoded bytes; never reuse portal signing keys.\n"
                        + "backend_key_base64=\n# Velocity command-catalog listener (HMAC-authenticated).\n"
                        + "catalog_host=\ncatalog_port=25576\n", StandardCharsets.UTF_8);
                log.warn("[NetworkAuthorization] Created {}; central checks remain denied until configured", path.getFileName());
            }
            try { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------")); }
            catch (UnsupportedOperationException unsupported) { /* Windows ACLs are managed by the host. */ }
            Properties properties = new Properties();
            try (var input = Files.newInputStream(path)) { properties.load(input); }
            Optional<ServerId> server = Optional.empty();
            String id = properties.getProperty("server_id", "").trim();
            if (!id.isEmpty()) {
                try { server = Optional.of(ServerId.of(id)); }
                catch (IllegalArgumentException invalid) { log.error("[NetworkAuthorization] Invalid server_id in {}", path.getFileName()); }
            }
            Optional<byte[]> key = Optional.empty();
            String encoded = properties.getProperty("backend_key_base64", "").trim();
            if (!encoded.isEmpty()) {
                try {
                    byte[] decoded = Base64.getDecoder().decode(encoded);
                    if (decoded.length < 32) throw new IllegalArgumentException("key shorter than 32 bytes");
                    key = Optional.of(decoded);
                } catch (IllegalArgumentException invalid) {
                    log.error("[NetworkAuthorization] Invalid backend key; snapshots will not be accepted or requested");
                }
            }
            if (server.isEmpty()) log.warn("[NetworkAuthorization] No canonical backend server ID configured");
            if (key.isEmpty()) log.warn("[NetworkAuthorization] No backend-specific HMAC key configured");
            String catalogHost = properties.getProperty("catalog_host", "").trim();
            int catalogPort;
            try {
                catalogPort = Integer.parseInt(properties.getProperty("catalog_port", "25576").trim());
                if (catalogPort < 1 || catalogPort > 65535) throw new NumberFormatException("outside 1..65535");
            } catch (NumberFormatException invalid) {
                catalogPort = 25576;
                log.error("[NetworkAuthorization] Invalid catalog_port; using default 25576");
            }
            if (catalogHost.isEmpty()) log.warn("[CommandPolicy] catalog_host is unset; startup catalog sync is disabled");
            return new FabricAuthorizationConfig(server, key, catalogHost, catalogPort);
        } catch (IOException failure) {
            log.error("[NetworkAuthorization] Could not read backend authorization config; central checks fail closed", failure);
            return new FabricAuthorizationConfig(Optional.empty(), Optional.empty(), "", 25576);
        }
    }

    FabricAuthorizationConfig {
        serverId = serverId == null ? Optional.empty() : serverId;
        backendKey = backendKey == null ? Optional.empty() : backendKey.map(byte[]::clone);
        catalogHost = catalogHost == null ? "" : catalogHost.strip();
        if (catalogPort < 1 || catalogPort > 65535) throw new IllegalArgumentException("catalogPort must be 1..65535");
    }

    @Override public Optional<byte[]> backendKey() { return backendKey.map(byte[]::clone); }
}
