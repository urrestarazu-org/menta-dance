package com.menta.physical.infrastructure.transaction;

import com.menta.physical.application.dto.PhysicalDeviceSecretResult;
import com.menta.physical.application.port.in.RotatePhysicalDeviceSecretUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Transactional decorator (#44, US-PHYSICAL-007, design C5) -- see {@link TransactionalRegisterPhysicalDeviceUseCase}. */
public class TransactionalRotatePhysicalDeviceSecretUseCase implements RotatePhysicalDeviceSecretUseCase {

    private final RotatePhysicalDeviceSecretUseCase delegate;

    public TransactionalRotatePhysicalDeviceSecretUseCase(RotatePhysicalDeviceSecretUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public PhysicalDeviceSecretResult rotate(String deviceId, UUID actorId) {
        return delegate.rotate(deviceId, actorId);
    }
}
