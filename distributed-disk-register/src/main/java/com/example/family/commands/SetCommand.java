package com.example.family.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public class SetCommand implements Command {
    private final Map<String, String> store;
    private final String id;
    private final String value;

    public SetCommand(Map<String, String> store, String id, String value) {
        this.store = store;
        this.id = id;
        this.value = value;
    }

    @Override
    public String execute() {
        store.put(id, value);

        try {
            Path messagesDir = Path.of("messages");
            Files.createDirectories(messagesDir);

            Path file = messagesDir.resolve(id + ".msg");
            Files.writeString(
                    file,
                    value,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            return "ERROR";
        }

        return "OK";
    }
}
