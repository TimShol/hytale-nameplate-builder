package com.frotty27.nameplatebuilder.api;

import java.util.UUID;

/**
 * Context passed to a {@link INameplateTextProvider} each tick.
 *
 * @param entityUuid the UUID of the entity whose nameplate is being built
 * @param entityTypeId the Hytale entity type identifier (e.g. {@code "hytale:player"})
 * @param viewerUuid the UUID of the playerRef viewing the nameplate
 */
public record NameplateContext(UUID entityUuid, String entityTypeId, UUID viewerUuid) {
}
