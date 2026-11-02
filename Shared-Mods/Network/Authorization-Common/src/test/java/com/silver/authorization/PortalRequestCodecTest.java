package com.silver.authorization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.silver.authorization.PortalRequestCodec.PortalRequest;
import com.silver.authorization.PortalRequestCodec.PortalRequestUnsigned;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PortalRequestCodecTest {
    private static final UUID PLAYER = new UUID(0x0102030405060708L, 0x1112131415161718L);
    private static final long ISSUED_AT = 0x0000018F00000001L;
    private static final Pattern URL_SAFE_BASE64_24 = Pattern.compile("[A-Za-z0-9_-]{24}");

    @Test
    void encodeUnsignedMatchesGoldenBytes() {
        assertArrayEquals(goldenUnsigned(),
                PortalRequestCodec.encodeUnsigned(PLAYER, "tech", "gate", ISSUED_AT, "n1"));
    }

    @Test
    void encodeSignedAppendsVarIntLengthAndSignatureAfterUnsignedPrefix() {
        byte[] signature = signatureOf(32);
        byte[] expected = concat(concat(goldenUnsigned(), new byte[] {32}), signature);

        byte[] payload = PortalRequestCodec.encodeSigned(PLAYER, "tech", "gate", ISSUED_AT, "n1", signature);

        assertArrayEquals(expected, payload);
    }

    @Test
    void signedPayloadRoundTripsThroughDecode() {
        byte[] signature = signatureOf(32);
        byte[] payload = PortalRequestCodec.encodeSigned(PLAYER, "tech", "gate", ISSUED_AT, "n1", signature);

        PortalRequest decoded = PortalRequestCodec.decode(payload).orElseThrow();

        assertEquals(PLAYER, decoded.playerId());
        assertEquals("tech", decoded.targetServer());
        assertEquals("gate", decoded.arrivalPortal());
        assertEquals(ISSUED_AT, decoded.issuedAtMs());
        assertEquals("n1", decoded.nonce());
        assertArrayEquals(signature, decoded.signature());
    }

    @Test
    void recordEncodeDecodeAndUnsignedViewRoundTrip() {
        PortalRequest original = new PortalRequest(PLAYER, "tech", "gate", ISSUED_AT, "n1", signatureOf(32));

        byte[] payload = PortalRequestCodec.encode(original);
        PortalRequest decoded = PortalRequestCodec.decode(payload).orElseThrow();
        assertSameRequest(original, decoded);

        PortalRequestUnsigned unsigned = original.unsigned();
        assertEquals(new PortalRequestUnsigned(PLAYER, "tech", "gate", ISSUED_AT, "n1"), unsigned);
        assertArrayEquals(PortalRequestCodec.encodeUnsigned(PLAYER, "tech", "gate", ISSUED_AT, "n1"),
                PortalRequestCodec.encodeUnsigned(unsigned));
        assertArrayEquals(goldenUnsigned(), Arrays.copyOf(payload, goldenUnsigned().length));
    }

    @Test
    void longStringsUseMultiByteVarIntLengthAndRoundTrip() {
        String longTarget = "a".repeat(200);
        PortalRequest original = new PortalRequest(PLAYER, longTarget, "gate", ISSUED_AT, "n1", signatureOf(32));

        byte[] payload = PortalRequestCodec.encode(original);

        // Target length starts after version (1) + issuedAtMs (8) + UUID (16) = index 25.
        // 200 as a varint is 0xC8 0x01 (low 7 bits with continuation, then the high bit).
        assertEquals((byte) 0xC8, payload[25]);
        assertEquals((byte) 0x01, payload[26]);
        assertSameRequest(original, PortalRequestCodec.decode(payload).orElseThrow());
    }

    @Test
    void nonAsciiStringsRoundTripAsUtf8() {
        // "Zoë→" is 7 UTF-8 bytes: Z, o, 2-byte e-diaeresis, 3-byte arrow.
        String arrival = "Zoë→";
        PortalRequest original = new PortalRequest(PLAYER, "tech", arrival, ISSUED_AT, "n1", signatureOf(32));

        byte[] payload = PortalRequestCodec.encode(original);

        // Arrival length byte sits after version, issuedAtMs, UUID, and the "tech" string (1 + 4 bytes).
        assertEquals(7, payload[30]);
        PortalRequest decoded = PortalRequestCodec.decode(payload).orElseThrow();
        assertEquals(arrival, decoded.arrivalPortal());
        assertSameRequest(original, decoded);
    }

    @Test
    void decodeReturnsEmptyForNullEmptyWrongVersionOrBlankFields() {
        byte[] signature = signatureOf(32);
        byte[] wrongVersion = PortalRequestCodec.encodeSigned(PLAYER, "tech", "gate", ISSUED_AT, "n1", signature);
        wrongVersion[0] = 2;

        assertEquals(Optional.empty(), PortalRequestCodec.decode(null));
        assertEquals(Optional.empty(), PortalRequestCodec.decode(new byte[0]));
        assertEquals(Optional.empty(), PortalRequestCodec.decode(wrongVersion));
        assertEquals(Optional.empty(), PortalRequestCodec.decode(
                PortalRequestCodec.encodeSigned(PLAYER, "  ", "gate", ISSUED_AT, "n1", signature)));
        assertEquals(Optional.empty(), PortalRequestCodec.decode(
                PortalRequestCodec.encodeSigned(PLAYER, "tech", "gate", ISSUED_AT, " ", signature)));
        assertEquals(Optional.empty(), PortalRequestCodec.decode(
                PortalRequestCodec.encodeSigned(PLAYER, "tech", "gate", ISSUED_AT, "", signature)));
    }

    @Test
    void decodeThrowsForTruncatedPayload() {
        byte[] valid = PortalRequestCodec.encodeSigned(PLAYER, "tech", "gate", ISSUED_AT, "n1", signatureOf(32));
        byte[] truncated = Arrays.copyOf(valid, valid.length - 5);

        assertThrows(IllegalArgumentException.class, () -> PortalRequestCodec.decode(truncated));
    }

    @Test
    void decodeThrowsForSignatureLongerThan128BytesAndAcceptsExactly128() {
        byte[] oversized = PortalRequestCodec.encodeSigned(PLAYER, "tech", "gate", ISSUED_AT, "n1", signatureOf(129));
        byte[] maximum = PortalRequestCodec.encodeSigned(PLAYER, "tech", "gate", ISSUED_AT, "n1", signatureOf(128));

        assertThrows(IllegalArgumentException.class, () -> PortalRequestCodec.decode(oversized));
        assertEquals(128, PortalRequestCodec.decode(maximum).orElseThrow().signature().length);
    }

    @Test
    void generateNonceIsTwentyFourUrlSafeBase64CharsAndVaries() {
        String first = PortalRequestCodec.generateNonce();
        String second = PortalRequestCodec.generateNonce();

        assertTrue(URL_SAFE_BASE64_24.matcher(first).matches(), first);
        assertTrue(URL_SAFE_BASE64_24.matcher(second).matches(), second);
        assertNotEquals(first, second);
        assertFalse(first.contains("="));
    }

    private static void assertSameRequest(PortalRequest expected, PortalRequest actual) {
        assertEquals(expected.playerId(), actual.playerId());
        assertEquals(expected.targetServer(), actual.targetServer());
        assertEquals(expected.arrivalPortal(), actual.arrivalPortal());
        assertEquals(expected.issuedAtMs(), actual.issuedAtMs());
        assertEquals(expected.nonce(), actual.nonce());
        assertArrayEquals(expected.signature(), actual.signature());
    }

    /**
     * Hand-written expected bytes for encodeUnsigned(PLAYER, "tech", "gate", ISSUED_AT, "n1"):
     * version 1, issuedAtMs big-endian, UUID msb then lsb big-endian, then length-prefixed UTF-8 strings.
     */
    private static byte[] goldenUnsigned() {
        return new byte[] {
                0x01,
                0x00, 0x00, 0x01, (byte) 0x8F, 0x00, 0x00, 0x00, 0x01,
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
                0x04, 't', 'e', 'c', 'h',
                0x04, 'g', 'a', 't', 'e',
                0x02, 'n', '1'
        };
    }

    private static byte[] signatureOf(int length) {
        byte[] signature = new byte[length];
        for (int i = 0; i < length; i++) {
            signature[i] = (byte) (0xA0 + i);
        }
        return signature;
    }

    private static byte[] concat(byte[] head, byte[] tail) {
        byte[] out = Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, out, head.length, tail.length);
        return out;
    }
}
