package models;

public enum ResourceType {
    AMBULANCE("Ambulance", 8),
    SHELTER("Shelter", 5),
    FIRE_RESCUE_TEAM("Fire Rescue Team", 6),
    FOOD_WATER_SUPPLY("Food/Water Supply", 10),
    ROAD_CREW("Road Crew", 4);

    private final String displayName;
    private final int totalUnits;

    ResourceType(String displayName, int totalUnits) {
        this.displayName = displayName;
        this.totalUnits  = totalUnits;
    }

    public String getDisplayName() { return displayName; }
    public int getTotalUnits()     { return totalUnits; }

    @Override
    public String toString() { return displayName; }
}
