package com.example.family.commands;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandParser {

    // SET <id> <message...>  (message must be non-empty)
    private static final Pattern SET_PATTERN =
            Pattern.compile("(?i)^SET\\s+(\\d+)\\s+(.+)$");

    // GET <id> (no extra tokens)
    private static final Pattern GET_PATTERN =
            Pattern.compile("(?i)^GET\\s+(\\d+)\\s*$");

    private final Map<String, String> store;

    public CommandParser(Map<String, String> store) {
        this.store = store;
    }

    public Command parse(String line) {
        if (line == null) {
            return new InvalidCommand("Input is null");
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return new InvalidCommand("Empty command");
        }

        Matcher get = GET_PATTERN.matcher(trimmed);
        if (get.matches()) {
            String id = get.group(1);
            return new GetCommand(store, id);
        }

        Matcher set = SET_PATTERN.matcher(trimmed);
        if (set.matches()) {
            String id = set.group(1);
            String message = set.group(2).trim();
            if (message.isEmpty()) {
                return new InvalidCommand("SET requires a non-empty message");
            }
            return new SetCommand(store, id, message);
        }

        String first = trimmed.split("\\s+")[0].toUpperCase();
        if ("GET".equals(first)) {
            return new InvalidCommand("Invalid GET format. Expected: GET <id>");
        }
        if ("SET".equals(first)) {
            return new InvalidCommand("Invalid SET format. Expected: SET <id> <message>");
        }

        return new InvalidCommand("Unknown command: " + first);
    }
}
