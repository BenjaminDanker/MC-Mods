package com.silver.authorization;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical Brigadier literal path. Argument nodes are deliberately not part of a policy key. */
public record CommandPath(List<String> literals) implements Comparable<CommandPath> {
    // Brigadier command literals include symbols (for example execute score operators `<=`
    // and vanilla recipe wildcards `*`), not only identifier characters.
    private static final Pattern LITERAL = Pattern.compile("[^\\s\\p{Cntrl}]{1,128}");

    public CommandPath {
        Objects.requireNonNull(literals, "literals");
        if (literals.isEmpty() || literals.size() > 32) {
            throw new IllegalArgumentException("Command path must contain 1..32 literal nodes");
        }
        List<String> canonical = new ArrayList<>(literals.size());
        for (String literal : literals) {
            Objects.requireNonNull(literal, "literal");
            String value = literal.toLowerCase(Locale.ROOT);
            if (!LITERAL.matcher(value).matches()) {
                throw new IllegalArgumentException("Invalid command literal: " + literal);
            }
            canonical.add(value);
        }
        literals = List.copyOf(canonical);
    }

    /** Parses a slash-prefixed command path, removing exactly one leading slash. */
    public static CommandPath of(String commandPath) {
        Objects.requireNonNull(commandPath, "commandPath");
        String text = commandPath.strip();
        if (text.startsWith("/")) text = text.substring(1);
        String[] parts = text.split("\\s+");
        return new CommandPath(List.of(parts));
    }

    public static CommandPath ofLiterals(List<String> literals) {
        return new CommandPath(literals);
    }

    public CommandPath child(String literal) {
        List<String> extended = new ArrayList<>(literals);
        extended.add(literal);
        return new CommandPath(extended);
    }

    public boolean isRoot() { return literals.size() == 1; }

    public String root() { return literals.get(0); }

    public String canonical() {
        return "/" + String.join(" ", literals);
    }

    @Override
    public int compareTo(CommandPath other) {
        return canonical().compareTo(other.canonical());
    }

    @Override
    public String toString() { return canonical(); }
}
