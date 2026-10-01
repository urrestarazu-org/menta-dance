package com.menta.physical.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.shared.domain.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

class CheckInDeviceRevokedExceptionTest {

    @Test
    void carries_a_stable_error_code() {
        assertThat(new CheckInDeviceRevokedException().getErrorCode()).isEqualTo("DEVICE_REVOKED");
    }

    @Test
    void is_a_business_exception_distinct_from_the_admin_rotation_one() {
        assertThat(new CheckInDeviceRevokedException())
            .isInstanceOf(BusinessException.class)
            .isNotInstanceOf(DeviceRevokedException.class);
    }
}
