package com.example.family;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ToleranceConfig {

    private static final String KEY = "TOLERANCE";
    private static final int DEFAULT_TOLERANCE = 1;

    private ToleranceConfig() {
    }

    public static int load(Path path) {
        try {
            if (!Files.exists(path)) {
                return DEFAULT_TOLERANCE;
            }

            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int eqIndex = trimmed.indexOf('=');
                if (eqIndex <= 0) {
                    continue;
                }

                String key = trimmed.substring(0, eqIndex).trim();
                String value = trimmed.substring(eqIndex + 1).trim();

                if (!KEY.equalsIgnoreCase(key)) {
                    continue;
                }

                int parsed = Integer.parseInt(value);
                if (parsed < 1) {
                    return DEFAULT_TOLERANCE;
                }
                return parsed;
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Failed to read tolerance.conf: " + e.getMessage());
        }

        return DEFAULT_TOLERANCE;
    }
}
