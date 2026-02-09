package org.example.gdm.exception;

/**
 * Exception thrown when configuration validation fails.
 */
public class ConfigurationException extends GdmException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

