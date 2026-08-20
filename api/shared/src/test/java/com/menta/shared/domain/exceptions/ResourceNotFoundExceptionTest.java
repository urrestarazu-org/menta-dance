package com.menta.shared.domain.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ResourceNotFoundExceptionTest {

    @Test
    void exposesTheGivenMessageAndTheResourceNotFoundErrorCode() {
        ResourceNotFoundException exception = new ResourceNotFoundException("no encontrado");

        assertEquals("no encontrado", exception.getMessage());
        assertEquals("RESOURCE_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void buildsAMessageFromResourceTypeAndId() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Course", "abc-123");

        assertEquals("Course with id abc-123 not found", exception.getMessage());
        assertEquals("RESOURCE_NOT_FOUND", exception.getErrorCode());
    }
}
