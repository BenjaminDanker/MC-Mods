package com.silver.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizationProtocolCodecTest {
    private static final byte[] KEY = new byte[32];
    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");
    private static final ServerId SERVER = ServerId.of("vanilla1");
    private static final UUID PLAYER = UUID.fromString("ac4c25b5-7dd2-47c9-a6c0-7c08a3b5905b");
    private static final UUID EPOCH = UUID.fromString("9c434a47-62ac-43bd-a64c-a40910b46f6c");
    private static final UUID NONCE = UUID.fromString("4f474608-bfcc-47ef-9c3e-d53d7c80e57b");

    @Test
    void requestAndSnapshotRoundTripPreserveAuthenticationAndRuleProvenance() {
        var requestSigner = new BackendRequestSigner();
        var unsigned = AuthorizationSyncRequest.unsigned(SERVER.value(), PLAYER, SERVER, EPOCH, NONCE, NOW);
        var request = requestSigner.sign(unsigned, KEY);
        var decodedRequest = AuthorizationProtocolCodec.decodeRequest(AuthorizationProtocolCodec.encodeRequest(request));
        assertTrue(requestSigner.verify(decodedRequest, KEY));
        assertFalse(requestSigner.verify(decodedRequest.withSignature("B".repeat(43)), KEY));

        var petPermission = PermissionNode.of("aipets.use");
        var playerRole = PermissionRule.role(PermissionPattern.of("aipets.*"),
                AuthorizationScope.global(), PermissionEffect.ALLOW, RoleId.of("PLAYER"));
        var snapshot = new AuthorizationSnapshot(AuthorizationSnapshot.CURRENT_PROTOCOL_VERSION,
                AuthorizationSubject.player(PLAYER), SERVER, 7, EPOCH, NONCE, NOW,
                NOW.plusSeconds(90), List.of(playerRole));
        var signer = new SnapshotSigner();
        var signed = signer.sign(snapshot, KEY);
        var decoded = AuthorizationProtocolCodec.decodeSnapshot(AuthorizationProtocolCodec.encodeSnapshot(signed));
        assertTrue(signer.verify(decoded, KEY));
        assertEquals(RoleId.of("PLAYER"), decoded.snapshot().rules().get(0).sourceRole().orElseThrow());

        var store = new AuthorizationSnapshotStore(Clock.fixed(NOW, ZoneOffset.UTC), signer);
        store.beginServerSession(SERVER, EPOCH);
        assertEquals(SnapshotApplyResult.APPLIED, store.apply(decoded,
                AuthorizationSubject.player(PLAYER), SERVER, KEY));
        var decision = store.decide(AuthorizationSubject.player(PLAYER), petPermission, SERVER);
        assertTrue(decision.allowed());
        assertEquals(7, decision.authorizationRevision());
        assertEquals(RoleId.of("PLAYER"), decision.matchedRule().orElseThrow().sourceRole().orElseThrow());
    }
}
