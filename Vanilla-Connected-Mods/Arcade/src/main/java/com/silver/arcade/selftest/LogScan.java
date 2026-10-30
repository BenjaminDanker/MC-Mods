package com.silver.arcade.selftest;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;

/** Reads the server log from where the test started and picks out errors. */
public final class LogScan {
    /** Lines the test accepts (known noise of fake players, not product problems). */
    private static final List<Pattern> ALLOW = List.of(
        Pattern.compile("(?i)profile.*(lookup|fetch|resolve)|Couldn't (look up|fetch).*profile|Failed to (fetch|look up|verify).*profile"),
        Pattern.compile("(?i)polymer.*(chunk|packet)|chunk update packet, without the packet context|Please report this to mods in the stack-trace"),
        Pattern.compile("(?i)com\\.mojang\\.authlib|authlib|yggdrasil|sessionserver|api\\.minecraftservices|api\\.mojang"),
        Pattern.compile("\\[selftest\\]|arcade\\.selftest"));
    private static Path log;
    private static long offset;

    private LogScan() {
    }

    static void begin(MinecraftServer server) {
        log = server.getServerDirectory().resolve("logs/latest.log");
        try {
            offset = Files.exists(log) ? Files.size(log) : 0;
        } catch (IOException failure) {
            offset = 0;
        }
    }

    /** New log lines since the start. */
    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        if (log == null) {
            return out;
        }
        try (RandomAccessFile file = new RandomAccessFile(log.toFile(), "r")) {
            long size = file.length();
            long from = size < offset ? 0 : offset; // the log was rotated
            file.seek(from);
            byte[] bytes = new byte[(int) Math.min(size - from, 64L * 1024 * 1024)];
            file.readFully(bytes);
            for (String line : new String(bytes, StandardCharsets.UTF_8).split("\\R")) {
                out.add(line);
            }
        } catch (IOException failure) {
            out.add("[log unreadable] " + failure);
        }
        return out;
    }

    /** Error entries (an ERROR line with the stack trace lines after it), minus the allow-list. */
    public static List<String> errors() {
        List<String> found = new ArrayList<>();
        StringBuilder entry = null;
        for (String line : lines()) {
            boolean header = line.startsWith("[") && line.length() > 11 && line.charAt(9) == ']';
            if (header) {
                if (entry != null) {
                    found.add(entry.toString());
                    entry = null;
                }
                if (line.contains("/ERROR]") || line.contains("/FATAL]")) {
                    entry = new StringBuilder(line);
                }
            } else if (entry != null && entry.length() < 1200) {
                entry.append(" | ").append(line.strip());
            }
        }
        if (entry != null) {
            found.add(entry.toString());
        }
        found.removeIf(e -> ALLOW.stream().anyMatch(p -> p.matcher(e).find()));
        return found;
    }

    /** Lines containing the text (for checking that something was logged). */
    public static List<String> grep(String text) {
        return lines().stream().filter(l -> l.contains(text)).toList();
    }
}
