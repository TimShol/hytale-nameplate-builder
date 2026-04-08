package com.frotty27.nameplatebuilder.api;

public class NameplateException extends RuntimeException {

    public NameplateException(String message) {
        super(message);
    }

    public NameplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
