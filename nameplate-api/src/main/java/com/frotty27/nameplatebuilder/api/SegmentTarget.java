package com.frotty27.nameplatebuilder.api;

public enum SegmentTarget {

    ALL("All"),

    PLAYERS("Players"),

    NPCS("NPCs");

    private final String label;

    SegmentTarget(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
