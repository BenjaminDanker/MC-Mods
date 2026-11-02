package com.silver.aipets.service.devplayer;

/** Redacts common credentials even when the synthetic owner includes them in a message. */
public final class DevPlayerRedactor {
    private DevPlayerRedactor() { }
    public static String redact(String text) {
        return text.replaceAll("(?i)\\b(sk-(?:proj-)?[A-Za-z0-9_-]{12,})", "[REDACTED_KEY]")
                .replaceAll("(?i)\\bBearer\\s+[A-Za-z0-9._-]{12,}", "Bearer [REDACTED]")
                .replaceAll("(?i)(password|secret|token|api_key)\\s*[:=]\\s*[^\\s,;]+",
                        "$1=[REDACTED]");
    }
}
