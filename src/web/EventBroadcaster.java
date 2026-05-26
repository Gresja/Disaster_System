package web;

import managers.EmergencyQueue;
import managers.ResourceManager;
import models.Emergency;
import models.ResourceType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleton that pushes JSON events to all connected browser clients via SSE.
 *
 * Parallel Programming Note:
 *   CopyOnWriteArrayList allows many threads to read/iterate concurrently
 *   without locking. The broadcast() method is synchronized to prevent
 *   interleaved bytes when multiple threads emit events simultaneously.
 */
public class EventBroadcaster {

    private static final EventBroadcaster INSTANCE = new EventBroadcaster();
    private final CopyOnWriteArrayList<OutputStream> clients = new CopyOnWriteArrayList<>();

    private EventBroadcaster() {}

    public static EventBroadcaster getInstance() { return INSTANCE; }

    public void addClient(OutputStream out) {
        clients.add(out);
    }

    public void removeClient(OutputStream out) {
        clients.remove(out);
    }

    /** Sends a JSON event to every connected browser client. Thread-safe. */
    public synchronized void broadcast(String jsonData) {
        if (clients.isEmpty()) return;
        byte[] bytes = ("data: " + jsonData + "\n\n").getBytes(StandardCharsets.UTF_8);
        List<OutputStream> dead = new ArrayList<>();
        for (OutputStream out : clients) {
            try {
                out.write(bytes);
                out.flush();
            } catch (IOException e) {
                dead.add(out);
            }
        }
        clients.removeAll(dead);
    }

    // ── Typed broadcast helpers ───────────────────────────────────────────────

    public void broadcastEmergencySubmitted(Emergency e) {
        broadcast(String.format(
                "{\"type\":\"emergency_submitted\",\"id\":%d,\"zone\":\"%s\",\"disaster\":\"%s\",\"severity\":%d}",
                e.getId(), e.getZone(), e.getDisasterType().name(), e.getSeverity()));
    }

    public void broadcastEmergencyHandling(Emergency e, String handlerName) {
        broadcast(String.format(
                "{\"type\":\"emergency_handling\",\"id\":%d,\"zone\":\"%s\",\"disaster\":\"%s\",\"severity\":%d,\"handler\":\"%s\"}",
                e.getId(), e.getZone(), e.getDisasterType().name(), e.getSeverity(), handlerName));
    }

    public void broadcastEmergencyResolved(Emergency e, String handlerName) {
        broadcast(String.format(
                "{\"type\":\"emergency_resolved\",\"id\":%d,\"zone\":\"%s\",\"disaster\":\"%s\",\"severity\":%d,\"handler\":\"%s\",\"responseTimeMs\":%d}",
                e.getId(), e.getZone(), e.getDisasterType().name(), e.getSeverity(),
                handlerName, e.getResponseTimeMs()));
    }

    public void broadcastResourceAcquired(ResourceType rt, int emergencyId, int available, int capacity) {
        broadcast(String.format(
                "{\"type\":\"resource_acquired\",\"resource\":\"%s\",\"resourceName\":\"%s\",\"emergencyId\":%d,\"available\":%d,\"capacity\":%d,\"inUse\":%d}",
                rt.name(), rt.getDisplayName(), emergencyId, available, capacity, capacity - available));
    }

    public void broadcastResourceReleased(ResourceType rt, int emergencyId, int available, int capacity) {
        broadcast(String.format(
                "{\"type\":\"resource_released\",\"resource\":\"%s\",\"resourceName\":\"%s\",\"emergencyId\":%d,\"available\":%d,\"capacity\":%d,\"inUse\":%d}",
                rt.name(), rt.getDisplayName(), emergencyId, available, capacity, capacity - available));
    }

    public void broadcastZoneStarted(String zone, String disaster) {
        broadcast(String.format(
                "{\"type\":\"zone_started\",\"zone\":\"%s\",\"disaster\":\"%s\"}", zone, disaster));
    }

    public void broadcastZoneFinished(String zone) {
        broadcast(String.format(
                "{\"type\":\"zone_finished\",\"zone\":\"%s\"}", zone));
    }

    public void broadcastDashboardUpdate(EmergencyQueue queue, ResourceManager rm) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"dashboard_update\"");
        sb.append(",\"queued\":").append(queue.getQueueSize());
        sb.append(",\"received\":").append(queue.getTotalReceived());
        sb.append(",\"resolved\":").append(queue.getTotalResolved());
        sb.append(",\"resources\":{");
        boolean first = true;
        for (ResourceType rt : ResourceType.values()) {
            if (!first) sb.append(",");
            sb.append("\"").append(rt.name()).append("\":{");
            sb.append("\"name\":\"").append(rt.getDisplayName()).append("\"");
            sb.append(",\"capacity\":").append(rm.getCapacity(rt));
            sb.append(",\"inUse\":").append(rm.getInUse(rt));
            sb.append(",\"available\":").append(rm.getAvailable(rt));
            sb.append("}");
            first = false;
        }
        sb.append("}}");
        broadcast(sb.toString());
    }

    public void broadcastSystemComplete(int received, int resolved) {
        broadcast(String.format(
                "{\"type\":\"system_complete\",\"totalReceived\":%d,\"totalResolved\":%d}",
                received, resolved));
    }
}
