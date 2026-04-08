package com.frotty27.nameplatebuilder.api;

public final class NameplateArgumentException extends NameplateException {

    private final String parameterName;

    public NameplateArgumentException(String parameterName, String message) {
        super(message);
        this.parameterName = parameterName;
    }

    public String getParameterName() {
        return parameterName;
    }
}
