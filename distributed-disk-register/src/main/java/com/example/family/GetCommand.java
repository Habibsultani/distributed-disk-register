package com.example.family;

public final class GetCommand implements Command {
    private final int id;

    public GetCommand(int id) { this.id = id; }

    public int getId() { return id; }

    @Override public Type getType() { return Type.GET; }
}
