package managers;

import models.DisasterType;
import utils.Logger;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects and reports system-wide response statistics.
 * All counters use AtomicInteger/AtomicLong for lock-free thread safety.
 *
 * Parallel Programming Concepts Demonstrated:
 *  - AtomicInteger / AtomicLong: non-blocking counters updated by many threads.
 */
public class StatisticsManager {

    private final Map<DisasterType, AtomicInteger> resolvedByType = new EnumMap<>(DisasterType.class);
    private final AtomicLong totalResponseTimeMs = new AtomicLong(0);
    private final AtomicInteger resolvedCount     = new AtomicInteger(0);

    public StatisticsManager() {
        for (DisasterType type : DisasterType.values()) {
            resolvedByType.put(type, new AtomicInteger(0));
        }
    }

    public void recordResolution(DisasterType type, long responseTimeMs) {
        resolvedByType.get(type).incrementAndGet();
        resolvedCount.incrementAndGet();
        totalResponseTimeMs.addAndGet(responseTimeMs);
    }

    public void printReport() {
        int total = resolvedCount.get();
        long avgMs = total > 0 ? totalResponseTimeMs.get() / total : 0;

        Logger.monitor(String.format("  %-20s %12s", "Disaster Type", "Resolved"));
        Logger.monitor("  " + "─".repeat(35));
        for (DisasterType type : DisasterType.values()) {
            Logger.monitor(String.format("  %-20s %12d", type.getDisplayName(),
                    resolvedByType.get(type).get()));
        }
        Logger.monitor("  " + "─".repeat(35));
        Logger.monitor(String.format("  %-20s %12d", "TOTAL RESOLVED", total));
        Logger.monitor(String.format("  %-20s %11dms", "AVG RESPONSE TIME", avgMs));
    }
}
