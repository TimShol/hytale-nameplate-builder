package com.frotty27.nameplatebuilder.api;

/**
 * Thrown when the Nameplate Builder API is used before
 * the NameplateBuilder server plugin has finished loading.
 *
 * <p>This usually means your mod's {@code manifest.json} is missing
 * the dependency on {@code "Frotty27:NameplateBuilder": "*"}, or
 * you are calling API methods too early (e.g. in a static initializer).</p>
 */
public final class NameplateNotInitializedException extends NameplateException {

    public NameplateNotInitializedException(String message) {
        super(message);
    }
}
