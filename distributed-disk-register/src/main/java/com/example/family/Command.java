package com.example.family;

public interface Command {
    enum Type { SET, GET }
    Type getType();
}
