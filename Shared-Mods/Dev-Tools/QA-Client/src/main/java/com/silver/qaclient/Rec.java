package com.silver.qaclient;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Append-only JSON-lines event log: every line is flushed at once, so a crash loses nothing. */
public final class Rec {
    public static volatile Path dir = Path.of("qa-out");
    public static volatile long tick;
    public static volatile String room = "-";
    public static volatile String step = "-";
    private static PrintWriter out;

    private Rec() {
    }

    public static String esc(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    static String val(Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof Number || o instanceof Boolean) {
            return o.toString();
        }
        if (o instanceof Raw r) {
            return r.json;
        }
        return esc(o.toString());
    }

    /** Pre-rendered JSON. */
    public record Raw(String json) {
    }

    public static synchronized void event(String kind, Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", System.currentTimeMillis());
        m.put("tick", tick);
        m.put("room", room);
        m.put("step", step);
        m.put("kind", kind);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        StringBuilder b = new StringBuilder("{");
        boolean first = true;
        for (var e : m.entrySet()) {
            if (!first) {
                b.append(',');
            }
            first = false;
            b.append(esc(e.getKey())).append(':').append(val(e.getValue()));
        }
        b.append('}');
        try {
            if (out == null) {
                Files.createDirectories(dir);
                out = new PrintWriter(Files.newBufferedWriter(dir.resolve("events.jsonl"), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND), true);
            }
            out.println(b);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
