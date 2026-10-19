package com.menta.physical.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.shared.domain.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

class DeviceExpiredExceptionTest {

    @Test
    void carries_a_stable_error_code() {
        assertThat(new DeviceExpiredException().getErrorCode()).isEqualTo("DEVICE_EXPIRED");
    }

    @Test
    void is_a_business_exception() {
        assertThat(new DeviceExpiredException()).isInstanceOf(BusinessException.class);
    }
}
