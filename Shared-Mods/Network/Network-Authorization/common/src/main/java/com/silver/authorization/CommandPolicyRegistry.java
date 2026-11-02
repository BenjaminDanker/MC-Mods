package com.silver.authorization;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

/** Immutable origin-aware command-policy map with nearest-mapped-ancestor inheritance. */
public final class CommandPolicyRegistry {
    private final Map<CommandOrigin, Map<CommandPath, CommandPolicyEntry>> entries;
    private final Map<CommandOrigin, Map<String, RootFamily>> rootFamilies;

    public CommandPolicyRegistry(Collection<CommandPolicyEntry> entries) {
        this(entries, Map.of());
    }

    private CommandPolicyRegistry(Collection<CommandPolicyEntry> entries,
                                  Map<CommandOrigin, ? extends Map<String, RootFamily>> rootFamilies) {
        Objects.requireNonNull(entries, "entries");
        EnumMap<CommandOrigin, TreeMap<CommandPath, CommandPolicyEntry>> collected =
                new EnumMap<>(CommandOrigin.class);
        for (CommandPolicyEntry entry : entries) {
            Objects.requireNonNull(entry, "entry");
            TreeMap<CommandPath, CommandPolicyEntry> byPath =
                    collected.computeIfAbsent(entry.origin(), ignored -> new TreeMap<>());
            CommandPolicyEntry previous = byPath.putIfAbsent(entry.path(), entry);
            if (previous != null && !previous.equals(entry)) {
                throw new IllegalArgumentException("Conflicting command policies for "
                        + entry.origin() + " " + entry.path());
            }
        }
        EnumMap<CommandOrigin, Map<CommandPath, CommandPolicyEntry>> immutableEntries =
                new EnumMap<>(CommandOrigin.class);
        collected.forEach((origin, byPath) ->
                immutableEntries.put(origin, Collections.unmodifiableMap(new TreeMap<>(byPath))));
        this.entries = Collections.unmodifiableMap(immutableEntries);

        EnumMap<CommandOrigin, Map<String, RootFamily>> immutableFamilies = new EnumMap<>(CommandOrigin.class);
        rootFamilies.forEach((origin, families) ->
                immutableFamilies.put(origin, Collections.unmodifiableMap(new TreeMap<>(families))));
        this.rootFamilies = Collections.unmodifiableMap(immutableFamilies);
    }

