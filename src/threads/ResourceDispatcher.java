package threads;

import managers.EmergencyQueue;
import managers.ResourceManager;
import managers.StatisticsManager;
import utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Creates and manages the team of 4 emergency handler threads.
 *
 * Instead of creating threads manually, we use a "thread pool" (ExecutorService).
 * A thread pool is like a team of workers — you give them tasks, and Java
 * decides which worker picks up each task. This is more efficient than
 * creating/destroying threads every time.
 *
 * Java concepts used:
 *   ExecutorService — a thread pool that manages handler threads
 *   AtomicBoolean — a shared "stop" flag that all handlers check
 */
public class ResourceDispatcher {

    private final ExecutorService threadPool;
    private final int             handlerCount;
    private final AtomicBoolean   running = new AtomicBoolean(true);
    private final List<EmergencyHandler> handlers = new ArrayList<>();

    public ResourceDispatcher(int handlerCount,
                              EmergencyQueue emergencyQueue,
                              ResourceManager resourceManager,
                              StatisticsManager statisticsManager) {
        this.handlerCount = handlerCount;
        this.threadPool   = Executors.newFixedThreadPool(handlerCount,
                r -> {
                    Thread t = new Thread(r, "Handler-" + (handlers.size() + 1));
                    t.setDaemon(false);
                    return t;
                });

        for (int i = 1; i <= handlerCount; i++) {
            EmergencyHandler handler = new EmergencyHandler(
                    "Handler-" + i,
                    emergencyQueue,
                    resourceManager,
                    statisticsManager,
                    running);
            handlers.add(handler);
        }
    }

    /** Submits all handler tasks to the thread pool — they run concurrently. */
    public void start() {
        Logger.info("ResourceDispatcher",
                "Starting thread pool with " + handlerCount + " concurrent handlers.");
        for (EmergencyHandler handler : handlers) {
            threadPool.submit(handler);
        }
    }

    /**
     * Signals handlers to stop accepting new work and waits for current tasks to finish.
     */
    public void shutdown(int waitSeconds) {
        Logger.warning("ResourceDispatcher", "Initiating graceful shutdown...");
        running.set(false);
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(waitSeconds, TimeUnit.SECONDS)) {
                Logger.error("ResourceDispatcher", "Forcing shutdown after timeout.");
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        Logger.success("ResourceDispatcher", "All handler threads have completed.");
    }

    public boolean isRunning() { return !threadPool.isTerminated(); }
}
