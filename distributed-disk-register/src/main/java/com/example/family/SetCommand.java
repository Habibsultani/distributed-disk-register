package com.example.family;

public final class SetCommand implements Command {
    private final int id;
    private final String message;

    public SetCommand(int id, String message) {
        this.id = id;
        this.message = message;
    }

    public int getId() { return id; }
    public String getMessage() { return message; }

    @Override public Type getType() { return Type.SET; }
}
