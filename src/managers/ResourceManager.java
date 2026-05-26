package managers;

import models.ResourceType;
import utils.Logger;
import web.EventBroadcaster;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages limited resources like ambulances, shelters, road crews, etc.
 *
 * Example: there are only 8 ambulances. If 4 handlers each need 2 ambulances,
 * that's 8 total — the next handler that needs one must WAIT until someone
 * finishes and returns theirs. This is exactly what a Semaphore does.
 *
 * WHY PARALLEL?
 * - Multiple handlers request resources at the same time.
 * - Semaphore lets them share safely: it counts how many are left.
 * - When count reaches 0, the next thread automatically waits.
 * - When a resource is released, a waiting thread wakes up.
 *
 * Java concepts used:
 *   Semaphore — controls how many threads can use a resource at once
 *   AtomicInteger — thread-safe counter for statistics
 */
public class ResourceManager {

    private final Map<ResourceType, Semaphore>    semaphores    = new EnumMap<>(ResourceType.class);
    private final Map<ResourceType, AtomicInteger> totalUsed    = new EnumMap<>(ResourceType.class);
    private final Map<ResourceType, Integer>       totalCapacity = new EnumMap<>(ResourceType.class);

    public ResourceManager() {
        for (ResourceType type : ResourceType.values()) {
            int units = type.getTotalUnits();
            semaphores.put(type, new Semaphore(units, true)); // fair semaphore
            totalUsed.put(type, new AtomicInteger(0));
            totalCapacity.put(type, units);
        }
    }

    /**
     * Acquires one unit of the requested resource.
     * Blocks the calling thread if no units are currently available.
     */
    public boolean acquire(ResourceType type, int emergencyId) throws InterruptedException {
        Logger.info("ResourceManager", "Thread [" + Thread.currentThread().getName() +
                "] requesting " + type.getDisplayName() + " for Emergency #" + emergencyId +
                " | Available: " + semaphores.get(type).availablePermits());
        semaphores.get(type).acquire();
        totalUsed.get(type).incrementAndGet();
        int available = semaphores.get(type).availablePermits();
        Logger.success("ResourceManager", "Acquired " + type.getDisplayName() +
                " for Emergency #" + emergencyId + " | Remaining: " + available);
        EventBroadcaster.getInstance().broadcastResourceAcquired(
                type, emergencyId, available, totalCapacity.get(type));
        return true;
    }

    /**
     * Releases one unit back to the pool, allowing waiting threads to proceed.
     */
    public void release(ResourceType type, int emergencyId) {
        semaphores.get(type).release();
        int available = semaphores.get(type).availablePermits();
        Logger.info("ResourceManager", "Released " + type.getDisplayName() +
                " from Emergency #" + emergencyId + " | Available: " + available);
        EventBroadcaster.getInstance().broadcastResourceReleased(
                type, emergencyId, available, totalCapacity.get(type));
    }

    public int getAvailable(ResourceType type) {
        return semaphores.get(type).availablePermits();
    }

    public int getCapacity(ResourceType type) {
        return totalCapacity.get(type);
    }

    public int getInUse(ResourceType type) {
        return totalCapacity.get(type) - semaphores.get(type).availablePermits();
    }

    public int getTotalDeployed(ResourceType type) {
        return totalUsed.get(type).get();
    }

    /** Prints a snapshot of all resource states (called from monitor thread). */
    public synchronized void printStatus() {
        Logger.monitor(String.format("  %-22s %8s %8s %8s %10s",
                "Resource", "Capacity", "In-Use", "Free", "Total Sent"));
        Logger.monitor("  " + "─".repeat(60));
        for (ResourceType type : ResourceType.values()) {
            Logger.monitor(String.format("  %-22s %8d %8d %8d %10d",
                    type.getDisplayName(),
                    totalCapacity.get(type),
                    getInUse(type),
                    getAvailable(type),
                    totalUsed.get(type).get()));
        }
    }
}
