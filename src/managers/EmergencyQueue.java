package managers;

import models.Emergency;
import utils.Logger;
import web.EventBroadcaster;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Think of this as a "waiting list" for emergencies, sorted by severity.
 *
 * When a disaster zone reports an emergency, it goes into this queue.
 * When a handler thread is free, it takes the most severe one from the queue.
 *
 * WHY PARALLEL?
 * - Multiple zones can add emergencies at the same time (thread-safe).
 * - Multiple handlers can take emergencies at the same time (no conflicts).
 * - If the queue is empty, handlers automatically wait instead of crashing.
 *
 * Java concepts used:
 *   PriorityBlockingQueue — sorts by severity + thread-safe
 *   AtomicInteger — a counter that many threads can update without errors
 */
public class EmergencyQueue {

    private final PriorityBlockingQueue<Emergency> queue = new PriorityBlockingQueue<>();
    private final AtomicInteger totalReceived = new AtomicInteger(0);
    private final AtomicInteger totalResolved = new AtomicInteger(0);

    /** Called by zone threads (producers) to report a new emergency. */
    public void submit(Emergency emergency) {
        queue.put(emergency);
        int count = totalReceived.incrementAndGet();
        Logger.emergency("EmergencyQueue",
                "NEW EMERGENCY SUBMITTED  " + emergency +
                " | Queue size: " + queue.size() + " | Total received: " + count);
        EventBroadcaster.getInstance().broadcastEmergencySubmitted(emergency);
    }

    /**
     * Called by handler threads (consumers). Blocks until an emergency is available.
     */
    public Emergency take() throws InterruptedException {
        return queue.take();
    }

    public int getQueueSize()    { return queue.size(); }
    public int getTotalReceived() { return totalReceived.get(); }
    public int getTotalResolved() { return totalResolved.get(); }

    public void incrementResolved() { totalResolved.incrementAndGet(); }
}
