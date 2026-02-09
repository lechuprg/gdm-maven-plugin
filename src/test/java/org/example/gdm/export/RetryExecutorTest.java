package org.example.gdm.export;

import org.example.gdm.exception.ExportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for RetryExecutor.
 */
class RetryExecutorTest {

    private RetryExecutor executor;

    @BeforeEach
    void setUp() {
        // Use short backoff for faster tests
        executor = new RetryExecutor(3, 100);
    }

    @Nested
    @DisplayName("Successful Execution")
    class SuccessfulExecution {

        @Test
        @DisplayName("should return result on first success")
        void shouldReturnResultOnFirstSuccess() throws ExportException {
            String result = executor.execute(() -> "success", "test operation");

            assertThat(result).isEqualTo("success");
        }

        @Test
        @DisplayName("should succeed after retries")
        void shouldSucceedAfterRetries() throws ExportException {
            AtomicInteger attempts = new AtomicInteger(0);

            String result = executor.execute(() -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new ConnectException("Connection refused");
                }
                return "success on third attempt";
            }, "test operation");

            assertThat(result).isEqualTo("success on third attempt");
            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("should execute void operation")
        void shouldExecuteVoidOperation() throws ExportException {
            AtomicInteger counter = new AtomicInteger(0);

            executor.executeVoid(counter::incrementAndGet, "void operation");

            assertThat(counter.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Retryable Exceptions")
    class RetryableExceptions {

        @Test
        @DisplayName("should retry on SocketTimeoutException")
        void shouldRetryOnSocketTimeoutException() {
            assertThat(executor.isRetryable(new SocketTimeoutException("timeout"))).isTrue();
        }

        @Test
        @DisplayName("should retry on ConnectException")
        void shouldRetryOnConnectException() {
            assertThat(executor.isRetryable(new ConnectException("refused"))).isTrue();
        }

        @Test
        @DisplayName("should retry on IOException with connection message")
        void shouldRetryOnConnectionIOException() {
            assertThat(executor.isRetryable(new IOException("Connection reset"))).isTrue();
            assertThat(executor.isRetryable(new IOException("Network unreachable"))).isTrue();
            assertThat(executor.isRetryable(new IOException("Connection refused"))).isTrue();
        }

        @Test
        @DisplayName("should retry on SQLException with 08 state code")
        void shouldRetryOnConnectionSqlState() {
            SQLException connectionException = new SQLException("Connection error", "08001");
            assertThat(executor.isRetryable(connectionException)).isTrue();
        }

        @Test
        @DisplayName("should retry on wrapped connection exception")
        void shouldRetryOnWrappedException() {
            Exception wrapped = new RuntimeException("Wrapper", new ConnectException("refused"));
            assertThat(executor.isRetryable(wrapped)).isTrue();
        }
    }

    @Nested
    @DisplayName("Non-Retryable Exceptions")
    class NonRetryableExceptions {

        @Test
        @DisplayName("should not retry on SQLException syntax error")
        void shouldNotRetryOnSqlSyntaxError() {
            SQLException syntaxError = new SQLException("Syntax error", "42000");
            assertThat(executor.isRetryable(syntaxError)).isFalse();
        }

        @Test
        @DisplayName("should not retry on constraint violation")
        void shouldNotRetryOnConstraintViolation() {
            SQLException constraintViolation = new SQLException("Unique constraint violated", "23000");
            assertThat(executor.isRetryable(constraintViolation)).isFalse();
        }

        @Test
        @DisplayName("should not retry on authentication error")
        void shouldNotRetryOnAuthError() {
            SQLException authError = new SQLException("Invalid credentials", "28000");
            assertThat(executor.isRetryable(authError)).isFalse();
        }

        @Test
        @DisplayName("should not retry on generic runtime exception")
        void shouldNotRetryOnGenericRuntimeException() {
            assertThat(executor.isRetryable(new RuntimeException("Some error"))).isFalse();
        }

        @Test
        @DisplayName("should not retry on IllegalArgumentException")
        void shouldNotRetryOnIllegalArgumentException() {
            assertThat(executor.isRetryable(new IllegalArgumentException("Invalid arg"))).isFalse();
        }

        @Test
        @DisplayName("should not retry on null")
        void shouldNotRetryOnNull() {
            assertThat(executor.isRetryable(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Failed Execution")
    class FailedExecution {

        @Test
        @DisplayName("should throw after max retries for retryable error")
        void shouldThrowAfterMaxRetries() {
            AtomicInteger attempts = new AtomicInteger(0);

            assertThatThrownBy(() -> executor.execute(() -> {
                attempts.incrementAndGet();
                throw new ConnectException("Connection refused");
            }, "connect"))
                    .isInstanceOf(ExportException.class)
                    .hasMessageContaining("connect")
                    .hasMessageContaining("3 attempts")
                    .hasCauseInstanceOf(ConnectException.class);

            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("should throw immediately for non-retryable error")
        void shouldThrowImmediatelyForNonRetryable() {
            AtomicInteger attempts = new AtomicInteger(0);

            assertThatThrownBy(() -> executor.execute(() -> {
                attempts.incrementAndGet();
                throw new SQLException("Syntax error", "42000");
            }, "query"))
                    .isInstanceOf(ExportException.class)
                    .hasCauseInstanceOf(SQLException.class);

            // Should only attempt once
            assertThat(attempts.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("should use default settings")
        void shouldUseDefaultSettings() {
            RetryExecutor defaultExecutor = new RetryExecutor();

            assertThat(defaultExecutor.getMaxAttempts()).isEqualTo(3);
            assertThat(defaultExecutor.getBackoffMs()).isEqualTo(2000);
        }

        @Test
        @DisplayName("should use custom settings")
        void shouldUseCustomSettings() {
            RetryExecutor customExecutor = new RetryExecutor(5, 500);

            assertThat(customExecutor.getMaxAttempts()).isEqualTo(5);
            assertThat(customExecutor.getBackoffMs()).isEqualTo(500);
        }
    }
}

