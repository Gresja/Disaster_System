package web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import utils.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP server that:
 *  GET /        → serves the HTML dashboard (web/index.html)
 *  GET /events  → opens a persistent SSE stream for live event push
 *
 * Uses Java's built-in com.sun.net.httpserver — no external dependencies.
 */
public class WebServer {

    private final HttpServer       server;
    private final EventBroadcaster broadcaster = EventBroadcaster.getInstance();
    private final int              port;

    public WebServer(int port) throws IOException {
        this.port   = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/", new StaticHandler());
        this.server.createContext("/events", new SseHandler());
        // Daemon thread pool so the server never blocks JVM shutdown on its own
        this.server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "WebServer-worker");
            t.setDaemon(true);
            return t;
        }));
    }

    public void start() {
        server.start();
        Logger.success("WebServer", "Dashboard → http://localhost:" + port);
    }

    public void stop() {
        server.stop(1);
    }

    // ── Static file handler ───────────────────────────────────────────────────
    private class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (!path.equals("/") && !path.equals("/index.html")) {
                byte[] body = "Not Found".getBytes();
                ex.sendResponseHeaders(404, body.length);
                ex.getResponseBody().write(body);
                ex.close();
                return;
            }
            File htmlFile = new File("web/index.html");
            byte[] content = htmlFile.exists()
                    ? Files.readAllBytes(htmlFile.toPath())
                    : "<h1>web/index.html not found</h1>".getBytes();

            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, content.length);
            ex.getResponseBody().write(content);
            ex.close();
        }
    }

    // ── SSE handler ───────────────────────────────────────────────────────────
    private class SseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            ex.getResponseHeaders().add("Content-Type",  "text/event-stream; charset=utf-8");
            ex.getResponseHeaders().add("Cache-Control", "no-cache");
            ex.getResponseHeaders().add("Connection",    "keep-alive");
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.sendResponseHeaders(200, 0); // 0 = streaming (no content-length)

            OutputStream out = ex.getResponseBody();
            broadcaster.addClient(out);

            try {
                // Establish the connection with a SSE comment line
                out.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Keep the connection alive with periodic pings so the browser
                // doesn't time out the SSE stream on slow sections.
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(15_000);
                    out.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (Exception ignored) {
                // Client disconnected or thread interrupted
            } finally {
                broadcaster.removeClient(out);
                try { ex.close(); } catch (Exception ignored) {}
            }
        }
    }
}
