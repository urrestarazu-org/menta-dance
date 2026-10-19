package com.menta.physical.infrastructure.transaction;

import com.menta.physical.application.dto.PhysicalDeviceSecretResult;
import com.menta.physical.application.dto.RegisterPhysicalDeviceCommand;
import com.menta.physical.application.port.in.RegisterPhysicalDeviceUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator (#44, US-PHYSICAL-007, design C5): the device registration and its
 * audit row must share a single commit. Physical's adapters declare {@code
 * Propagation.REQUIRED}, which only joins an ambient transaction, so an outer boundary is
 * required here (mirrors {@code TransactionalCreateVirtualCourseUseCase}).
 */
public class TransactionalRegisterPhysicalDeviceUseCase implements RegisterPhysicalDeviceUseCase {

    private final RegisterPhysicalDeviceUseCase delegate;

    public TransactionalRegisterPhysicalDeviceUseCase(RegisterPhysicalDeviceUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public PhysicalDeviceSecretResult register(RegisterPhysicalDeviceCommand command, UUID actorId) {
        return delegate.register(command, actorId);
    }
}
