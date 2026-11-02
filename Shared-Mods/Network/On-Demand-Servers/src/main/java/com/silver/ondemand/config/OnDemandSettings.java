package com.silver.ondemand.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Starting backend servers when a player needs them and stopping them when nobody is on.
 *
 * @param enabled     off by default: the proxy then only sends the Wake-on-LAN packet, as before
 * @param sshTarget   user@host of the machine that runs the servers (it runs a restricted control script for this key)
 * @param sshKey      private key file used for that connection
 * @param idleMinutes how long a server may stay empty before it is stopped
 * @param servers     the servers managed this way (never the holding lobby)
 * @param keepRunning servers (a subset of {@code servers}) that are started whenever they are down and never stopped for being idle
 */
public record OnDemandSettings(boolean enabled, String sshTarget, String sshKey, long idleMinutes, Set<String> servers, Set<String> keepRunning) {
    public static final OnDemandSettings DISABLED = new OnDemandSettings(false, "", "", 10, Set.of(), Set.of());

    public OnDemandSettings {
        servers = Set.copyOf(servers);
        keepRunning = Set.copyOf(keepRunning);
    }

    public OnDemandSettings(boolean enabled, String sshTarget, String sshKey, long idleMinutes, Set<String> servers) {
        this(enabled, sshTarget, sshKey, idleMinutes, servers, Set.of());
    }

    public boolean keeps(String server) {
        return manages(server) && keepRunning.contains(server);
    }

    public boolean manages(String server) {
        return enabled && servers.contains(server);
    }

    /** on_demand: {enabled, ssh_target, ssh_key, idle_minutes, servers: [..], keep_running: [..]}; missing or malformed means off. */
    public static OnDemandSettings parse(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return DISABLED;
        }
        boolean enabled = Boolean.parseBoolean(Objects.toString(map.get("enabled"), "false"));
        String target = Objects.toString(map.get("ssh_target"), "").trim();
        String key = Objects.toString(map.get("ssh_key"), "").trim();
        long idle = map.get("idle_minutes") == null ? 10 : Math.max(1, asLong(map.get("idle_minutes")));
        List<String> servers = stringList(map.get("servers"));
        if (enabled && (target.isEmpty() || key.isEmpty() || servers.isEmpty())) {
            return DISABLED;
        }
        return new OnDemandSettings(enabled, target, key, idle, new HashSet<>(servers), new HashSet<>(stringList(map.get("keep_running"))));
    }

    private static List<String> stringList(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                if (element == null) {
                    continue;
                }
                String s = element.toString().trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(Objects.toString(value));
    }
}
