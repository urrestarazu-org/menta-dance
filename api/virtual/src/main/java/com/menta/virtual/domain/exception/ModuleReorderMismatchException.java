package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a reorder request's module id list does not contain exactly
 * the course's current set of modules — neither missing one nor including a
 * module from another course (US-VIRTUAL-006 escenario 7).
 */
public class ModuleReorderMismatchException extends BusinessException {

    private static final String ERROR_CODE = "MODULE_REORDER_MISMATCH";

    public ModuleReorderMismatchException() {
        super(ERROR_CODE, "The reorder list must contain exactly the course's current modules, no more, no less");
    }
}
