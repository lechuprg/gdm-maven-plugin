package org.example.gdm.exception;

/**
 * Base exception for all GDM plugin errors.
 */
public class GdmException extends Exception {

    public GdmException(String message) {
        super(message);
    }

    public GdmException(String message, Throwable cause) {
        super(message, cause);
    }
}

