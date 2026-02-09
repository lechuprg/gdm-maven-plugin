package org.example.gdm.exception;

/**
 * Exception thrown when database export fails.
 */
public class ExportException extends GdmException {

    public ExportException(String message) {
        super(message);
    }

    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}

