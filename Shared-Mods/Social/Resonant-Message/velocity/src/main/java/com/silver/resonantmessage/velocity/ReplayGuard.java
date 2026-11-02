package com.silver.resonantmessage.velocity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ReplayGuard {
    private static final long RETENTION_MILLIS = 120_000L;
    private static final int MAX_ENTRIES = 50_000;
    private final Path journal;
    private final Map<String, Long> seen = new HashMap<>();

    ReplayGuard(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        journal = dataDirectory.resolve("replay-journal.log");
        if (Files.notExists(journal)) {
            try {
                Files.createFile(journal, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
            } catch (UnsupportedOperationException unsupported) {
                Files.createFile(journal);
            }
        }
        try { Files.setPosixFilePermissions(journal, PosixFilePermissions.fromString("rw-------")); }
        catch (UnsupportedOperationException unsupported) { /* Host ACLs apply on non-POSIX systems. */ }
        long now = System.currentTimeMillis();
        long cutoff = now - RETENTION_MILLIS;
        for (String line : Files.readAllLines(journal, StandardCharsets.UTF_8)) {
            String[] fields = line.split("\\|", -1);
            if (fields.length != 4) continue;
            try {
                long seenAt = Long.parseLong(fields[3]);
                if (seenAt >= cutoff && seenAt <= now + 10_000L) {
                    seen.put(key(fields[0], UUID.fromString(fields[1]), UUID.fromString(fields[2])), seenAt);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore incomplete or corrupt records; HMAC authentication still applies.
            }
        }
        prune(now);
    }

    synchronized boolean markIfNew(String backendId, UUID epoch, UUID nonce, long now) throws IOException {
        prune(now);
        String key = key(backendId, epoch, nonce);
        if (seen.containsKey(key) || seen.size() >= MAX_ENTRIES) return false;
        String line = backendId + "|" + epoch + "|" + nonce + "|" + now + System.lineSeparator();
        Files.writeString(journal, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        seen.put(key, now);
        if (Files.size(journal) > 1_048_576L) compact();
        return true;
    }

    private void prune(long now) {
        seen.entrySet().removeIf(entry -> entry.getValue() < now - RETENTION_MILLIS);
    }

    private void compact() throws IOException {
        Path temporary = journal.resolveSibling(journal.getFileName() + ".tmp");
        StringBuilder content = new StringBuilder();
        seen.forEach((key, time) -> content.append(key).append('|').append(time).append(System.lineSeparator()));
        Files.writeString(temporary, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try { Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------")); }
        catch (UnsupportedOperationException unsupported) { /* Host ACLs apply on non-POSIX systems. */ }
        Files.move(temporary, journal, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String key(String backendId, UUID epoch, UUID nonce) {
        return backendId + "|" + epoch + "|" + nonce;
    }
}