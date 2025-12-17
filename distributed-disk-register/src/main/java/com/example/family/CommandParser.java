package com.example.family;

import java.util.Locale;


public class CommandParser {
	public static Command parse(String line) {
		if (line == null) return null;
		String trimmed = line.trim();
		if (trimmed.isEmpty()) return null;

		// Split into at most 3 parts so that SET message can contain spaces
		String[] parts = trimmed.split("\\s+", 3);
		String op = parts[0].toUpperCase(Locale.ROOT);

		try {
			switch (op) {
				case "SET":
					if (parts.length < 3) return null;
					int idSet = Integer.parseInt(parts[1]);
					String msg = parts[2];
					return new SetCommand(idSet, msg);
				case "GET":
					if (parts.length < 2) return null;
					int idGet = Integer.parseInt(parts[1]);
					return new GetCommand(idGet);
				default:
					return null;
			}
		} catch (NumberFormatException e) {
			return null;
		}
	}
}

