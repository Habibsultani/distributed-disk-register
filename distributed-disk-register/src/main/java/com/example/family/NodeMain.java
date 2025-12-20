package com.example.family;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.example.family.commands.Command;
import com.example.family.commands.CommandParser;

import family.Empty;
import family.FamilyServiceGrpc;
import family.FamilyView;
import family.MessageId;
import family.NodeInfo;
import family.StoredMessage;
import family.StoreResult;
import family.StorageServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;

public class NodeMain {

    // messageId -> bu mesaj hangi node'larda var
    private static final java.util.concurrent.ConcurrentHashMap<Integer, List<NodeInfo>>
            MESSAGE_TO_MEMBERS = new java.util.concurrent.ConcurrentHashMap<>();

    // TCP SET/GET için lokal store (StorageServiceImpl bunun üstünden yazıyor/okuyor)
    private static final Map<String, String> STORE = new java.util.concurrent.ConcurrentHashMap<>();

    private static final int START_PORT = 5555;
    private static final int PRINT_INTERVAL_SECONDS = 10;

    private static final Path TOLERANCE_CONF_PATH = resolveToleranceConfPath();
    private static final int TOLERANCE = normalizeTolerance(ToleranceConfig.load(TOLERANCE_CONF_PATH));

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = findFreePort(START_PORT);

        NodeInfo self = NodeInfo.newBuilder()
                .setHost(host)
                .setPort(port)
                .build();

        NodeRegistry registry = new NodeRegistry();
        FamilyServiceImpl service = new FamilyServiceImpl(registry, self);

        StorageServiceImpl storageService = new StorageServiceImpl(STORE);

        Server server = ServerBuilder
                .forPort(port)
                .addService(service)
                .addService(storageService)
                .build()
                .start();

        System.out.printf("Node started on %s:%d%n", host, port);
        System.out.printf("Configured tolerance level: %d%n", TOLERANCE);

        // sadece leader TCP 6666 dinler
        if (port == START_PORT) {
            startLeaderTextListener(registry, self);
        }

        discoverExistingNodes(host, port, registry, self);
        startFamilyPrinter(registry, self);
        startHealthChecker(registry, self);

