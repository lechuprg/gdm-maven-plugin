package org.example.gdm.export;

import org.example.gdm.exception.ExportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.concurrent.Callable;

/**
 * Executes operations with retry logic for transient connection errors.
 *
 * <p>Retry is triggered ONLY for:</p>
 * <ul>
 *   <li>Connection timeout (SocketTimeoutException)</li>
 *   <li>Connection refused (ConnectException)</li>
 *   <li>Network errors (IOException subtypes related to network)</li>
 * </ul>
 *
 * <p>NO retry for:</p>
 * <ul>
 *   <li>SQL syntax errors</li>
 *   <li>Constraint violations</li>
 *   <li>Authentication failures</li>
 *   <li>Authorization failures</li>
 * </ul>
 */
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    private final int maxAttempts;
    private final long backoffMs;

    /**
     * Creates a RetryExecutor with default settings (3 attempts, 2s backoff).
     */
    public RetryExecutor() {
        this(3, 2000);
    }

    /**
     * Creates a RetryExecutor with custom settings.
     *
     * @param maxAttempts maximum number of retry attempts
     * @param backoffMs   backoff time in milliseconds between retries
     */
    public RetryExecutor(int maxAttempts, long backoffMs) {
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    /**
     * Executes the given operation with retry logic.
     *
     * @param operation   the operation to execute
     * @param description description for logging
     * @param <T>         the return type
     * @return the operation result
     * @throws ExportException if all retries are exhausted or non-retryable error occurs
     */
    public <T> T execute(Callable<T> operation, String description) throws ExportException {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.call();
            } catch (Exception e) {
                lastException = e;

                if (!isRetryable(e)) {
                    log.error("{} failed with non-retryable error: {}", description, e.getMessage());
                    throw wrapException(e, description);
                }

                if (attempt < maxAttempts) {
                    log.warn("{} failed (attempt {}/{}), retrying in {}ms: {}",
                            description, attempt, maxAttempts, backoffMs, e.getMessage());
                    sleep(backoffMs);
                } else {
                    log.error("{} failed after {} attempts: {}",
                            description, maxAttempts, e.getMessage());
                }
            }
        }

        throw new ExportException(
                String.format("%s failed after %d attempts", description, maxAttempts),
                lastException
        );
    }

    /**
     * Executes the given operation with retry logic (void return).
     *
     * @param operation   the operation to execute
     * @param description description for logging
     * @throws ExportException if all retries are exhausted or non-retryable error occurs
     */
    public void executeVoid(VoidCallable operation, String description) throws ExportException {
        execute(() -> {
            operation.call();
            return null;
        }, description);
    }

    /**
     * Determines if the given exception is retryable.
     */
    public boolean isRetryable(Throwable e) {
        if (e == null) {
            return false;
        }

        // Check direct exception types
        if (isConnectionException(e)) {
            return true;
        }

        // Check wrapped exceptions (e.g., SQLException wrapping network error)
        Throwable cause = e.getCause();
        while (cause != null) {
            if (isConnectionException(cause)) {
                return true;
            }
            cause = cause.getCause();
        }

        // Check for specific SQL state codes indicating connection issues
        if (e instanceof SQLException) {
            String sqlState = ((SQLException) e).getSQLState();
            if (sqlState != null && isConnectionSqlState(sqlState)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the exception is a connection-related exception.
     */
    private boolean isConnectionException(Throwable e) {
        // Socket/connection errors
        if (e instanceof SocketTimeoutException) {
            return true;
        }
        if (e instanceof ConnectException) {
            return true;
        }
        // General network I/O errors (but not all IOExceptions)
        if (e instanceof IOException) {
            String message = e.getMessage();
            if (message != null) {
                String lowerMessage = message.toLowerCase();
                return lowerMessage.contains("connection") ||
                       lowerMessage.contains("network") ||
                       lowerMessage.contains("timeout") ||
                       lowerMessage.contains("refused") ||
                       lowerMessage.contains("unreachable") ||
                       lowerMessage.contains("reset");
            }
        }
        return false;
    }

    /**
     * Checks if the SQL state indicates a connection issue.
     * SQL states starting with "08" are connection exceptions.
     */
    private boolean isConnectionSqlState(String sqlState) {
        return sqlState.startsWith("08"); // Connection exception class
    }

    /**
     * Wraps the exception in an ExportException.
     */
    private ExportException wrapException(Exception e, String description) {
        if (e instanceof ExportException) {
            return (ExportException) e;
        }
        return new ExportException(description + " failed: " + e.getMessage(), e);
    }

    /**
     * Sleeps for the specified duration.
     */
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Retry sleep interrupted");
        }
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getBackoffMs() {
        return backoffMs;
    }

    /**
     * Functional interface for void operations.
     */
    @FunctionalInterface
    public interface VoidCallable {
        void call() throws Exception;
    }
}

