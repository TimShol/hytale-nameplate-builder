package com.frotty27.nameplatebuilder.api;

/**
 * Base exception for all Nameplate Builder API errors.
 *
 * @see NameplateNotInitializedException
 * @see NameplateArgumentException
 */
public class NameplateException extends RuntimeException {

    public NameplateException(String message) {
        super(message);
    }

    public NameplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
