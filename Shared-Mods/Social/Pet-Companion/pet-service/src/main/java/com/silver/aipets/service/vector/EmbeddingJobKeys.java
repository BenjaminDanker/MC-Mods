package com.silver.aipets.service.vector;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

final class EmbeddingJobKeys {
    private EmbeddingJobKeys() {
    }

    static String forMemory(UUID petId, UUID memoryId, long version, String model) {
        String canonical = petId + "\n" + memoryId + "\n" + version + "\n" + model;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "memory-embedding:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
