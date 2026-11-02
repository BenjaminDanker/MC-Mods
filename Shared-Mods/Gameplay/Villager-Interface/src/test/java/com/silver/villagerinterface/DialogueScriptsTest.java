package com.silver.villagerinterface;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DialogueScriptsTest {
    private static final Gson GSON = new Gson();
    private static final Path DIR = Path.of("src/main/resources/villagerinterface/dialogues");

    static class Choice {
        String label;
        String go;
        String action;
        Boolean end;
    }

    static class Node {
        List<String> text;
        List<Choice> choices;
    }

    static class Script {
        String start;
        List<Choice> openings;
        Map<String, Node> nodes;
    }

    private static List<Path> files() throws IOException {
        try (Stream<Path> s = Files.list(DIR)) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static Script load(Path p) throws IOException {
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, Script.class);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    @Test
    void dialogueFilesExist() throws IOException {
        List<Path> files = files();
        assertTrue(files.size() >= 2, "expected at least oswin.json and rowan.json");
        assertTrue(Files.exists(DIR.resolve("oswin.json")));
        assertTrue(Files.exists(DIR.resolve("rowan.json")));
    }

    @Test
    void allScriptsAreWellFormed() throws IOException {
        for (Path p : files()) {
            String f = p.getFileName().toString();
            Script s = load(p);
            assertNotNull(s, f);
            assertFalse(blank(s.start), f + ": start");
            assertNotNull(s.nodes, f + ": nodes");
            assertFalse(s.nodes.isEmpty(), f + ": nodes empty");
            assertTrue(s.nodes.containsKey(s.start), f + ": start node '" + s.start + "' missing");

            if (s.openings != null) {
                for (Choice o : s.openings) {
                    assertFalse(blank(o.go), f + ": opening without go");
                    assertTrue(s.nodes.containsKey(o.go), f + ": opening go '" + o.go + "' missing");
                }
            }

            for (Map.Entry<String, Node> e : s.nodes.entrySet()) {
                String n = f + " node '" + e.getKey() + "'";
                Node node = e.getValue();
                assertNotNull(node, n);
                assertTrue(node.text != null && !node.text.isEmpty(), n + ": no text");
                for (String line : node.text) assertFalse(blank(line), n + ": blank text line");
                assertTrue(node.choices != null && !node.choices.isEmpty(), n + ": no choices");
                for (Choice c : node.choices) {
                    boolean hasGo = c.go != null;
                    boolean hasAction = c.action != null;
                    boolean hasEnd = Boolean.TRUE.equals(c.end);
                    int kinds = (hasGo ? 1 : 0) + (hasAction ? 1 : 0) + (hasEnd ? 1 : 0);
                    String where = n + " choice '" + c.label + "'";
                    assertEquals(1, kinds, where + ": needs exactly one of go/action/end");
                    if (hasGo) assertTrue(s.nodes.containsKey(c.go), where + ": go '" + c.go + "' missing");
                    if (hasAction) {
                        assertTrue(c.action.equals("soulbound_quote") || c.action.equals("soulbound_confirm"),
                                where + ": unknown action '" + c.action + "'");
                    }
                    assertFalse(blank(c.label), where + ": blank label");
                }
            }
        }
    }

    @Test
    void openingChoicesNeedNoLabelButGoSomewhere() throws IOException {
        for (Path p : files()) {
            Script s = load(p);
            if (s.openings == null) continue;
            for (Choice o : s.openings) {
                assertNull(o.action, p + ": opening action");
                assertFalse(Boolean.TRUE.equals(o.end), p + ": opening end");
            }
        }
    }

    @Test
    void everyNodeIsReachable() throws IOException {
        for (Path p : files()) {
            Script s = load(p);
            Set<String> seen = new HashSet<>();
            ArrayDeque<String> q = new ArrayDeque<>();
            q.add(s.start);
            if (s.openings != null) for (Choice o : s.openings) q.add(o.go);
            while (!q.isEmpty()) {
                String n = q.poll();
                if (!seen.add(n)) continue;
                Node node = s.nodes.get(n);
                if (node == null || node.choices == null) continue;
                for (Choice c : node.choices) if (c.go != null) q.add(c.go);
            }
            Set<String> unreachable = new HashSet<>(s.nodes.keySet());
            unreachable.removeAll(seen);
            assertTrue(unreachable.isEmpty(), p.getFileName() + ": unreachable nodes " + unreachable);
        }
    }
}
