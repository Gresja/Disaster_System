package models;

public enum DisasterType {
    EARTHQUAKE("Earthquake", 5),
    FLOOD("Flood", 4),
    FOREST_FIRE("Forest Fire", 4),
    LANDSLIDE("Landslide", 3),
    STORM("Storm", 3);

    private final String displayName;
    private final int baseSeverity;

    DisasterType(String displayName, int baseSeverity) {
        this.displayName = displayName;
        this.baseSeverity = baseSeverity;
    }

    public String getDisplayName() { return displayName; }
    public int getBaseSeverity()   { return baseSeverity; }

    @Override
    public String toString() { return displayName; }
}