        server.awaitTermination();
    }

    // =========================================================
    // Leader TCP listener (SET / GET)
    // =========================================================

    private static void startLeaderTextListener(NodeRegistry registry, NodeInfo self) {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(6666)) {
                System.out.printf("Leader listening for text on TCP %s:%d%n", self.getHost(), 6666);

                while (true) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleClientTextConnection(client, registry, self)).start();
                }

            } catch (IOException e) {
                System.err.println("Error in leader text listener: " + e.getMessage());
            }
        }, "LeaderTextListener").start();
    }

    private static void handleClientTextConnection(Socket client,
                                                   NodeRegistry registry,
                                                   NodeInfo self) {
        System.out.println("New TCP client connected: " + client.getRemoteSocketAddress());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
             java.io.PrintWriter out = new java.io.PrintWriter(client.getOutputStream(), true)) {

            CommandParser parser = new CommandParser(STORE);

            String line;
            while ((line = reader.readLine()) != null) {
                String text = line.trim();
                if (text.isEmpty()) continue;

                boolean isLeader = (self.getPort() == START_PORT);

                Command cmd = parser.parse(text);
                String localResponse = cmd.execute(); // OK / NOT_FOUND / ERROR
                String finalResponse = localResponse;

                // -------------------------
                // SET: Leader replication
                // -------------------------
                if (isLeader && "OK".equals(localResponse) && text.toUpperCase().startsWith("SET ")) {
                    String[] parts = text.split("\\s+", 3); // SET id msg
                    if (parts.length == 3) {
                        try {
                            int id = Integer.parseInt(parts[1]);
                            String message = parts[2];

                            // leader kendinde zaten yazdı (cmd.execute ile)
                            trackStoredAt(id, self);

                            int need = TOLERANCE; // kaç replika

                            List<NodeInfo> targets = selectReplicas(registry, self, need, id);

                            if (targets.size() < need) {
                                finalResponse = "ERROR";
                                System.out.println("[REPL] Not enough members. need=" + need + " available=" + targets.size());
                            } else {
                                boolean allOk = replicateStoreToTargets(id, message, targets, registry);
                                finalResponse = allOk ? "OK" : "ERROR";
                                printMappingFor(id);
                            }
                        } catch (NumberFormatException e) {
                            finalResponse = "ERROR";
                        }
                    } else {
                        finalResponse = "ERROR";
                    }
                }

                // -------------------------
                // GET: leader önce local, yoksa mapping ile üyelerden dene
                // Crash (exception) olursa -> dead/remove -> diğer üyeyi dene
                // -------------------------
                if (isLeader && text.toUpperCase().startsWith("GET ")) {
                    String[] parts = text.split("\\s+");
                    if (parts.length == 2) {
                        try {
                            int id = Integer.parseInt(parts[1]);

                            String localDisk = readFromLocalDisk(id);
                            if (localDisk != null) {
                                System.out.println("[GET] Local disk hit id=" + id);
                                finalResponse = localDisk;
                            } else {
                                System.out.println("[GET] Local disk miss id=" + id + ", trying members...");
                                String fromMember = retrieveFromMembersUsingMapping(id, self, registry);
                                if (fromMember == null) {
                                    System.out.println("[GET] No live replica found in mapping, trying failover across all members...");
                                    fromMember = retrieveWithFailover(id, self, registry);
                                }
                                if (fromMember == null) {
                                    System.out.println("[GET] NOT_FOUND id=" + id + " (not on local disk and not on any member)");
                                }
                                finalResponse = (fromMember != null) ? fromMember : "NOT_FOUND";
                            }

                        } catch (NumberFormatException e) {
                            finalResponse = "ERROR";
                        }
                    } else {
                        finalResponse = "ERROR";
                    }
                }

                out.println(finalResponse);
                System.out.println("TCP> " + text + "  =>  " + finalResponse);
            }

        } catch (IOException e) {
            System.err.println("TCP client handler error: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    // =========================================================
    // Replica selection (Rendezvous / HRW) -> daha dengeli dağılım
    // =========================================================

    private static final class ScoredNode {
        final NodeInfo node;
        final long score;
        ScoredNode(NodeInfo node, long score) {
            this.node = node;
            this.score = score;
        }
    }

    /**
     * Deterministik ve dengeli replika seçimi:
     * - aynı (members + id) => aynı target listesi
     * - üyeler arası dağılım genelde dengeli (1000 SET'te bariz kayma olmaz)
     */
    private static List<NodeInfo> selectReplicas(NodeRegistry registry, NodeInfo self, int need, int messageId) {
        if (need <= 0) return java.util.Collections.emptyList();

        List<NodeInfo> members = registry.snapshot();
        List<NodeInfo> candidates = new ArrayList<NodeInfo>();
        for (NodeInfo n : members) {
            if (!sameMember(n, self)) {
                candidates.add(n);
            }
        }
        if (candidates.isEmpty()) return java.util.Collections.emptyList();

        List<ScoredNode> scored = new ArrayList<ScoredNode>(candidates.size());
        for (NodeInfo n : candidates) {
            scored.add(new ScoredNode(n, score(messageId, n)));
        }

        scored.sort(Comparator
                .comparingLong((ScoredNode sn) -> sn.score).reversed()
                .thenComparing(sn -> sn.node.getHost())
                .thenComparingInt(sn -> sn.node.getPort()));

        int take = Math.min(need, scored.size());
        List<NodeInfo> out = new ArrayList<NodeInfo>(take);
        for (int i = 0; i < take; i++) {
            out.add(scored.get(i).node);
        }
        return out;
    }

    // Score: messageId + node(host+port) -> uniform'a yakın 64-bit skor
    private static long score(int messageId, NodeInfo node) {
        long k = 0x9E3779B97F4A7C15L;
        k ^= ((long) messageId) * 0xD6E8FEB86659FD93L;
        k ^= ((long) node.getPort()) * 0xA5A35625AA0F5A5BL;
        k ^= ((long) node.getHost().hashCode()) * 0x9E3779B185EBCA87L;
        return splitMix64(k);
    }

    // SplitMix64 finalizer (küçük, hızlı, çok iyi dağılım)
    private static long splitMix64(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // =========================================================
    // Replication
    // =========================================================

    private static boolean replicateStoreToTargets(int id, String message, List<NodeInfo> targets, NodeRegistry registry) {
        StoredMessage sm = StoredMessage.newBuilder()
                .setId(id)
                .setText(message)
                .build();

        boolean allOk = true;

        for (NodeInfo t : targets) {
            boolean ok = storeOnMember(t, sm, registry);
            if (ok) {
                trackStoredAt(id, t);
            } else {
                allOk = false;
            }
        }

        return allOk;
    }

    private static boolean storeOnMember(NodeInfo member, StoredMessage msg, NodeRegistry registry) {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder
                    .forAddress(member.getHost(), member.getPort())
                    .usePlaintext()
                    .build();

            StorageServiceGrpc.StorageServiceBlockingStub stub = StorageServiceGrpc.newBlockingStub(channel);
            StoreResult res = stub.store(msg);

            if (res.getOk()) {
                System.out.println("[REPL] Store OK on " + member.getHost() + ":" + member.getPort());
                return true;
            } else {
                System.out.println("[REPL] Store failed on " + member.getHost() + ":" + member.getPort()
                        + " error=" + res.getError());
                return false;
            }

        } catch (StatusRuntimeException e) {
            System.out.println("[REPL] Store RPC error on " + member.getHost() + ":" + member.getPort()
                    + " status=" + e.getStatus());
            registry.remove(member);
            removeMemberFromAllMappings(member);
            return false;
        } catch (Exception e) {
            System.out.println("[REPL] Store exception on " + member.getHost() + ":" + member.getPort()
                    + " msg=" + e.getMessage());
            registry.remove(member);
            removeMemberFromAllMappings(member);
            return false;
        } finally {
            if (channel != null) channel.shutdownNow();
        }
    }

    // =========================================================
    // GET from members (mapping + crash recovery)
    // =========================================================

    private static String retrieveFromMembersUsingMapping(int id, NodeInfo self, NodeRegistry registry) {
        List<NodeInfo> holders = MESSAGE_TO_MEMBERS.get(id);
        if (holders == null || holders.isEmpty()) {
            System.out.println("[GET] No mapping found for id=" + id);
            return null;
        }

        System.out.println("[GET] Trying mapped replicas for id=" + id + " (count=" + holders.size() + ")");

        for (NodeInfo m : holders) {
            if (sameMember(m, self)) continue;

            String text = retrieveFromMember(m, id, registry);
            if (text != null) return text;
        }
        return null;
    }

    // mapping yoksa / hepsi düşmüşse: tüm üyeleri dene
    private static String retrieveWithFailover(int id, NodeInfo self, NodeRegistry registry) {
        System.out.println("[GET] Failover: scanning all family members for id=" + id);
        List<NodeInfo> members = registry.snapshot();
        if (members.isEmpty()) return null;

        for (NodeInfo n : members) {
            if (sameMember(n, self)) continue;
            String text = retrieveFromMember(n, id, registry);
            if (text != null) {
                trackStoredAt(id, n);
                return text;
            }
        }
        return null;
    }

    private static String retrieveFromMember(NodeInfo member, int id, NodeRegistry registry) {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder
                    .forAddress(member.getHost(), member.getPort())
                    .usePlaintext()
                    .build();

            StorageServiceGrpc.StorageServiceBlockingStub stub = StorageServiceGrpc.newBlockingStub(channel);

            StoredMessage got = stub.retrieve(MessageId.newBuilder().setId(id).build());

            String text = got.getText();
            if (text == null || text.isEmpty()) return null;

            System.out.println("[GET] Retrieved from " + member.getHost() + ":" + member.getPort());
            return text;

        } catch (StatusRuntimeException e) {
            System.out.println("[GET] Retrieve RPC error on " + member.getHost() + ":" + member.getPort()
                    + " status=" + e.getStatus());
            registry.remove(member);
            removeMemberFromAllMappings(member);
            return null;
        } catch (Exception e) {
            System.out.println("[GET] Retrieve exception on " + member.getHost() + ":" + member.getPort()
                    + " msg=" + e.getMessage());
            registry.remove(member);
            removeMemberFromAllMappings(member);
            return null;
        } finally {
            if (channel != null) channel.shutdownNow();
        }
    }

    // =========================================================
    // Local disk read (messages/<id>.msg)
    // =========================================================

    private static String readFromLocalDisk(int id) {
        Path p = Path.of("messages", id + ".msg");
        try {
            if (!Files.exists(p)) return null;
            String s = Files.readString(p, StandardCharsets.UTF_8);
            if (s == null) return null;
            s = s.trim();
            return s.isEmpty() ? null : s;
        } catch (IOException e) {
            return null;
        }
    }

    // =========================================================
    // Discovery & Health
    // =========================================================

    private static int findFreePort(int startPort) {
        int port = startPort;
        while (true) {
            try (ServerSocket ignored = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                port++;
            }
        }
    }

    private static void discoverExistingNodes(String host, int selfPort, NodeRegistry registry, NodeInfo self) {
        for (int port = START_PORT; port < selfPort; port++) {
            ManagedChannel channel = null;
            try {
                channel = ManagedChannelBuilder
                        .forAddress(host, port)
                        .usePlaintext()
                        .build();

                FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(channel);
                FamilyView view = stub.join(self);

                registry.addAll(view.getMembersList());

                System.out.printf("Joined through %s:%d, family size now: %d%n",
                        host, port, registry.snapshot().size());

            } catch (Exception ignored) {
            } finally {
                if (channel != null) channel.shutdownNow();
            }
        }
    }

    private static void startFamilyPrinter(NodeRegistry registry, NodeInfo self) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            List<NodeInfo> members = registry.snapshot();

            System.out.println("======================================");
            System.out.printf("Family at %s:%d (me)%n", self.getHost(), self.getPort());
            System.out.println("Time: " + LocalDateTime.now());
            System.out.println("Members:");

            for (NodeInfo n : members) {
                boolean isMe = sameMember(n, self);
                System.out.printf(" - %s:%d%s%n", n.getHost(), n.getPort(), isMe ? " (me)" : "");
            }

            Map<NodeInfo, Integer> counts = computeMemberMessageCounts();
            if (!counts.isEmpty()) {
                System.out.println("Message counts:");
                for (NodeInfo n : members) {
                    int count = counts.getOrDefault(n, 0);
                    System.out.printf(" - %s:%d -> %d message%s%n",
                            n.getHost(), n.getPort(), count, (count == 1 ? "" : "s"));
                }
            }

            System.out.println("======================================");
        }, 3, PRINT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private static void startHealthChecker(NodeRegistry registry, NodeInfo self) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            List<NodeInfo> members = registry.snapshot();

            for (NodeInfo n : members) {
                if (sameMember(n, self)) continue;

                ManagedChannel channel = null;
                try {
                    channel = ManagedChannelBuilder
                            .forAddress(n.getHost(), n.getPort())
                            .usePlaintext()
                            .build();

                    FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(channel);
                    stub.getFamily(Empty.newBuilder().build());

                } catch (Exception e) {
                    System.out.printf("Node %s:%d unreachable, removing from family%n", n.getHost(), n.getPort());
                    registry.remove(n);

                    // leader mapping'i de temizlesin
                    if (self.getPort() == START_PORT) {
                        removeMemberFromAllMappings(n);
                    }
                } finally {
                    if (channel != null) channel.shutdownNow();
                }
            }
        }, 5, 10, TimeUnit.SECONDS);
    }

    private static Map<NodeInfo, Integer> computeMemberMessageCounts() {
        Map<NodeInfo, Integer> counts = new HashMap<NodeInfo, Integer>();

        for (Map.Entry<Integer, List<NodeInfo>> entry : MESSAGE_TO_MEMBERS.entrySet()) {
            List<NodeInfo> list = entry.getValue();
            if (list == null) continue;

            for (NodeInfo n : list) {
                counts.put(n, counts.getOrDefault(n, 0) + 1);
            }
        }

        return counts;
    }

    // =========================================================
    // Mapping utils
    // =========================================================

    private static void trackStoredAt(int id, NodeInfo member) {
        MESSAGE_TO_MEMBERS.compute(id, (k, v) -> {
            if (v == null) v = new CopyOnWriteArrayList<NodeInfo>();
            boolean exists = false;
            for (NodeInfo m : v) {
                if (sameMember(m, member)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) v.add(member);
            return v;
        });
    }

    private static void removeMemberFromAllMappings(NodeInfo dead) {
        for (Map.Entry<Integer, List<NodeInfo>> entry : MESSAGE_TO_MEMBERS.entrySet()) {
            List<NodeInfo> list = entry.getValue();
            if (list == null) continue;

            list.removeIf(m -> sameMember(m, dead));
            if (list.isEmpty()) {
                MESSAGE_TO_MEMBERS.remove(entry.getKey(), list);
            }
        }
    }

    private static boolean sameMember(NodeInfo a, NodeInfo b) {
        return a.getHost().equals(b.getHost()) && a.getPort() == b.getPort();
    }

    private static void printMappingFor(int id) {
        List<NodeInfo> list = MESSAGE_TO_MEMBERS.get(id);
        if (list == null || list.isEmpty()) {
            System.out.println("[MAPPING] id=" + id + " -> (empty)");
            return;
        }

        String members = list.stream()
                .map(m -> m.getHost() + ":" + m.getPort())
                .collect(Collectors.joining(", "));

        System.out.println("[MAPPING] id=" + id + " -> " + members);
    }

    // =========================================================
    // Config helpers
    // =========================================================

    private static Path resolveToleranceConfPath() {
        Path p1 = Path.of("tolerance.conf");
        if (Files.exists(p1)) return p1;

        Path p2 = Path.of("distributed-disk-register", "tolerance.conf");
        if (Files.exists(p2)) return p2;

        return p1;
    }

    private static int normalizeTolerance(int raw) {
        if (raw < 1) return 1;
        if (raw > 7) return 7;
        return raw;
    }
}
