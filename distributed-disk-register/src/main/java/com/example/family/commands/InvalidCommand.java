package com.example.family.commands;

public final class InvalidCommand implements Command {

    private final String error;

    public InvalidCommand(String error) {
        this.error = (error == null || error.isBlank()) ? "Invalid command" : error;
    }

    public String getError() {
        return error;
    }

    @Override
    public String execute() {
        return "ERROR";
    }

    @Override
    public String toString() {
        return "InvalidCommand{error='" + error + "'}";
    }
}
