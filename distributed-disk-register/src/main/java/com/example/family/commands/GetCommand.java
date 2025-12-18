package com.example.family.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class GetCommand implements Command {
    private final Map<String, String> store;
    private final String id;

    public GetCommand(Map<String, String> store, String id) {
        this.store = store;
        this.id = id;
    }

    @Override
    public String execute() {
        String value = store.get(id);
        if (value != null) {
            return value;
        }

        Path file = Path.of("messages", id + ".msg");
        if (!Files.exists(file)) {
            return "NOT_FOUND";
        }

        try {
            value = Files.readString(file, StandardCharsets.UTF_8);
            store.put(id, value);
            return value;
        } catch (IOException e) {
            return "ERROR";
        }
    }
}
