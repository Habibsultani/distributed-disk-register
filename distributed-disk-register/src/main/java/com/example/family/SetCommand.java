package com.example.family;

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
        return "OK";
    }
}
