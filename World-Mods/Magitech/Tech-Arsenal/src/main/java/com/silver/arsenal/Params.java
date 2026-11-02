package com.silver.arsenal;

import java.util.Map;

/** The numbers of one behaviour, with the server's overrides already applied. Missing keys fall back to the given default. */
final class Params {
    static final Params EMPTY = new Params(Map.of());

    private final Map<String, Object> values;

    Params(Map<String, Object> values) {
        this.values = values == null ? Map.of() : values;
    }

    String kind() {
        return str("kind", "");
    }

    double num(String key, double fallback) {
        Object value = values.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    int integer(String key, int fallback) {
        return (int) Math.round(num(key, fallback));
    }

    String str(String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String text ? text : fallback;
    }

    boolean bool(String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }

    boolean has(String key) {
        return values.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    java.util.List<Map<String, Object>> list(String key) {
        Object value = values.get(key);
        return value instanceof java.util.List<?> list ? (java.util.List<Map<String, Object>>) list : java.util.List.of();
    }

    @SuppressWarnings("unchecked")
    Params child(String key) {
        Object value = values.get(key);
        return value instanceof Map<?, ?> map ? new Params((Map<String, Object>) map) : EMPTY;
    }
}
