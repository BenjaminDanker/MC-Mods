package com.silver.aipets.fabric.speech;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/** Pure rendering policy: bounded, non-clickable, two-sentence speech only. */
public record PetSpeechPolicy(
        int maximumCharacters, int wrapColumns,
        Duration minimumLifetime, Duration maximumLifetime,
        float verticalGap, float yawOffsetDegrees, float viewRange) {
    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.)\\S+|\\b[a-z0-9.-]+\\.(?:com|net|org|gg|io)\\b\\S*");

    public PetSpeechPolicy {
        if (maximumCharacters < 40 || maximumCharacters > 240 || wrapColumns < 10 || wrapColumns > 80
                || verticalGap < 0 || verticalGap > 2 || viewRange <= 0 || viewRange > 4) {
            throw new IllegalArgumentException("Speech display bounds are invalid");
        }
        Objects.requireNonNull(minimumLifetime, "minimumLifetime");
        Objects.requireNonNull(maximumLifetime, "maximumLifetime");
        if (minimumLifetime.isNegative() || minimumLifetime.isZero()
                || maximumLifetime.compareTo(minimumLifetime) < 0
                || maximumLifetime.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Speech lifetime bounds are invalid");
        }
    }

    public static PetSpeechPolicy defaults() {
        // Billboard orientation is client-side; yaw is retained only for source compatibility.
        return new PetSpeechPolicy(
                240, 38, Duration.ofSeconds(5), Duration.ofSeconds(12),
                0.65F, 0.0F, 1.0F);
    }

    public PreparedSpeech prepare(String untrusted) {
        Objects.requireNonNull(untrusted, "untrusted");
        String normalized = untrusted.replaceAll("§.", "")
                .replaceAll("[\\p{Cc}&&[^\\n\\t]]", " ")
                .replace('\n', ' ').replace('\t', ' ')
                .replaceAll("\\s+", " ").strip();
        normalized = URL.matcher(normalized).replaceAll("").replaceAll("\\s+", " ").strip();
        normalized = firstSentences(normalized, 2);
        if (normalized.codePointCount(0, normalized.length()) > maximumCharacters) {
            int end = normalized.offsetByCodePoints(0, maximumCharacters - 1);
            normalized = normalized.substring(0, end).stripTrailing() + "…";
        }
        if (normalized.isBlank()) throw new IllegalArgumentException("Speech is empty after sanitization");
        String wrapped = wrap(normalized, wrapColumns);
        long min = minimumLifetime.toMillis();
        long max = maximumLifetime.toMillis();
        long calculated = min + normalized.codePointCount(0, normalized.length()) * 35L;
        return new PreparedSpeech(normalized, wrapped, Duration.ofMillis(Math.min(max, calculated)));
    }

    private static String firstSentences(String value, int maximum) {
        int sentences = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if ((current == '.' || current == '!' || current == '?')
                    && (index + 1 == value.length() || Character.isWhitespace(value.charAt(index + 1)))) {
                if (++sentences == maximum) return value.substring(0, index + 1).strip();
            }
        }
        return value;
    }

    private static String wrap(String value, int columns) {
        StringBuilder result = new StringBuilder();
        int line = 0;
        for (String word : value.split(" ")) {
            if (line > 0 && line + 1 + word.length() > columns) {
                result.append('\n'); line = 0;
            } else if (line > 0) {
                result.append(' '); line++;
            }
            result.append(word); line += word.length();
        }
        return result.toString();
    }

    public record PreparedSpeech(String plainText, String wrappedText, Duration lifetime) { }
}
