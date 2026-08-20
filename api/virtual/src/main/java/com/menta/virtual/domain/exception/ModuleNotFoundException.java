package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/** Thrown when a module id does not exist (US-VIRTUAL-006 management endpoints). */
public class ModuleNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "MODULE_NOT_FOUND";

    public ModuleNotFoundException() {
        super(ERROR_CODE, "Module not found");
    }
}
