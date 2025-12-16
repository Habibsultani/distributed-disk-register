package com.example.family;

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
        if (value == null) {
            return "NOT_FOUND";
        }
        return value;
    }
}
