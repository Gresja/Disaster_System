import managers.EmergencyQueue;
import managers.ResourceManager;
import managers.StatisticsManager;
import models.DisasterType;
import threads.DisasterZone;
import threads.MonitorThread;
import threads.ResourceDispatcher;
import utils.Logger;
import web.EventBroadcaster;
import web.WebServer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ============================================================
 *   DISASTER EMERGENCY RESPONSE COORDINATION SYSTEM
 *   Parallel Programming Project — Java
 * ============================================================
 *
 * ARCHITECTURE OVERVIEW:
 * ┌─────────────────────────────────────────────────────────┐
 * │  PRODUCER THREADS (DisasterZone)                        │
 * │   Zone-Alpha  Zone-Beta  Zone-Gamma  Zone-Delta ...     │
 * │        │           │           │          │             │
 * │        └───────────┴───────────┴──────────┘             │
 * │                         │                               │
 * │            PriorityBlockingQueue<Emergency>             │
 * │           (highest severity served first)               │
 * │                         │                               │
 * │  CONSUMER THREADS (EmergencyHandler via ExecutorService)│
 * │   Handler-1  Handler-2  Handler-3  Handler-4            │
 * │        │           │           │          │             │
 * │        └───────────┴───────────┴──────────┘             │
 * │                         │                               │
 * │         ResourceManager (Semaphore per resource)        │
 * │    Ambulance  Shelter  FireTeam  FoodWater  RoadCrew    │
 * │                                                         │
 * │  MONITOR THREAD (live dashboard, daemon thread)         │
 * └─────────────────────────────────────────────────────────┘
 *
 * KEY PARALLEL PROGRAMMING CONCEPTS USED:
 *  1. Thread          — DisasterZone (producers), each an independent thread
 *  2. Runnable        — EmergencyHandler (consumers) run inside the thread pool
 *  3. CountDownLatch  — synchronizes all zones to start simultaneously
 *  4. PriorityBlockingQueue — thread-safe, priority-ordered request queue
 *  5. ExecutorService / ThreadPoolExecutor — manages handler thread lifecycle
 *  6. Semaphore       — limits concurrent access to finite resources
 *  7. AtomicBoolean / AtomicInteger / AtomicLong — lock-free shared counters
 *  8. Daemon thread   — MonitorThread runs without blocking JVM shutdown
 */
public class DisasterCoordinationSystem {

    // ── Configuration ────────────────────────────────────────────────────────
    private static final int HANDLER_THREAD_COUNT = 4;   // concurrent response handlers
    private static final int MONITOR_INTERVAL_MS  = 4000; // dashboard refresh rate
    private static final int SHUTDOWN_WAIT_S      = 120;  // max wait for handlers to finish
    private static final int WEB_PORT             = 8080; // browser dashboard port

    // Disaster zone definitions: (name, primary disaster type, number of emergencies)
    private static final Object[][] ZONES = {
            { "Alpha",   DisasterType.EARTHQUAKE,  3 },
            { "Beta",    DisasterType.FLOOD,        3 },
            { "Gamma",   DisasterType.FOREST_FIRE,  2 },
            { "Delta",   DisasterType.LANDSLIDE,    2 },
            { "Epsilon", DisasterType.STORM,        2 },
    };

    // ── Entry Point ──────────────────────────────────────────────────────────
    public static void main(String[] args) throws InterruptedException, java.io.IOException {

        Logger.header("DISASTER EMERGENCY RESPONSE COORDINATION SYSTEM — STARTING");

        // ── 0. Start web dashboard server ─────────────────────────────────────
        WebServer webServer = new WebServer(WEB_PORT);
        webServer.start();
        Runtime.getRuntime().exec("open http://localhost:" + WEB_PORT); // auto-open browser (macOS)
        Logger.info("Main", "Opening dashboard in browser... (or go to http://localhost:" + WEB_PORT + ")");
        Thread.sleep(2500); // brief pause so the browser can connect before events start

        // Shared infrastructure
        EmergencyQueue    emergencyQueue    = new EmergencyQueue();
        ResourceManager   resourceManager   = new ResourceManager();
        StatisticsManager statisticsManager = new StatisticsManager();
        AtomicBoolean     systemRunning     = new AtomicBoolean(true);

        // ── 1. Start live monitor (daemon thread) ────────────────────────────
        MonitorThread monitor = new MonitorThread(
                emergencyQueue, resourceManager, statisticsManager,
                systemRunning, MONITOR_INTERVAL_MS);
        monitor.start();

        // ── 2. Start handler thread pool ─────────────────────────────────────
        ResourceDispatcher dispatcher = new ResourceDispatcher(
                HANDLER_THREAD_COUNT, emergencyQueue, resourceManager, statisticsManager);
        dispatcher.start();

        // ── 3. Create zone threads — all wait on a shared CountDownLatch ─────
        //    CountDownLatch(1): countdown reaches 0 when we call latch.countDown(),
        //    releasing all zones simultaneously to simulate concurrent disaster onset.
        CountDownLatch startLatch = new CountDownLatch(1);
        List<DisasterZone> zones  = new ArrayList<>();

        for (Object[] zoneConfig : ZONES) {
            String       name       = (String)       zoneConfig[0];
            DisasterType disaster   = (DisasterType) zoneConfig[1];
            int          count      = (int)          zoneConfig[2];

            DisasterZone zone = new DisasterZone(name, disaster, emergencyQueue, startLatch, count);
            zones.add(zone);
            zone.start();
        }

        Logger.info("Main", "All " + zones.size() + " disaster zones initialized. "
                + "Releasing start signal in 2 seconds...");
        Thread.sleep(2000);

        // ── 4. Release all zone threads simultaneously (CountDownLatch) ──────
        Logger.header("ALL ZONES ACTIVATED — DISASTER RESPONSE BEGINS");
        startLatch.countDown();  // all Zone threads unblock at the same instant

        // ── 5. Wait for all zones to finish generating emergencies ────────────
        for (DisasterZone zone : zones) {
            zone.join();
        }
        Logger.info("Main", "All zones have finished reporting. Waiting for handlers to clear the queue...");

        // ── 6. Wait until the emergency queue is drained ─────────────────────
        while (emergencyQueue.getQueueSize() > 0 || dispatcher.isRunning()) {
            if (emergencyQueue.getQueueSize() == 0 && emergencyQueue.getTotalResolved()
                    >= emergencyQueue.getTotalReceived()) {
                break;
            }
            Thread.sleep(500);
        }
        Thread.sleep(1000); // small buffer for in-flight handlers to finish

        // ── 7. Graceful shutdown ──────────────────────────────────────────────
        systemRunning.set(false);
        dispatcher.shutdown(SHUTDOWN_WAIT_S);

        // Broadcast completion to dashboard
        EventBroadcaster.getInstance().broadcastSystemComplete(
                emergencyQueue.getTotalReceived(), emergencyQueue.getTotalResolved());

        // Wait for final report from monitor
        monitor.interrupt();
        monitor.join(3000);

        Logger.header("SYSTEM SHUTDOWN COMPLETE — Dashboard still live at http://localhost:" + WEB_PORT);
        Logger.info("Main", "Press Ctrl+C to exit.");

        // Keep the web server alive so the user can read the final dashboard
        Thread.currentThread().join();
    }
}
