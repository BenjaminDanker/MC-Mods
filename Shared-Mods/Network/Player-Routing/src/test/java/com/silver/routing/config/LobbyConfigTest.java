package com.silver.routing.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LobbyConfig")
class LobbyConfigTest {

    @Test
    @DisplayName("constructor stores all values correctly")
    void constructorStoresValues() {
        LobbyConfig config = new LobbyConfig(
                "192.168.1.255",
                "lobby",
                300,
                30,
                Map.of("server1", "00:11:22:33:44:55"),
                Map.of("group1", List.of("server1", "server2")),
                List.of("vanilla", "magic"),
                Map.of()
        );

        assertEquals("192.168.1.255", config.broadcastIp());
        assertEquals("lobby", config.holdingServer());
        assertEquals(300, config.graceSec());
        assertEquals(30, config.pingEverySec());
    }

    @Test
    @DisplayName("broadcastIp is required and cannot be null")
    void broadcastIpRequired() {
        assertThrows(NullPointerException.class, () ->
                new LobbyConfig(
                        null,
                        "lobby",
                        300,
                        30,
                        Map.of(),
                        Map.of(),
                        List.of("vanilla"),
                        Map.of()
        )
        );
    }

    @Test
    @DisplayName("holdingServer is required and cannot be null")
    void holdingServerRequired() {
        assertThrows(NullPointerException.class, () ->
                new LobbyConfig(
                        "192.168.1.255",
                        null,
                        300,
                        30,
                        Map.of(),
                        Map.of(),
                        List.of("vanilla"),
                        Map.of()
        )
        );
    }

    @Test
    @DisplayName("serverToMac is defensively copied (immutable)")
    void serverToMacImmutable() {
        Map<String, String> original = new java.util.HashMap<>();
        original.put("server1", "00:11:22:33:44:55");
        
        LobbyConfig config = new LobbyConfig(
                "192.168.1.255",
                "lobby",
                300,
                30,
                original,
                Map.of(),
                List.of("vanilla"),
                Map.of()
        );

        original.put("server2", "AA:BB:CC:DD:EE:FF");
        
        Map<String, String> fromConfig = config.serverToMac();
        assertEquals(1, fromConfig.size());
        assertThrows(UnsupportedOperationException.class, () ->
                fromConfig.put("server3", "11:22:33:44:55:66")
        );
    }

    @Test
    @DisplayName("groups is defensively copied (immutable)")
    void groupsImmutable() {
        Map<String, List<String>> original = new java.util.HashMap<>();
        original.put("group1", new java.util.ArrayList<>(List.of("server1")));
        
        LobbyConfig config = new LobbyConfig(
                "192.168.1.255",
                "lobby",
                300,
                30,
                Map.of(),
                original,
                List.of("vanilla"),
                Map.of()
        );

        original.put("group2", List.of("server2"));
        
        Map<String, List<String>> fromConfig = config.groups();
        assertEquals(1, fromConfig.size());
        assertThrows(UnsupportedOperationException.class, () ->
                fromConfig.put("group3", List.of("server3"))
        );
    }

    @Test
    @DisplayName("equals and hashCode work correctly")
    void equalsAndHashCode() {
        LobbyConfig config1 = new LobbyConfig(
                "192.168.1.255",
                "lobby",
                300,
                30,
                Map.of("server1", "00:11:22:33:44:55"),
                Map.of("group1", List.of("server1")),
                List.of("vanilla"),
                Map.of()
        );

        LobbyConfig config2 = config1; // same instance
        assertEquals(config1, config2);
    }

    @Test
    @DisplayName("all getters return correct values")
    void allGetters() {
        Map<String, String> serverToMac = Map.of("server1", "00:11:22:33:44:55");
        Map<String, List<String>> groups = Map.of("group1", List.of("server1"));

        LobbyConfig config = new LobbyConfig(
                "192.168.1.255",
                "lobby",
                300,
                30,
                serverToMac,
                groups,
                List.of("vanilla", "magic"),
                Map.of()
        );

        assertEquals("192.168.1.255", config.broadcastIp());
        assertEquals("lobby", config.holdingServer());
        assertEquals(300, config.graceSec());
        assertEquals(30, config.pingEverySec());
        assertEquals(serverToMac, config.serverToMac());
        assertEquals(groups, config.groups());
    }
}
