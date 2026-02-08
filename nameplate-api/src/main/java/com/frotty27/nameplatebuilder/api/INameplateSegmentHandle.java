package com.frotty27.nameplatebuilder.api;

/**
 * Handle returned by {@link NameplateAPI#register} that allows removing a segment at runtime.
 *
 * <p>Segments are automatically cleaned up when the server shuts down,
 * so holding and calling this handle is only necessary if you need to
 * remove a segment while the server is still running (e.g. a temporary buff).</p>
 */
public interface INameplateSegmentHandle {

    /** Remove this segment from the registry. */
    void unregister();
}
