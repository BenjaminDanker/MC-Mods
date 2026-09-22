package com.silver.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommandPolicyRegistryTest {
    private static final CommandPolicyRegistry POLICIES = DiscoveredCommandPolicies.create();

    @Test
    void publicPathsAreVisibleAndExecutable() {
        var decision = decide("/help", true, false, false, false, ignored -> false);
        assertEquals(CommandClassification.PUBLIC, decision.classification());
        assertTrue(decision.visible());
        assertTrue(decision.allowed());
    }

    @Test
    void nestedPathsInheritNearestMappedAncestorAndMoreSpecificPoliciesOverride() {
        var parent = decide("/pet", true, false, false, false, ignored -> false);
        var child = decide("/pet admin inspect", true, false, false, false, ignored -> false);
        var inheritedAdminChild = decide("/pet admin unclassified", true, false, false, false, ignored -> false);

        assertEquals(CommandClassification.PUBLIC, parent.classification());
        assertEquals(CommandClassification.PERMISSION, child.classification());
        assertEquals("aipets.admin.inspect", child.requiredPermission().orElseThrow().value());
        assertEquals(CommandClassification.PERMISSION, inheritedAdminChild.classification());
        assertEquals("aipets.admin.*", inheritedAdminChild.requiredPermission().orElseThrow().value());
        assertEquals("aipets.admin.__policy_probe__",
                inheritedAdminChild.requiredPermission().orElseThrow().policyGateNode().value());
        assertFalse(inheritedAdminChild.visible());
        assertFalse(inheritedAdminChild.allowed());
        assertTrue(decide("/pet admin", true, false, false, false,
                pattern -> pattern.value().equals("aipets.admin.*")).allowed());
    }

    @Test
    void nearestMappedAncestorAppliesWithoutArgumentNodePolicies() {
        CommandPolicyRegistry registry = CommandPolicyRegistry.builder()
                .add("/example", CommandClassification.PUBLIC, null, "test", "test")
                .add("/example staff", CommandClassification.PERMISSION, "staff.example",
                        "test", "test")
                .add("/example staff inspect", CommandClassification.PUBLIC, null, "test", "test")
                .build();

        assertEquals(CommandClassification.PUBLIC,
                registry.decide(CommandOrigin.FABRIC_BACKEND, CommandPath.of("/example arbitrary argument"), true, false,
                        false, false, ignored -> false).classification());
        var inherited = registry.decide(CommandOrigin.FABRIC_BACKEND,
                CommandPath.of("/example staff future child"), true, false,
                false, false, ignored -> false);
        assertEquals(CommandClassification.PERMISSION, inherited.classification());
        assertEquals("staff.example", inherited.requiredPermission().orElseThrow().value());
        assertEquals(CommandClassification.PUBLIC,
                registry.decide(CommandOrigin.FABRIC_BACKEND,
                        CommandPath.of("/example staff inspect detail"), true, false,
                        false, false, ignored -> false).classification());
    }

    @Test
    void internalIsHiddenAndNeverOwnerOverriddenForPlayer() {
        for (boolean owner : new boolean[]{false, true}) {
            var decision = decide("/mpdscomplete", true, owner, false, true, ignored -> true);
            assertEquals(CommandClassification.INTERNAL, decision.classification());
            assertFalse(decision.visible());
            assertFalse(decision.allowed());
            assertFalse(decision.ownerOverride());
        }

        assertTrue(decide("/mpdscomplete", false, false, true, false, ignored -> false).allowed());
        assertFalse(decide("/return", true, true, false, false, ignored -> true).visible());
        assertTrue(decide("/return", false, false, true, false, ignored -> false).allowed());
    }

    @Test
    void datapackFunctionsRetainTheirServerCommandAuthorityWithoutTrustingBlocksOrRcon() {
        assertTrue(decide("/time set", false, false, true, false, ignored -> false).allowed());
        assertFalse(decide("/time set", false, false, false, false, ignored -> false).allowed());
        assertFalse(decide("/time set", true, false, true, false, ignored -> false).allowed());
    }

    @Test
    void unmappedIsVisibleDeniedExceptForOwnerWithExplicitWarningState() {
        var player = decide("/genuinely-unknown replace", true, false, false, false, ignored -> true);
        assertEquals(CommandClassification.UNMAPPED, player.classification());
        assertTrue(player.visible());
        assertFalse(player.allowed());
        assertFalse(player.ownerOverride());

        var owner = decide("/genuinely-unknown replace", true, true, false, false, ignored -> false);
        assertTrue(owner.visible());
        assertTrue(owner.allowed());
        assertTrue(owner.ownerOverride());
    }

    @Test
    void unknownNestedLiteralDoesNotInheritMappedPermissionOrPublicAccess() {
        assertEquals(CommandClassification.PERMISSION,
                decide("/skyislands dragons undocumented", true, false, false, false, ignored -> true)
                        .classification());
        assertEquals(CommandClassification.PERMISSION,
                decide("/skyislands dragons", true, false, false, false, ignored -> true)
                        .classification());
        assertEquals(CommandClassification.UNMAPPED,
                decide("/truly-unknown command", true, false, false, false, ignored -> true)
                        .classification());
    }

    @Test
    void worldeditAndInstalledAdminUtilitiesUseCentralPermissionFamilies() {
        assertEquals("commands.admin.worldedit",
                decide("/worldedit replace", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("commands.admin.worldedit",
                decide("// set", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("commands.admin.worldedit",
                decide("//brush", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("commands.admin.worldedit",
                decide("//set replace", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("commands.admin.worldedit",
                decide("/br", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("commands.admin.worldedit",
                decide("/removeabove", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("commands.admin.spark",
                decide("/spark profiler", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("commands.admin.carpet",
                decide("/script run", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("commands.admin.carpet",
                decide("/spawn tracking start", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("Carpet", decide("/spawn entities all", true, false, false, false, ignored -> false)
                .source().orElseThrow());
        assertEquals(1, POLICIES.entries().stream()
                .filter(entry -> entry.origin() == CommandOrigin.FABRIC_BACKEND)
                .filter(entry -> entry.path().root().equals("spawn"))
                .count(), "Carpet /spawn must be mapped once at the root");
        var playerSpawn = decide("/spawn tracking start", true, false, false, false, ignored -> false);
        assertFalse(playerSpawn.visible());
        assertFalse(playerSpawn.allowed());
        var adminSpawn = decide("/spawn tracking start", true, false, false, false,
                permission -> permission.value().equals("commands.admin.carpet"));
        assertTrue(adminSpawn.visible());
        assertTrue(adminSpawn.allowed());
    }

    @Test
    void suspiciousCommandRootsUseTheirIntendedPlayerPolicyAndKeepFamilyInheritance() {
        for (String path : new String[]{"/spawn", "/spawn rates reset", "/spark", "/spark profiler",
                "/polymer", "/polymer client-item", "/seed", "/version"}) {
            var player = decide(path, true, false, false, false, ignored -> false);
            assertEquals(CommandClassification.PERMISSION, player.classification(), path);
            assertFalse(player.visible(), path);
            assertFalse(player.allowed(), path);
            assertTrue(decide(path, true, false, false, false,
                    permission -> permission.value().startsWith("commands.admin.")).visible(), path);
            var owner = decide(path, true, true, false, false, ignored -> false);
            assertTrue(owner.visible(), path);
            assertTrue(owner.allowed(), path);
        }

        for (String path : new String[]{"/trigger", "/trigger objective", "/list", "/random", "/random roll",
                "/random value", "/mpdsremovecustomidself", "/mpdsremovecustomidself key value"}) {
            var player = decide(path, true, false, false, false, ignored -> false);
            if (path.startsWith("/mpdsremovecustomidself")) {
                assertEquals(CommandClassification.PERMISSION, player.classification(), path);
                assertFalse(player.visible(), path);
                assertFalse(player.allowed(), path);
            } else {
                assertEquals(CommandClassification.PUBLIC, player.classification(), path);
                assertTrue(player.visible(), path);
                assertTrue(player.allowed(), path);
            }
        }

        for (String path : new String[]{"/list uuids", "/locate structure", "/locate biome",
                "/waypoint", "/waypoint list", "/waypoint modify target style set",
                "/random reset", "/random reset sequence"}) {
            var player = decide(path, true, false, false, false, ignored -> false);
            assertEquals(CommandClassification.PERMISSION, player.classification(), path);
            assertEquals("commands.admin.vanilla", player.requiredPermission().orElseThrow().value(), path);
            assertFalse(player.visible(), path);
            assertFalse(player.allowed(), path);
        }
    }

    @Test
    void identicalReturnLiteralsResolveIndependentlyByCommandOrigin() {
        var backend = POLICIES.decide(CommandOrigin.FABRIC_BACKEND, CommandPath.of("/return"),
                true, true, false, false, ignored -> true);
        var proxy = POLICIES.decide(CommandOrigin.VELOCITY_PROXY, CommandPath.of("/return"),
                true, false, false, false, ignored -> false);

        assertEquals(CommandClassification.INTERNAL, backend.classification());
        assertFalse(backend.visible());
        assertFalse(backend.allowed());
        assertEquals(CommandClassification.PUBLIC, proxy.classification());
        assertTrue(proxy.visible());
        assertTrue(proxy.allowed());
        assertEquals("WakeUpLobby", proxy.source().orElseThrow());
    }

    @Test
    void petFeatureRootsUseTheirSpecificCentralCapabilities() {
        assertEquals(CommandClassification.PUBLIC,
                decide("/pet", true, false, false, false, ignored -> false).classification());
        assertEquals("aipets.use", petDecision("/pet help", "aipets.use").requiredPermission().orElseThrow().value());
        assertEquals("aipets.use", petDecision("/pet status", "aipets.use").requiredPermission().orElseThrow().value());
        assertEquals("aipets.use", petDecision("/pet link", "aipets.use").requiredPermission().orElseThrow().value());
        assertEquals("aipets.use", petDecision("/pet billing", "aipets.use").requiredPermission().orElseThrow().value());
        assertEquals("aipets.use", petDecision("/pet portal", "aipets.use").requiredPermission().orElseThrow().value());
        assertEquals("aipets.use", petDecision("/pet place", "aipets.use").requiredPermission().orElseThrow().value());
        assertEquals("aipets.use", petDecision("/pet pickup", "aipets.use").requiredPermission().orElseThrow().value());
        assertEquals("aipets.use", petDecision("/pet adopt subscribe", "aipets.use").requiredPermission().orElseThrow().value());
        assertEquals("aipets.adopt", petDecision("/pet adopt cat Mochi", "aipets.adopt").requiredPermission().orElseThrow().value());
        assertEquals("aipets.recall", petDecision("/pet recall", "aipets.recall").requiredPermission().orElseThrow().value());
        assertEquals("aipets.compass", petDecision("/pet compass", "aipets.compass").requiredPermission().orElseThrow().value());
        assertEquals("aipets.admin.inspect", petDecision("/pet admin inspect", "aipets.admin.inspect").requiredPermission().orElseThrow().value());
        assertEquals("aipets.admin.recover", petDecision("/pet admin recall-reset", "aipets.admin.recover").requiredPermission().orElseThrow().value());
        assertEquals("aipets.admin.memory", petDecision("/pet admin history", "aipets.admin.memory").requiredPermission().orElseThrow().value());
        assertEquals("aipets.admin.reconcile", petDecision("/pet admin reconcile", "aipets.admin.reconcile").requiredPermission().orElseThrow().value());
        assertTrue(decide("/pet help", true, false, false, false,
                node -> node.value().equals("aipets.use")).allowed());
        assertFalse(decide("/pet help", true, false, false, false,
                ignored -> false).allowed());
        assertFalse(decide("/pet help", true, false, false, false,
                node -> !node.value().equals("aipets.use")).allowed());
        assertTrue(decide("/pet admin inspect", true, false, false, false,
                node -> node.value().equals("aipets.admin.inspect")).allowed());
        assertFalse(decide("/pet admin inspect", true, false, false, false,
                node -> node.value().equals("aipets.admin.*")).allowed());
        assertEquals("dimensions.manage",
                decide("/disabledimensions", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("portal.registry.manage",
                decide("/serverportals register", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("portal.registry.view",
                decide("/serverportals list", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("commands.admin.openpac",
                decide("/opac sub create", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertFalse(decide("/oclaims admin-mode", true, false, false, false, ignored -> false).allowed());
        assertFalse(decide("/oparties impersonate", true, false, false, false, ignored -> false).allowed());
        assertEquals("xaero.pac_claims_impersonation",
                decide("/oclaims claim as target", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("xaero.pac_claims_impersonation",
                decide("/oclaims party claim as target", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("xaero.pac_server_claims",
                decide("/oclaims server claim", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("xaero.pac_server_claims",
                decide("/oclaims server-claim-mode", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("xaero.pac_admin_mode",
                decide("/oclaims clear for profile", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("xaero.pac_claims_moderator_mode",
                decide("/oclaims interrupt for player", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertEquals("xaero.pac_claims_teleport",
                decide("/oclaims teleport profile", true, false, false, false, ignored -> false)
                        .requiredPermission().orElseThrow().value());
        assertTrue(decide("/opac help", true, false, false, false, ignored -> false).allowed());
        assertTrue(decide("/oclaims claim in", true, false, false, false, ignored -> false).allowed());
    }

    private static CommandPolicyDecision petDecision(String path, String permission) {
        var decision = decide(path, true, false, false, false,
                node -> node.value().equals(permission));
        assertEquals(CommandClassification.PERMISSION, decision.classification(), path);
        assertEquals(permission, decision.requiredPermission().orElseThrow().value(), path);
        return decision;
    }

    @Test
    void commandPathsPreserveBrigadierSymbolLiterals() {
        assertEquals("/execute if score <=", CommandPath.of("/execute if score <=").canonical());
        assertEquals("/recipe give *", CommandPath.of("recipe give *").canonical());
        assertEquals(CommandClassification.PERMISSION,
                decide("/execute if score <=", true, false, false, false, ignored -> true).classification());
    }

    private static CommandPolicyDecision decide(String path, boolean player, boolean owner,
                                                 boolean internal, boolean console,
                                                 java.util.function.Predicate<PermissionPattern> permission) {
        return POLICIES.decide(CommandOrigin.FABRIC_BACKEND, CommandPath.of(path),
                player, owner, internal, console, permission);
    }
}
