package com.menta.shared.domain.exceptions;

/**
 * Exception thrown when a requested resource is not found.
 */
public class ResourceNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(ERROR_CODE, message);
    }

    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(ERROR_CODE, String.format("%s with id %s not found", resourceType, resourceId));
    }
}
