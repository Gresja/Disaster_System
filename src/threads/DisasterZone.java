package threads;

import managers.EmergencyQueue;
import models.DisasterType;
import models.Emergency;
import utils.Logger;
import web.EventBroadcaster;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * Represents one disaster area (e.g. "Alpha" hit by an earthquake).
 *
 * Each zone is its own Thread — it runs independently from the other zones.
 * All 5 zones produce emergencies at the same time, just like in a real disaster
 * where multiple areas need help simultaneously.
 *
 * HOW IT WORKS:
 * 1. Zone starts but waits for the "go" signal (CountDownLatch).
 * 2. When all zones are ready, they ALL start at the exact same moment.
 * 3. Each zone creates emergencies one by one with random delays.
 * 4. Emergencies are added to the shared queue for handlers to pick up.
 *
 * Java concepts used:
 *   Thread — each zone runs independently and in parallel
 *   CountDownLatch — makes all zones start at the same time
 */
public class DisasterZone extends Thread {

    private final String zoneName;
    private final DisasterType primaryDisaster;
    private final EmergencyQueue emergencyQueue;
    private final CountDownLatch startSignal;   // wait for all zones to be ready
    private final int emergenciesCount;          // how many emergencies this zone generates
    private final Random random;

    public DisasterZone(String zoneName,
                        DisasterType primaryDisaster,
                        EmergencyQueue emergencyQueue,
                        CountDownLatch startSignal,
                        int emergenciesCount) {
        super("Zone-" + zoneName);
        this.zoneName         = zoneName;
        this.primaryDisaster  = primaryDisaster;
        this.emergencyQueue   = emergencyQueue;
        this.startSignal      = startSignal;
        this.emergenciesCount = emergenciesCount;
        this.random           = new Random();
    }

    @Override
    public void run() {
        try {
            Logger.info(getName(), "Zone initialized. Waiting for system-wide start signal...");
            startSignal.await();   // block until all zones are ready → simultaneous start
            Logger.warning(getName(), "DISASTER ONSET — Zone " + zoneName + " reporting emergencies!");
            EventBroadcaster.getInstance().broadcastZoneStarted(zoneName, primaryDisaster.name());

            for (int i = 0; i < emergenciesCount; i++) {
                // Occasionally mix in a secondary disaster type for realism
                DisasterType type = (random.nextInt(5) == 0)
                        ? DisasterType.values()[random.nextInt(DisasterType.values().length)]
                        : primaryDisaster;

                int severity = primaryDisaster.getBaseSeverity()
                        + random.nextInt(3) - 1;  // ±1 around base severity
                severity = Math.min(10, Math.max(1, severity));

                Emergency emergency = new Emergency(zoneName, type, severity);
                emergencyQueue.submit(emergency);

                // Zones report emergencies at staggered intervals (500–1500 ms)
                Thread.sleep(500 + random.nextInt(1000));
            }

            Logger.info(getName(), "Zone " + zoneName + " has reported all emergencies.");
            EventBroadcaster.getInstance().broadcastZoneFinished(zoneName);
        } catch (InterruptedException e) {
            Logger.error(getName(), "Zone " + zoneName + " interrupted.");
            Thread.currentThread().interrupt();
        }
    }
}
