package com.silver.aipets.service.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.silver.aipets.common.domain.PetSpecies;
import java.util.Set;

class PetServiceConfigTest {
    private static final String TOKEN = "service-token-0123456789-0123456789-ab";

    @Test
    void validatesBoundsAndNeverRendersSecrets() {
        Map<String, String> valid = validEnvironment();
        PetServiceConfig config = PetServiceConfig.fromEnvironment(valid);

        assertEquals("127.0.0.1", config.bindAddress());
        assertEquals(8787, config.port());
        assertEquals(4, config.workerThreads());
        assertEquals(4, config.poolMaximumSize());
        assertEquals(Set.of(PetSpecies.CAT, PetSpecies.DOG), config.allowedSpecies());
        assertEquals(7, config.rawTextRetentionDays());
        assertEquals(true, config.aiEnabled());
        assertFalse(config.qdrantEnabled());
        assertFalse(config.toString().contains(TOKEN));
        assertFalse(config.toString().contains("database-secret"));

        Map<String, String> embeddedCredentials = new HashMap<>(valid);
        embeddedCredentials.put(PetServiceConfig.JDBC_URL_ENV,
                "jdbc:mariadb://user:password@127.0.0.1:3306/minecraft");
        assertThrows(
                IllegalArgumentException.class,
                () -> PetServiceConfig.fromEnvironment(embeddedCredentials));

        Map<String, String> oversizedPool = new HashMap<>(valid);
        oversizedPool.put("PET_DB_POOL_MAXIMUM", "17");
        assertThrows(
                IllegalArgumentException.class,
                () -> PetServiceConfig.fromEnvironment(oversizedPool));

        Map<String, String> missingToken = new HashMap<>(valid);
        missingToken.remove(PetServiceConfig.TOKEN_ENV);
        assertThrows(
                IllegalArgumentException.class,
                () -> PetServiceConfig.fromEnvironment(missingToken));

        Map<String, String> catOnly = new HashMap<>(valid);
        catOnly.put(PetServiceConfig.ALLOWED_SPECIES_ENV, "minecraft:cat");
        assertEquals(Set.of(PetSpecies.CAT),
                PetServiceConfig.fromEnvironment(catOnly).allowedSpecies());

        Map<String, String> arbitraryMob = new HashMap<>(valid);
        arbitraryMob.put(PetServiceConfig.ALLOWED_SPECIES_ENV, "minecraft:cat,minecraft:allay");
        assertThrows(
                IllegalArgumentException.class,
                () -> PetServiceConfig.fromEnvironment(arbitraryMob));

        Map<String, String> customRetention = new HashMap<>(valid);
        customRetention.put(PetServiceConfig.RAW_TEXT_RETENTION_DAYS_ENV, "14");
        assertEquals(14, PetServiceConfig.fromEnvironment(customRetention).rawTextRetentionDays());
        customRetention.put(PetServiceConfig.RAW_TEXT_RETENTION_DAYS_ENV, "31");
        assertThrows(IllegalArgumentException.class,
                () -> PetServiceConfig.fromEnvironment(customRetention));

        Map<String, String> aiDisabled = new HashMap<>(valid);
        aiDisabled.put(PetServiceConfig.AI_ENABLED_ENV, "false");
        assertFalse(PetServiceConfig.fromEnvironment(aiDisabled).aiEnabled());
        aiDisabled.put(PetServiceConfig.AI_ENABLED_ENV, "sometimes");
        assertThrows(IllegalArgumentException.class,
                () -> PetServiceConfig.fromEnvironment(aiDisabled));

        for (String wildcard : java.util.List.of(
                "0.0.0.0", "::", "[::]", "0:0:0:0:0:0:0:0")) {
            Map<String, String> publicBind = new HashMap<>(valid);
            publicBind.put("PET_SERVICE_BIND_ADDRESS", wildcard);
            assertThrows(IllegalArgumentException.class,
                    () -> PetServiceConfig.fromEnvironment(publicBind));
        }

        Map<String, String> stripeEnabled = new HashMap<>(valid);
        stripeEnabled.put(PetServiceConfig.STRIPE_ENABLED_ENV, "true");
        stripeEnabled.put(PetServiceConfig.STRIPE_PRICE_ID_ENV, "price_pet_monthly");
        stripeEnabled.put(PetServiceConfig.STRIPE_SECRET_KEY_ENV,
                "sk_test_checkout_secret_value");
        stripeEnabled.put(PetServiceConfig.STRIPE_WEBHOOK_SECRET_ENV,
                "whsec_test_signing_secret");
        stripeEnabled.put(PetServiceConfig.PUBLIC_BASE_URL_ENV,
                "https://pets.example.test");
        stripeEnabled.put(PetServiceConfig.ACCOUNT_LINK_PEPPER_ENV,
                "test-account-link-pepper-at-least-thirty-two-characters");
        PetServiceConfig stripeConfig = PetServiceConfig.fromEnvironment(stripeEnabled);
        assertEquals(true, stripeConfig.stripeEnabled());
        assertEquals("price_pet_monthly", stripeConfig.stripePriceId());
        assertEquals("https://pets.example.test", stripeConfig.publicBaseUri().toString());
        assertFalse(stripeConfig.toString().contains("sk_test_checkout_secret_value"));
        assertFalse(stripeConfig.toString().contains("whsec_test_signing_secret"));
        assertFalse(stripeConfig.toString().contains("test-account-link-pepper"));

        Map<String, String> qdrant = new HashMap<>(valid);
        qdrant.put(PetServiceConfig.QDRANT_ENABLED_ENV, "true");
        qdrant.put(PetServiceConfig.QDRANT_URL_ENV, "http://127.0.0.1:6333/");
        qdrant.put(PetServiceConfig.QDRANT_COLLECTION_ENV, "aipets_test");
        qdrant.put(PetServiceConfig.QDRANT_DIMENSION_ENV, "1536");
        qdrant.put(PetServiceConfig.QDRANT_API_KEY_ENV, "qdrant-test-secret");
        qdrant.put(PetServiceConfig.OPENAI_API_KEY_ENV, "openai-test-secret");
        PetServiceConfig qdrantConfig = PetServiceConfig.fromEnvironment(qdrant);
        assertEquals(true, qdrantConfig.qdrantEnabled());
        assertEquals("http://127.0.0.1:6333", qdrantConfig.qdrantUri().toString());
        assertEquals("aipets_test", qdrantConfig.qdrantCollection());
        assertEquals(1536, qdrantConfig.qdrantDimension());
        assertFalse(qdrantConfig.toString().contains("qdrant-test-secret"));
        assertFalse(qdrantConfig.toString().contains("openai-test-secret"));
        qdrant.put(PetServiceConfig.QDRANT_URL_ENV, "http://user:pass@127.0.0.1:6333");
        assertThrows(IllegalArgumentException.class,
                () -> PetServiceConfig.fromEnvironment(qdrant));
    }

    private static Map<String, String> validEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put(PetServiceConfig.TOKEN_ENV, TOKEN);
        environment.put(PetServiceConfig.JDBC_URL_ENV,
                "jdbc:mariadb://127.0.0.1:3306/minecraft");
        environment.put(PetServiceConfig.DB_USER_ENV, "pet_service");
        environment.put(PetServiceConfig.DB_PASSWORD_ENV, "database-secret");
        return environment;
    }
}
