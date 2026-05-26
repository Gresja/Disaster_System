package threads;

import managers.EmergencyQueue;
import managers.ResourceManager;
import managers.StatisticsManager;
import models.Emergency;
import models.ResourceType;
import utils.Logger;
import web.EventBroadcaster;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A responder that handles emergencies. There are 4 of these running in parallel.
 *
 * Each handler runs in a loop:
 *   1. Take the most severe emergency from the queue (waits if queue is empty).
 *   2. Request the resources it needs (ambulance, shelter, etc.).
 *      → If a resource is all used up, the handler WAITS here until one is freed.
 *   3. Simulate the rescue operation (takes longer for higher severity).
 *   4. Release the resources so other handlers can use them.
 *   5. Mark emergency as resolved, then go back to step 1.
 *
 * WHY PARALLEL?
 * - 4 handlers work at the same time = 4 emergencies handled simultaneously.
 * - Without parallelism, they'd be handled one by one (much slower).
 * - They safely share the queue and resources using thread-safe tools.
 *
 * Java concepts used:
 *   Runnable — submitted to a thread pool (ExecutorService)
 *   AtomicBoolean — shared stop signal that all handlers can read safely
 */
public class EmergencyHandler implements Runnable {

    private final String handlerName;
    private final EmergencyQueue emergencyQueue;
    private final ResourceManager resourceManager;
    private final StatisticsManager statisticsManager;
    private final AtomicBoolean running;

    public EmergencyHandler(String handlerName,
                            EmergencyQueue emergencyQueue,
                            ResourceManager resourceManager,
                            StatisticsManager statisticsManager,
                            AtomicBoolean running) {
        this.handlerName        = handlerName;
        this.emergencyQueue     = emergencyQueue;
        this.resourceManager    = resourceManager;
        this.statisticsManager  = statisticsManager;
        this.running            = running;
    }

    @Override
    public void run() {
        Logger.info(handlerName, "Handler started on thread: " + Thread.currentThread().getName());

        while (running.get() || emergencyQueue.getQueueSize() > 0) {
            try {
                // Blocks here if the queue is empty (no busy-waiting)
                Emergency emergency = emergencyQueue.take();

                handleEmergency(emergency);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        Logger.info(handlerName, "Handler shutting down.");
    }

    private void handleEmergency(Emergency emergency) throws InterruptedException {
        emergency.setStatus(Emergency.Status.IN_PROGRESS);
        Logger.warning(handlerName, "Handling " + emergency);
        EventBroadcaster.getInstance().broadcastEmergencyHandling(emergency, handlerName);

        List<ResourceType> required = emergency.getRequiredResources();

        // Acquire all needed resources (may block per resource if unavailable)
        for (ResourceType resource : required) {
            resourceManager.acquire(resource, emergency.getId());
        }

        Logger.success(handlerName, "All resources secured for Emergency #" + emergency.getId()
                + " in Zone " + emergency.getZone()
                + " | Deploying " + required.size() + " resource type(s)...");

        // Simulate response time based on severity (higher severity = longer operation)
        long responseMs = (long) emergency.getSeverity() * 400 + 500;
        Thread.sleep(responseMs);

        // Release all acquired resources
        for (ResourceType resource : required) {
            resourceManager.release(resource, emergency.getId());
        }

        emergency.setStatus(Emergency.Status.RESOLVED);
        emergencyQueue.incrementResolved();
        statisticsManager.recordResolution(emergency.getDisasterType(), emergency.getResponseTimeMs());

        Logger.success(handlerName,
                "RESOLVED " + emergency + " | Response time: " + emergency.getResponseTimeMs() + "ms");
        EventBroadcaster.getInstance().broadcastEmergencyResolved(emergency, handlerName);
    }
}
