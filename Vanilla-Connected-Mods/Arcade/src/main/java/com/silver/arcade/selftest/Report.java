package com.silver.arcade.selftest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The self-test result: every check with its outcome, written as JSON at the end (and a progress file along the way). */
public final class Report {
    public static final class Check {
        public String suite;
        public String check;
        public boolean ok;
        public long ms;
        public String detail;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String id;
    public String server;
    public String startedAt;
    public String finishedAt;
    public long durationMs;
    public boolean passed;
    public String refused;
    public List<String> suitesRun = new ArrayList<>();
    public Map<String, int[]> totals = new LinkedHashMap<>();
    public final List<Check> checks = new ArrayList<>();
    public final List<String> notes = new ArrayList<>();

    public synchronized void add(String suite, String name, boolean ok, long ms, String detail) {
        Check c = new Check();
        c.suite = suite;
        c.check = name;
        c.ok = ok;
        c.ms = ms;
        c.detail = detail == null ? "" : detail;
        checks.add(c);
    }

    public synchronized void finish() {
        totals.clear();
        for (Check c : checks) {
            int[] t = totals.computeIfAbsent(c.suite, s -> new int[2]);
            t[c.ok ? 0 : 1]++;
        }
        passed = refused == null && !checks.isEmpty() && checks.stream().allMatch(c -> c.ok);
    }

    public synchronized String json() {
        // totals as {suite: {pass, fail}} for readability
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", id);
        view.put("server", server);
        view.put("startedAt", startedAt);
        view.put("finishedAt", finishedAt);
        view.put("durationMs", durationMs);
        view.put("passed", passed);
        if (refused != null) {
            view.put("refused", refused);
        }
        view.put("suitesRun", suitesRun);
        Map<String, Object> t = new LinkedHashMap<>();
        totals.forEach((k, v) -> t.put(k, Map.of("pass", v[0], "fail", v[1])));
        view.put("totals", t);
        view.put("notes", notes);
        view.put("checks", checks);
        return GSON.toJson(view);
    }
}
