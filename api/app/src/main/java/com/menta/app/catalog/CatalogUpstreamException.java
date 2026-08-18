package com.menta.app.catalog;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when Physical or Virtual's port throws unexpectedly while composing
 * the catalog (#95 acceptance criteria: an owning module's failure must map
 * to a specific RFC 9457 problem, never an opaque 500).
 */
public class CatalogUpstreamException extends BusinessException {

    private static final String ERROR_CODE = "CATALOG_DEGRADED";

    public CatalogUpstreamException(String moduleName, Throwable cause) {
        super(ERROR_CODE, "The " + moduleName + " catalog is temporarily unavailable", cause);
    }
}
