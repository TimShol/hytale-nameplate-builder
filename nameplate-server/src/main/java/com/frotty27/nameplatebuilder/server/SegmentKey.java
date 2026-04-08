package com.frotty27.nameplatebuilder.server;

final class SegmentKey {

    static final int UNASSIGNED = -1;

    private final String pluginId;
    private final String segmentId;
    private final int hash;
    private final int id;

    SegmentKey(String pluginId, String segmentId) {
        this(pluginId, segmentId, UNASSIGNED);
    }

    SegmentKey(String pluginId, String segmentId, int id) {
        this.pluginId = pluginId;
        this.segmentId = segmentId;
        this.hash = 31 * pluginId.hashCode() + segmentId.hashCode();
        this.id = id;
    }

    String pluginId() { return pluginId; }
    String segmentId() { return segmentId; }
    int id() { return id; }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SegmentKey that)) return false;
        return hash == that.hash
                && pluginId.equals(that.pluginId)
                && segmentId.equals(that.segmentId);
    }

    @Override
    public String toString() {
        return "SegmentKey[pluginId=" + pluginId + ", segmentId=" + segmentId + ", id=" + id + "]";
    }
}
