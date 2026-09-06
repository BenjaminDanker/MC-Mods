package com.silver.aipets.fabric.config;

import com.silver.aipets.common.domain.BackendId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PetServiceClientConfigTest {
    private static final String TOKEN = "authority-token-0123456789-0123456789";
    private static final String SIGNING = "compass-signing-0123456789-0123456789";

    @Test
    void loadsSeparateSecretsFriendlyNamesAndRedactsRendering(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve(PetServiceClientConfig.FILE_NAME), """
                authority.enabled=true
                authority.backend-id=ocean
                authority.base-url=http://127.0.0.1:8787
                authority.token-environment=TEST_AUTHORITY_TOKEN
                authority.compass-signing-environment=TEST_COMPASS_SIGNING
                authority.backend-friendly-names=ocean=Ocean;survival=Survival Realm
                authority.connect-timeout-ms=1200
                authority.request-timeout-ms=2400
                """);
        Map<String, String> environment = new HashMap<>();
        environment.put("TEST_AUTHORITY_TOKEN", TOKEN);
        environment.put("TEST_COMPASS_SIGNING", SIGNING);

        PetServiceClientConfig config = PetServiceClientConfig.load(directory, environment)
                .orElseThrow();
        assertEquals("Ocean", config.friendlyBackendName(new BackendId("ocean")));
        assertEquals("Survival Realm", config.friendlyBackendName(new BackendId("survival")));
        assertEquals("unknown", config.friendlyBackendName(new BackendId("unknown")));
        assertFalse(config.toString().contains(TOKEN));
        assertFalse(config.toString().contains(SIGNING));

        environment.remove("TEST_COMPASS_SIGNING");
        assertThrows(
                IllegalArgumentException.class,
                () -> PetServiceClientConfig.load(directory, environment));
    }
}
