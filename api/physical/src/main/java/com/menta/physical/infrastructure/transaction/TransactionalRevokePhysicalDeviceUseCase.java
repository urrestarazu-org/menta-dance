package com.menta.physical.infrastructure.transaction;

import com.menta.physical.application.dto.PhysicalDeviceView;
import com.menta.physical.application.port.in.RevokePhysicalDeviceUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Transactional decorator (#44, US-PHYSICAL-007, design C5) -- see {@link TransactionalRegisterPhysicalDeviceUseCase}. */
public class TransactionalRevokePhysicalDeviceUseCase implements RevokePhysicalDeviceUseCase {

    private final RevokePhysicalDeviceUseCase delegate;

    public TransactionalRevokePhysicalDeviceUseCase(RevokePhysicalDeviceUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public PhysicalDeviceView revoke(String deviceId, UUID actorId) {
        return delegate.revoke(deviceId, actorId);
    }
}
