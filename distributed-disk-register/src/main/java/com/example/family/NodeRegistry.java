package com.example.family;

import family.NodeInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class NodeRegistry {

    private final Set<NodeInfo> nodes = ConcurrentHashMap.newKeySet();
    private final AtomicInteger roundRobin = new AtomicInteger(0);

    public void add(NodeInfo node) {
        nodes.add(node);
    }

    public void addAll(Collection<NodeInfo> others) {
        nodes.addAll(others);
    }

    public List<NodeInfo> snapshot() {
        return List.copyOf(nodes);
    }

    public void remove(NodeInfo node) {
        nodes.remove(node);
    }

    /**
     * Selects up to {@code tolerance} replica nodes using a simple round-robin strategy.
     * The current node (self) is excluded from the selection.
     */
    public List<NodeInfo> selectReplicas(NodeInfo self, int tolerance) {
        if (tolerance <= 0) {
            return List.of();
        }

        List<NodeInfo> others = new ArrayList<>();
        for (NodeInfo node : nodes) {
            if (node.getHost().equals(self.getHost()) && node.getPort() == self.getPort()) {
                continue;
            }
            others.add(node);
        }

        if (others.isEmpty()) {
            return List.of();
        }

        others.sort(Comparator.comparing(NodeInfo::getHost).thenComparingInt(NodeInfo::getPort));

        int start = Math.floorMod(roundRobin.getAndIncrement(), others.size());
        List<NodeInfo> selection = new ArrayList<>();

        for (int i = 0; i < tolerance && i < others.size(); i++) {
            int idx = (start + i) % others.size();
            selection.add(others.get(idx));
        }

        return selection;
    }
}
