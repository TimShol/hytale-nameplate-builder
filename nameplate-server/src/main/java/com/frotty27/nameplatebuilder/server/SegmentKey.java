package com.frotty27.nameplatebuilder.server;

/**
 * Identifier for a registered segment. The integer {@link #id()} is assigned
 * sequentially by the registry and used for array-based lookups in hot paths.
 * Equality is based on pluginId + segmentId only (id is not included).
 */
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SegmentKey other)) return false;
        return hash == other.hash
                && pluginId.equals(other.pluginId)
                && segmentId.equals(other.segmentId);
    }

    @Override
    public String toString() {
        return "SegmentKey[pluginId=" + pluginId + ", segmentId=" + segmentId + ", id=" + id + "]";
    }
}
