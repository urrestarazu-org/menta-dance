package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/** Thrown when a {@code deviceId} does not resolve to an existing device (#44, US-PHYSICAL-007). */
public class DeviceNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "DEVICE_NOT_FOUND";

    public DeviceNotFoundException() {
        super(ERROR_CODE, "Device not found");
    }
}
