package com.example.family;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal TCP server that listens on the given port (default 6666), accepts multiple
 * clients and supports simple text protocol:
 *   SET <id> <message...>  -> responds with OK
 *   GET <id>               -> responds with VALUE <message> or NOT_FOUND
 *   EXIT                   -> closes connection
 * 
 * This implementation keeps an in-memory, thread-safe map as the store.
 */
public class CommandServer {
	private final int port;
	private final ExecutorService pool = Executors.newCachedThreadPool();
	private final ConcurrentHashMap<Integer, String> store = new ConcurrentHashMap<>();

	public CommandServer(int port) { this.port = port; }

	public void start() throws IOException {
		try (ServerSocket ss = new ServerSocket(port)) {
			System.out.println("CommandServer listening on port " + port);
			while (!Thread.currentThread().isInterrupted()) {
				Socket client = ss.accept();
				pool.submit(() -> handleClient(client));
			}
		} finally {
			pool.shutdownNow();
		}
	}

	private void handleClient(Socket socket) {
		String remote = socket.getRemoteSocketAddress() == null ? "unknown" : socket.getRemoteSocketAddress().toString();
		System.out.println("Accepted connection from " + remote);
		try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

			out.write("OK Welcome to CommandServer\n");
			out.flush();

			String line;
			while ((line = in.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.isEmpty()) continue;

				// client requested close
				if (trimmed.equalsIgnoreCase("EXIT") || trimmed.equalsIgnoreCase("QUIT")) {
					out.write("BYE\n");
					out.flush();
					break;
				}

				Command cmd = CommandParser.parse(trimmed);
				if (cmd == null) {
					out.write("ERR_BAD_COMMAND\n");
					out.flush();
					continue;
				}

				if (cmd.getType() == Command.Type.SET) {
					SetCommand s = (SetCommand) cmd;
					store.put(s.getId(), s.getMessage());
					out.write("OK\n");
					out.flush();
				} else { // GET
					GetCommand g = (GetCommand) cmd;
					String v = store.get(g.getId());
					if (v == null) out.write("NOT_FOUND\n");
					else out.write("VALUE " + v + "\n");
					out.flush();
				}
			}
		} catch (IOException e) {
			System.err.println("Connection error (" + remote + "): " + e.getMessage());
		} finally {
			try { socket.close(); } catch (IOException ignored) {}
			System.out.println("Closed connection from " + remote);
		}
	}

	public static void main(String[] args) throws Exception {
		int port = 6666;
		CommandServer server = new CommandServer(port);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("Shutting down CommandServer");
		}));
		server.start();
	}
}
