package com.menta.shared.domain.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    private static final class TestBusinessException extends BusinessException {
        TestBusinessException(String errorCode, String message) {
            super(errorCode, message);
        }

        TestBusinessException(String errorCode, String message, Throwable cause) {
            super(errorCode, message, cause);
        }
    }

    @Test
    void exposesTheGivenErrorCodeAndMessage() {
        TestBusinessException exception = new TestBusinessException("SOME_ERROR", "algo falló");

        assertEquals("SOME_ERROR", exception.getErrorCode());
        assertEquals("algo falló", exception.getMessage());
    }

    @Test
    void preservesTheGivenCause() {
        RuntimeException cause = new RuntimeException("raíz");

        TestBusinessException exception = new TestBusinessException("SOME_ERROR", "algo falló", cause);

        assertSame(cause, exception.getCause());
    }
}
