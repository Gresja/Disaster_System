package threads;

import managers.EmergencyQueue;
import managers.ResourceManager;
import managers.StatisticsManager;
import utils.Logger;
import web.EventBroadcaster;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs in the background and prints system status every few seconds.
 * Also sends updates to the web dashboard so the browser stays in sync.
 *
 * This is a "daemon thread" — it runs alongside everything else but
 * won't prevent the program from stopping when the simulation is done.
 *
 * Java concepts used:
 *   Daemon thread — background thread that doesn't block program exit
 *   AtomicBoolean — shared stop signal, checked every loop iteration
 */
public class MonitorThread extends Thread {

    private final EmergencyQueue     emergencyQueue;
    private final ResourceManager    resourceManager;
    private final StatisticsManager  statisticsManager;
    private final AtomicBoolean      running;
    private final int                intervalMs;

    public MonitorThread(EmergencyQueue emergencyQueue,
                         ResourceManager resourceManager,
                         StatisticsManager statisticsManager,
                         AtomicBoolean running,
                         int intervalMs) {
        super("Monitor-Thread");
        setDaemon(true);
        this.emergencyQueue    = emergencyQueue;
        this.resourceManager   = resourceManager;
        this.statisticsManager = statisticsManager;
        this.running           = running;
        this.intervalMs        = intervalMs;
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                Thread.sleep(intervalMs);
                printDashboard();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // Final report after all handlers finish
        printFinalReport();
    }

    private void printDashboard() {
        Logger.divider();
        Logger.monitor(String.format("  LIVE DASHBOARD  |  Queue: %-4d  |  Received: %-4d  |  Resolved: %-4d",
                emergencyQueue.getQueueSize(),
                emergencyQueue.getTotalReceived(),
                emergencyQueue.getTotalResolved()));
        Logger.divider();
        resourceManager.printStatus();
        Logger.divider();
        EventBroadcaster.getInstance().broadcastDashboardUpdate(emergencyQueue, resourceManager);
    }

    private void printFinalReport() {
        Logger.header("FINAL REPORT — DISASTER EMERGENCY RESPONSE COORDINATION SYSTEM");
        Logger.monitor("  Emergency Statistics:");
        Logger.monitor("  " + "─".repeat(60));
        Logger.monitor(String.format("  %-30s %d", "Total Emergencies Received:",
                emergencyQueue.getTotalReceived()));
        Logger.monitor(String.format("  %-30s %d", "Total Emergencies Resolved:",
                emergencyQueue.getTotalResolved()));
        Logger.monitor("");
        Logger.monitor("  Resolution by Disaster Type:");
        Logger.monitor("  " + "─".repeat(60));
        statisticsManager.printReport();
        Logger.monitor("");
        Logger.monitor("  Final Resource Utilization:");
        Logger.monitor("  " + "─".repeat(60));
        resourceManager.printStatus();
        Logger.divider();
    }
}