    public Optional<CommandPolicyEntry> entry(CommandOrigin origin, CommandPath path) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(path, "path");
        return Optional.ofNullable(entries.getOrDefault(origin, Map.of()).get(path));
    }

    /** Returns the most-specific explicit mapping for this origin and path or its nearest mapped ancestor. */
    public Optional<CommandPolicyEntry> resolve(CommandOrigin origin, CommandPath path) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(path, "path");
        Map<CommandPath, CommandPolicyEntry> originEntries = entries.getOrDefault(origin, Map.of());
        for (int length = path.literals().size(); length >= 1; length--) {
            CommandPath candidate = CommandPath.ofLiterals(path.literals().subList(0, length));
            CommandPolicyEntry entry = originEntries.get(candidate);
            if (entry != null) return Optional.of(entry);
        }
        return rootFamilies.getOrDefault(origin, Map.of()).entrySet().stream()
                .filter(family -> path.root().startsWith(family.getKey()))
                .max(Map.Entry.comparingByKey(java.util.Comparator.comparingInt(String::length)))
                .map(family -> family.getValue().asEntry(origin, path.root()));
    }

    public Collection<CommandPolicyEntry> entries() {
        return entries.values().stream().flatMap(map -> map.values().stream())
                .sorted(java.util.Comparator.comparing(CommandPolicyEntry::origin)
                        .thenComparing(CommandPolicyEntry::path)).toList();
    }

    public CommandPolicyDecision decide(CommandOrigin origin, CommandPath path, boolean player, boolean owner,
                                        boolean internalExecution, boolean trustedConsole,
                                        Predicate<PermissionPattern> hasPermission) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(hasPermission, "hasPermission");
        CommandPolicyEntry entry = resolve(origin, path).orElse(null);
        CommandClassification classification = entry == null
                ? CommandClassification.UNMAPPED : entry.classification();
        Optional<PermissionPattern> permission = entry == null ? Optional.empty() : entry.permission();
        Optional<String> source = entry == null ? Optional.empty() : entry.source();
        Optional<String> modId = entry == null ? Optional.empty() : entry.modId();

        boolean visible;
        boolean allowed;
        boolean ownerOverride = false;
        switch (classification) {
            case PUBLIC -> visible = allowed = true;
            case PERMISSION -> {
                boolean granted = permission.filter(hasPermission).isPresent();
                // Deployed datapacks/functions retain their configured server-command source
                // authority. Player checks never inherit that trust; command blocks/RCON are
                // not marked internalExecution by the Fabric adapter.
                allowed = !player ? trustedConsole || internalExecution : owner || granted;
                visible = allowed;
                ownerOverride = player && owner && !granted;
            }
            case INTERNAL -> {
                visible = false;
                allowed = !player && (internalExecution || trustedConsole);
            }
            case UNMAPPED -> {
                visible = true;
                allowed = player ? owner : trustedConsole;
                ownerOverride = player && owner;
            }
            default -> throw new IllegalStateException("Unhandled command classification " + classification);
        }
        return new CommandPolicyDecision(origin, path, classification, permission, source, modId,
                visible, allowed, ownerOverride);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Map<CommandOrigin, Map<CommandPath, CommandPolicyEntry>> entries =
                new EnumMap<>(CommandOrigin.class);
        private final Map<CommandOrigin, Map<String, RootFamily>> rootFamilies =
                new EnumMap<>(CommandOrigin.class);

        public Builder add(String path, CommandClassification classification,
                           String permission, String source, String modId) {
            return add(CommandOrigin.FABRIC_BACKEND, path, classification, permission, source, modId);
        }

        public Builder add(CommandOrigin origin, String path, CommandClassification classification,
                           String permission, String source, String modId) {
            CommandPolicyEntry entry = CommandPolicyEntry.of(origin, path, classification, permission, source, modId);
            Map<CommandPath, CommandPolicyEntry> byPath =
                    entries.computeIfAbsent(origin, ignored -> new TreeMap<>());
            CommandPolicyEntry previous = byPath.putIfAbsent(entry.path(), entry);
            if (previous != null && !previous.equals(entry)) {
                throw new IllegalArgumentException("Conflicting command policies for "
                        + origin + " " + entry.path());
            }
            return this;
        }

        public Builder add(CommandPolicyEntry entry) {
            Objects.requireNonNull(entry, "entry");
            Map<CommandPath, CommandPolicyEntry> byPath =
                    entries.computeIfAbsent(entry.origin(), ignored -> new TreeMap<>());
            CommandPolicyEntry previous = byPath.putIfAbsent(entry.path(), entry);
            if (previous != null && !previous.equals(entry)) {
                throw new IllegalArgumentException("Conflicting command policies for "
                        + entry.origin() + " " + entry.path());
            }
            return this;
        }

        /** Maps roots whose literal begins with a known namespace prefix, such as WorldEdit's slash aliases. */
        public Builder addRootPrefix(String prefix, CommandClassification classification,
                                     String permission, String source, String modId) {
            return addRootPrefix(CommandOrigin.FABRIC_BACKEND, prefix, classification, permission, source, modId);
        }

        public Builder addRootPrefix(CommandOrigin origin, String prefix, CommandClassification classification,
                                     String permission, String source, String modId) {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(prefix, "prefix");
            String canonical = prefix.toLowerCase(java.util.Locale.ROOT);
            if (canonical.isEmpty() || canonical.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("Root prefix must be non-empty and contain no whitespace");
            }
            RootFamily family = new RootFamily(classification,
                    Optional.ofNullable(permission).map(PermissionPattern::of),
                    Optional.ofNullable(source), Optional.ofNullable(modId));
            Map<String, RootFamily> byPrefix =
                    rootFamilies.computeIfAbsent(origin, ignored -> new HashMap<>());
            RootFamily previous = byPrefix.putIfAbsent(canonical, family);
            if (previous != null && !previous.equals(family)) {
                throw new IllegalArgumentException("Conflicting command root-family policies for "
                        + origin + " " + canonical);
            }
            return this;
        }

        public CommandPolicyRegistry build() {
            return new CommandPolicyRegistry(entries.values().stream()
                    .flatMap(map -> map.values().stream()).toList(), rootFamilies);
        }
    }

    private record RootFamily(CommandClassification classification, Optional<PermissionPattern> permission,
                              Optional<String> source, Optional<String> modId) {
        private CommandPolicyEntry asEntry(CommandOrigin origin, String actualRoot) {
            return new CommandPolicyEntry(origin, CommandPath.ofLiterals(java.util.List.of(actualRoot)),
                    classification, permission, source, modId);
        }
    }
}
