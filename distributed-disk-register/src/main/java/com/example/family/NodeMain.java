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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    // id -> bu mesaj hangi node'larda var
    private static final java.util.concurrent.ConcurrentHashMap<Integer, java.util.List<NodeInfo>>
            MESSAGE_TO_MEMBERS = new java.util.concurrent.ConcurrentHashMap<>();

    // TCP SET/GET (Stage-1) için store
    private static final java.util.Map<String, String> STORE =
            new java.util.concurrent.ConcurrentHashMap<>();

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

        // Sadece leader TCP 6666 dinler
        if (port == START_PORT) {
            startLeaderTextListener(registry, self);
        }

        discoverExistingNodes(host, port, registry, self);
        startFamilyPrinter(registry, self);
        startHealthChecker(registry, self);

        server.awaitTermination();
    }

    private static void startLeaderTextListener(NodeRegistry registry, NodeInfo self) {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(6666)) {
                System.out.printf("Leader listening for text on TCP %s:%d%n",
                        self.getHost(), 6666);

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

                // =========================
                // SET: Leader replication (tolerance 1..7)
                // =========================
                if (isLeader && localResponse.equals("OK") && text.toUpperCase().startsWith("SET ")) {
                    String[] parts = text.split("\\s+", 3); // SET id msg
                    if (parts.length == 3) {
                        try {
                            int id = Integer.parseInt(parts[1]);
                            String message = parts[2];

                            // mapping: leader kendinde var
                            trackStoredAt(id, self);

                            // ARTIK 1..7: tolerance kadar üye
                            int need = TOLERANCE; // 1..7
                            List<NodeInfo> targets = selectTargets(registry, self, need);

                            if (targets.size() < need) {
                                finalResponse = "ERROR";
                                System.out.println("[REPL] Not enough members. need=" + need +
                                        " available=" + targets.size());
                            } else {
                                boolean allOk = replicateStoreToTargets(id, message, targets);
                                finalResponse = allOk ? "OK" : "ERROR";
                                printMappingFor(id);
                            }

                        } catch (NumberFormatException ignored) {
                            finalResponse = "ERROR";
                        }
                    } else {
                        finalResponse = "ERROR";
                    }
                }

                // =========================
                // GET: Leader-first disk, else members by mapping
                // =========================
                if (isLeader && text.toUpperCase().startsWith("GET ")) {
                    String[] parts = text.split("\\s+");
                    if (parts.length == 2) {
                        try {
                            int id = Integer.parseInt(parts[1]);

                            String localDisk = readFromLocalDisk(id);
                            if (localDisk != null) {
                                finalResponse = localDisk;
                            } else {
                                String fromMember = retrieveFromMembersUsingMapping(id, self);
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

    // ===== Selection =====

    private static List<NodeInfo> selectTargets(NodeRegistry registry, NodeInfo self, int need) {
        List<NodeInfo> members = registry.snapshot();
        List<NodeInfo> candidates = new ArrayList<>();

        for (NodeInfo n : members) {
            if (sameMember(n, self)) continue;
            candidates.add(n);
        }

        // En basit seçim: ilk N üye
        if (candidates.size() <= need) return candidates;
        return candidates.subList(0, need);
    }

    // ===== Replication =====

    private static boolean replicateStoreToTargets(int id, String message, List<NodeInfo> targets) {
        StoredMessage sm = StoredMessage.newBuilder()
                .setId(id)
                .setText(message)
                .build();

        boolean allOk = true;

        for (NodeInfo t : targets) {
            boolean ok = storeOnMember(t, sm);
            if (ok) {
                trackStoredAt(id, t);
            } else {
                allOk = false;
            }
        }

        return allOk;
    }

    private static boolean storeOnMember(NodeInfo member, StoredMessage msg) {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder
                    .forAddress(member.getHost(), member.getPort())
                    .usePlaintext()
                    .build();

            StorageServiceGrpc.StorageServiceBlockingStub stub =
                    StorageServiceGrpc.newBlockingStub(channel);

            StoreResult res = stub.store(msg);
            boolean ok = res.getOk();

            if (!ok) {
                System.out.println("[REPL] Store failed on " + member.getHost() + ":" + member.getPort()
                        + " error=" + res.getError());
            } else {
                System.out.println("[REPL] Store OK on " + member.getHost() + ":" + member.getPort());
            }

            return ok;

        } catch (StatusRuntimeException e) {
            System.out.println("[REPL] Store RPC error on " + member.getHost() + ":" + member.getPort()
                    + " status=" + e.getStatus());
            return false;
        } catch (Exception e) {
            System.out.println("[REPL] Store exception on " + member.getHost() + ":" + member.getPort()
                    + " msg=" + e.getMessage());
            return false;
        } finally {
            if (channel != null) channel.shutdownNow();
        }
    }

    // ===== GET from members =====

    private static String retrieveFromMembersUsingMapping(int id, NodeInfo self) {
        List<NodeInfo> holders = MESSAGE_TO_MEMBERS.get(id);
        if (holders == null || holders.isEmpty()) return null;

        for (NodeInfo m : holders) {
            if (sameMember(m, self)) continue;

            String text = retrieveFromMember(m, id);
            if (text != null) return text;
        }
        return null;
    }

    private static String retrieveFromMember(NodeInfo member, int id) {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder
                    .forAddress(member.getHost(), member.getPort())
                    .usePlaintext()
                    .build();

            StorageServiceGrpc.StorageServiceBlockingStub stub =
                    StorageServiceGrpc.newBlockingStub(channel);

            StoredMessage got = stub.retrieve(MessageId.newBuilder().setId(id).build());

            String text = got.getText();
            if (text == null || text.isEmpty()) return null;

            System.out.println("[GET] Retrieved from " + member.getHost() + ":" + member.getPort());
            return text;

        } catch (StatusRuntimeException e) {
            System.out.println("[GET] Retrieve RPC error on " + member.getHost() + ":" + member.getPort()
                    + " status=" + e.getStatus());
            return null;
        } catch (Exception e) {
            System.out.println("[GET] Retrieve exception on " + member.getHost() + ":" + member.getPort()
                    + " msg=" + e.getMessage());
            return null;
        } finally {
            if (channel != null) channel.shutdownNow();
        }
    }

    // ===== Local disk read =====

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

    // ===== Discovery / health =====

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

    private static void discoverExistingNodes(String host,
                                              int selfPort,
                                              NodeRegistry registry,
                                              NodeInfo self) {

        for (int port = START_PORT; port < selfPort; port++) {
            ManagedChannel channel = null;
            try {
                channel = ManagedChannelBuilder
                        .forAddress(host, port)
                        .usePlaintext()
                        .build();

                FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                        FamilyServiceGrpc.newBlockingStub(channel);

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
                System.out.printf(" - %s:%d%s%n",
                        n.getHost(),
                        n.getPort(),
                        isMe ? " (me)" : "");
            }

            Map<NodeInfo, Integer> counts = computeMemberMessageCounts();
            if (!counts.isEmpty()) {
                System.out.println("Message counts:");
                for (NodeInfo n : members) {
                    int count = counts.getOrDefault(n, 0);
                    System.out.printf(" - %s:%d -> %d message%s%n",
                            n.getHost(),
                            n.getPort(),
                            count,
                            (count == 1 ? "" : "s"));
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

                    FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                            FamilyServiceGrpc.newBlockingStub(channel);

                    stub.getFamily(Empty.newBuilder().build());

                } catch (Exception e) {
                    System.out.printf("Node %s:%d unreachable, removing from family%n",
                            n.getHost(), n.getPort());
                    registry.remove(n);

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
        Map<NodeInfo, Integer> counts = new HashMap<>();

        for (Map.Entry<Integer, List<NodeInfo>> entry : MESSAGE_TO_MEMBERS.entrySet()) {
            List<NodeInfo> list = entry.getValue();
            if (list == null) continue;

            for (NodeInfo n : list) {
                counts.merge(n, 1, Integer::sum);
            }
        }

        return counts;
    }

    // ===== Mapping utils =====

    private static void trackStoredAt(int id, NodeInfo member) {
        MESSAGE_TO_MEMBERS.compute(id, (k, v) -> {
            if (v == null) v = new java.util.concurrent.CopyOnWriteArrayList<>();
            boolean exists = v.stream().anyMatch(m -> sameMember(m, member));
            if (!exists) v.add(member);
            return v;
        });
    }

    private static void removeMemberFromAllMappings(NodeInfo dead) {
        for (var entry : MESSAGE_TO_MEMBERS.entrySet()) {
            List<NodeInfo> list = entry.getValue();
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
        var list = MESSAGE_TO_MEMBERS.get(id);
        if (list == null || list.isEmpty()) {
            System.out.println("[MAPPING] id=" + id + " -> (empty)");
            return;
        }
        String members = list.stream()
                .map(m -> m.getHost() + ":" + m.getPort())
                .reduce((x, y) -> x + ", " + y)
                .orElse("");
        System.out.println("[MAPPING] id=" + id + " -> " + members);
    }

    // tolerance.conf dosyasını hem proje kökünde hem de iç içe klasör senaryosunda bulmaya çalışır.
    private static Path resolveToleranceConfPath() {
        Path p1 = Path.of("tolerance.conf");
        if (Files.exists(p1)) return p1;

        // İç içe repo senaryosu: distributed-disk-register/tolerance.conf
        Path p2 = Path.of("distributed-disk-register", "tolerance.conf");
        if (Files.exists(p2)) return p2;

        return p1;
    }

    // Bu task için tolerance 1..7 aralığında olmalı. Dışarıdan farklı gelirse sınırla.
    private static int normalizeTolerance(int raw) {
        if (raw < 1) return 1;
        if (raw > 7) return 7;
        return raw;
    }
}
