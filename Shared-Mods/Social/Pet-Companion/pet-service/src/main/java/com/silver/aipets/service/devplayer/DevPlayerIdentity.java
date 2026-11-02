package com.silver.aipets.service.devplayer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Named test identities allow one run to be removed without touching other Dev Player sessions. */
public record DevPlayerIdentity(String session, UUID owner, UUID pet, UUID entity) {
    public static DevPlayerIdentity forSession(String session) {
        if (!session.matches("[a-z0-9][a-z0-9_-]{0,63}")) throw new IllegalArgumentException("Invalid test session name");
        return new DevPlayerIdentity(session,
                id("1ec1f4a8-8c9b-4e41-b978-3c84f658eabc", session),
                id("9f8eaf42-5a96-4dc4-a57d-a4e60e33ed19", session),
                id("e4ac1a15-4912-4512-b89c-b3c682a38072", session));
    }
    private static UUID id(String original, String session) {
        return session.equals("default") ? UUID.fromString(original)
                : UUID.nameUUIDFromBytes((original + ":" + session).getBytes(StandardCharsets.UTF_8));
    }
}
