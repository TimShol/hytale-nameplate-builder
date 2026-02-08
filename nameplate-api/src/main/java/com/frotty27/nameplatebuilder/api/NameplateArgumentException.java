package com.frotty27.nameplatebuilder.api;

/**
 * Thrown when an invalid argument is passed to a {@link NameplateAPI} method.
 *
 * <p>Common causes:</p>
 * <ul>
 *   <li>Passing {@code null} for a required parameter</li>
 *   <li>Using a blank segment ID or display name</li>
 *   <li>Passing blank text to {@link NameplateAPI#register} (use {@link NameplateAPI#remove} instead)</li>
 * </ul>
 */
public final class NameplateArgumentException extends NameplateException {

    private final String parameterName;

    public NameplateArgumentException(String parameterName, String message) {
        super(message);
        this.parameterName = parameterName;
    }

    /**
     * Returns the name of the parameter that caused the error.
     *
     * @return parameter name, e.g. {@code "segmentId"} or {@code "text"}
     */
    public String getParameterName() {
        return parameterName;
    }
}
