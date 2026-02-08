package com.frotty27.nameplatebuilder.api;

/**
 * Produces dynamic nameplate text based on which entity is being viewed and by whom.
 *
 * <p>Implementations are called each tick for every visible entity that has this
 * segment enabled. Keep the logic lightweight to avoid performance issues.</p>
 *
 * @see NameplateAPI#register(com.hypixel.hytale.server.core.plugin.JavaPlugin, String, String, INameplateTextProvider)
 */
@FunctionalInterface
public interface INameplateTextProvider {

    /**
     * Produce the text for this segment.
     *
     * @param context information about the entity and viewer
     * @return the text to display, or {@code null}/blank to skip this segment
     */
    String getText(NameplateContext context);
}
