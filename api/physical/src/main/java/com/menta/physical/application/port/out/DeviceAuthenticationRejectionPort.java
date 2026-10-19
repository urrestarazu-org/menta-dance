package com.menta.physical.application.port.out;

import com.menta.physical.application.dto.DeviceAuthenticationRejection;

/**
 * Out-port that makes a rejected QR check-in device authentication observable to operators (#266).
 * Not a response to the caller: the rejection itself is thrown as a domain exception.
 */
public interface DeviceAuthenticationRejectionPort {

    /** Reports the rejection once. Implementations must not throw: the 401 must still be sent. */
    void report(DeviceAuthenticationRejection rejection);
}
