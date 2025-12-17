package com.example.family;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class InMemoryStore {
	private final Map<Integer, String> map = new ConcurrentHashMap<>();

	public void put(int id, String msg) {
		map.put(id, msg);
	}

	public String get(int id) {
		return map.get(id);
	}

	public boolean contains(int id) {
		return map.containsKey(id);
	}

	public void remove(int id) {
		map.remove(id);
	}
}

