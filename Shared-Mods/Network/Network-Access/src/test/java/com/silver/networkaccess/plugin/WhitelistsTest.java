package com.silver.networkaccess.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

class WhitelistsTest {
    private static final UUID ALICE = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444");
    private static final UUID BOB = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444");

    @TempDir
    Path dataDir;

    private Logger logger;
    private Whitelists whitelists;

    @BeforeEach
    void setUp() throws IOException {
        logger = mock(Logger.class);
        WhitelistStore network = new WhitelistStore(dataDir, logger);
        network.ensureFileAndLoad();
        whitelists = new Whitelists(dataDir, network, logger);
    }

    /** Turns a server's whitelist on the same way the command does: the folder and list first, then the switch file. */
    private void turnOn(String server) throws IOException {
        whitelists.store(server);
        Files.createFile(whitelists.switchFile(server));
    }

    @Test
    void networkAndServerStoresAreSeparateFiles() throws IOException {
        whitelists.store(null).add(ALICE);
        whitelists.store("lobby").add(BOB);

        String networkFile = Files.readString(dataDir.resolve("whitelist.json"), StandardCharsets.UTF_8);
        String serverFile = Files.readString(dataDir.resolve("servers").resolve("lobby").resolve("whitelist.json"),
                StandardCharsets.UTF_8);
        assertTrue(networkFile.contains(ALICE.toString()));
        assertFalse(networkFile.contains(BOB.toString()));
        assertTrue(serverFile.contains(BOB.toString()));
        assertFalse(serverFile.contains(ALICE.toString()));

        assertTrue(whitelists.store(null).isWhitelisted(ALICE));
        assertFalse(whitelists.store(null).isWhitelisted(BOB));
        assertTrue(whitelists.store("lobby").isWhitelisted(BOB));
        assertFalse(whitelists.store("lobby").isWhitelisted(ALICE));
    }

    @Test
    void isOnFollowsTheSwitchFile() throws IOException {
        assertFalse(whitelists.isOn(null));
        Files.createFile(dataDir.resolve("whitelist-on"));
        assertTrue(whitelists.isOn(null));
        Files.delete(dataDir.resolve("whitelist-on"));
        assertFalse(whitelists.isOn(null));

        assertFalse(whitelists.isOn("lobby"));
        turnOn("lobby");
        assertTrue(whitelists.isOn("lobby"));
        assertEquals(dataDir.resolve("servers").resolve("lobby").resolve("whitelist-on"),
                whitelists.switchFile("lobby"));
    }

    @Test
    void allowedOnIsTrueWhenTheServerSwitchIsOff() {
        assertTrue(whitelists.allowedOn("lobby", BOB));
    }

    @Test
    void allowedOnIsFalseForUnlistedPlayerWhenOn() throws IOException {
        whitelists.store("lobby").add(ALICE);
        turnOn("lobby");

        assertFalse(whitelists.allowedOn("lobby", BOB));
    }

    @Test
    void allowedOnIsTrueForListedPlayerWhenOn() throws IOException {
        whitelists.store("lobby").add(ALICE);
        turnOn("lobby");

        assertTrue(whitelists.allowedOn("lobby", ALICE));
    }

    @Test
    void serverNamesAreLowerCasedForTheFolder() throws IOException {
        whitelists.store("Lobby").add(ALICE);

        assertTrue(Files.exists(dataDir.resolve("servers").resolve("lobby").resolve("whitelist.json")));
        assertFalse(Files.exists(dataDir.resolve("servers").resolve("Lobby")));
        assertEquals(dataDir.resolve("servers").resolve("lobby").resolve("whitelist-on"),
                whitelists.switchFile("LOBBY"));
    }
}
