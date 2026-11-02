package com.silver.authorization;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Authenticated actor identity. This is separate from how that actor was authenticated. */
public record AuthorizationSubject(Kind kind, String id) {
    private static final Pattern SERVICE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public AuthorizationSubject {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        switch (kind) {
            case PLAYER -> {
                UUID parsed;
                try {
                    parsed = UUID.fromString(id);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Player subjects require a UUID", exception);
                }
                if (!parsed.toString().equals(id)) {
                    throw new IllegalArgumentException("Player UUID must be lowercase canonical text");
                }
            }
            case SERVICE -> {
                if (!SERVICE_ID.matcher(id).matches()) {
                    throw new IllegalArgumentException("Invalid service subject ID: " + id);
                }
            }
            case CONSOLE -> {
                if (!id.equals("console")) throw new IllegalArgumentException("Console ID must be 'console'");
            }
            case COMMAND_BLOCK -> {
                if (id.isBlank() || id.length() > 128) {
                    throw new IllegalArgumentException("Command-block subject ID is required");
                }
            }
        }
    }

    public static AuthorizationSubject player(UUID uuid) {
        return new AuthorizationSubject(Kind.PLAYER, Objects.requireNonNull(uuid, "uuid").toString());
    }

    public static AuthorizationSubject service(String id) {
        return new AuthorizationSubject(Kind.SERVICE, id.toLowerCase(Locale.ROOT));
    }

    public static AuthorizationSubject console() {
        return new AuthorizationSubject(Kind.CONSOLE, "console");
    }

    public static AuthorizationSubject commandBlock(String id) {
        return new AuthorizationSubject(Kind.COMMAND_BLOCK, id);
    }

    public UUID playerUuid() {
        if (kind != Kind.PLAYER) throw new IllegalStateException("Subject is not a player");
        return UUID.fromString(id);
    }

    public enum Kind {
        PLAYER,
        SERVICE,
        CONSOLE,
        COMMAND_BLOCK
    }
}
