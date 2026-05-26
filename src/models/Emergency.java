package models;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Emergency implements Comparable<Emergency> {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);

    public enum Status { PENDING, IN_PROGRESS, RESOLVED }

    private final int id;
    private final String zone;
    private final DisasterType disasterType;
    private final int severity;          // 1-10
    private final List<ResourceType> requiredResources;
    private volatile Status status;
    private final long createdAt;
    private volatile long resolvedAt;

    public Emergency(String zone, DisasterType disasterType, int severity) {
        this.id               = ID_COUNTER.getAndIncrement();
        this.zone             = zone;
        this.disasterType     = disasterType;
        this.severity         = Math.min(10, Math.max(1, severity));
        this.requiredResources = resolveRequiredResources(disasterType);
        this.status           = Status.PENDING;
        this.createdAt        = System.currentTimeMillis();
    }

    private List<ResourceType> resolveRequiredResources(DisasterType type) {
        List<ResourceType> resources = new ArrayList<>();
        switch (type) {
            case EARTHQUAKE:
                resources.add(ResourceType.AMBULANCE);
                resources.add(ResourceType.SHELTER);
                resources.add(ResourceType.ROAD_CREW);
                break;
            case FLOOD:
                resources.add(ResourceType.FOOD_WATER_SUPPLY);
                resources.add(ResourceType.SHELTER);
                resources.add(ResourceType.ROAD_CREW);
                break;
            case FOREST_FIRE:
                resources.add(ResourceType.FIRE_RESCUE_TEAM);
                resources.add(ResourceType.AMBULANCE);
                break;
            case LANDSLIDE:
                resources.add(ResourceType.ROAD_CREW);
                resources.add(ResourceType.AMBULANCE);
                resources.add(ResourceType.FOOD_WATER_SUPPLY);
                break;
            case STORM:
                resources.add(ResourceType.SHELTER);
                resources.add(ResourceType.FOOD_WATER_SUPPLY);
                resources.add(ResourceType.AMBULANCE);
                break;
        }
        return resources;
    }

    // Higher severity = higher priority in queue
    @Override
    public int compareTo(Emergency other) {
        return Integer.compare(other.severity, this.severity);
    }

    public void setStatus(Status status) {
        this.status = status;
        if (status == Status.RESOLVED) {
            this.resolvedAt = System.currentTimeMillis();
        }
    }

    public long getResponseTimeMs() {
        return resolvedAt > 0 ? resolvedAt - createdAt : System.currentTimeMillis() - createdAt;
    }

    public int getId()                             { return id; }
    public String getZone()                        { return zone; }
    public DisasterType getDisasterType()          { return disasterType; }
    public int getSeverity()                       { return severity; }
    public List<ResourceType> getRequiredResources() { return requiredResources; }
    public Status getStatus()                      { return status; }

    @Override
    public String toString() {
        return String.format("[Emergency #%03d | Zone: %-12s | Type: %-15s | Severity: %2d/10 | Status: %s]",
                id, zone, disasterType, severity, status);
    }
}
