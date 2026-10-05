package com.silver.aipets.service.devplayer;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DevPlayerBoundaryTest {
    private static Map<String,String> env() {
        Map<String,String> env=new HashMap<>();
        env.put("DEV_PLAYER_DB_JDBC_URL","jdbc:mariadb://127.0.0.1:3306/pet_companion_dev_player");
        env.put("DEV_PLAYER_DB_USER","pet_dev_player");
        env.put("DEV_PLAYER_DB_PASSWORD","synthetic-test-password");
        env.put("DEV_PLAYER_QDRANT_URL","http://127.0.0.1:6333");
        env.put("DEV_PLAYER_QDRANT_COLLECTION","pet_companion_dev_player_memories");
        env.put("PET_OPENAI_API_KEY","synthetic-test-key");
        return env;
    }
    @Test void namedSessionsAreStableDistinctAndCannotEscapeStateDirectory() {
        var first=DevPlayerIdentity.forSession("conversation-20261001");
        assertEquals(first,DevPlayerIdentity.forSession("conversation-20261001"));
        assertNotEquals(first.pet(),DevPlayerIdentity.forSession("default").pet());
        assertNotEquals(first.pet(),DevPlayerIdentity.forSession("other").pet());
        assertThrows(IllegalArgumentException.class,()->DevPlayerIdentity.forSession("../default"));
    }
    @Test void refusesProductionDatabase() {
        var values=env(); values.put("DEV_PLAYER_DB_JDBC_URL","jdbc:mariadb://127.0.0.1:3306/minecraft");
        assertThrows(IllegalArgumentException.class,()->DevPlayerEnvironment.from(values));
    }
    @Test void refusesProductionCollectionAndSqlAccount() {
        var values=env(); values.put("DEV_PLAYER_QDRANT_COLLECTION","aipets_memories");
        assertThrows(IllegalArgumentException.class,()->DevPlayerEnvironment.from(values));
        var rejected=env(); rejected.put("DEV_PLAYER_DB_USER","pet-companion");
        assertThrows(IllegalArgumentException.class,()->DevPlayerEnvironment.from(rejected));
    }
    @Test void refusesRemoteStorage() {
        var values=env(); values.put("DEV_PLAYER_QDRANT_URL","http://192.168.1.200:6333");
        assertThrows(IllegalArgumentException.class,()->DevPlayerEnvironment.from(values));
    }
    @Test void clockPersistsAndAdvancesWithoutWaiting() throws Exception {
        var path=Files.createTempDirectory("dev-clock-").resolve("clock.txt");
        var first=new DevPlayerClock(path);
        var before=first.instant(); first.advance(Duration.ofDays(4));
        assertEquals(before.plus(Duration.ofDays(4)),new DevPlayerClock(path).instant());
        assertThrows(IllegalArgumentException.class,()->first.advance(Duration.ZERO));
    }
    @Test void redactsCredentialsInPromptsAndTraces() {
        String output=DevPlayerRedactor.redact("Bearer abcdefghijklmnop password=secret123 sk-proj-abcdefghijklmnop");
        assertFalse(output.contains("secret123"));
        assertFalse(output.contains("abcdefghijklmnop"));
        assertTrue(output.contains("[REDACTED]"));
    }
}
