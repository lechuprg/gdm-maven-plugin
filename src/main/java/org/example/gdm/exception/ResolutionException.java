package org.example.gdm.exception;

/**
 * Exception thrown when dependency resolution fails.
 */
public class ResolutionException extends GdmException {

    public ResolutionException(String message) {
        super(message);
    }

    public ResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

