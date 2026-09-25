package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalDeviceSecretResult;
import com.menta.physical.application.dto.RegisterPhysicalDeviceCommand;
import com.menta.physical.application.port.in.RegisterPhysicalDeviceUseCase;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.DeviceSecretGenerator;
import com.menta.physical.application.port.out.DeviceSecretHasher;
import com.menta.physical.application.port.out.PhysicalDeviceAuditRepository;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.model.PhysicalDevice;
import java.time.Instant;
import java.util.UUID;

/** #44, US-PHYSICAL-007, design C4. */
public class RegisterPhysicalDeviceUseCaseImpl implements RegisterPhysicalDeviceUseCase {

    private final PhysicalDeviceRepository deviceRepository;
    private final PhysicalDeviceAuditRepository auditRepository;
    private final DeviceSecretGenerator secretGenerator;
    private final DeviceSecretHasher secretHasher;
    private final Clock clock;

    public RegisterPhysicalDeviceUseCaseImpl(
        PhysicalDeviceRepository deviceRepository, PhysicalDeviceAuditRepository auditRepository,
        DeviceSecretGenerator secretGenerator, DeviceSecretHasher secretHasher, Clock clock
    ) {
        this.deviceRepository = deviceRepository;
        this.auditRepository = auditRepository;
        this.secretGenerator = secretGenerator;
        this.secretHasher = secretHasher;
        this.clock = clock;
    }

    @Override
    public PhysicalDeviceSecretResult register(RegisterPhysicalDeviceCommand command, UUID actorId) {
        String rawSecret = secretGenerator.generate();
        String secretHash = secretHasher.hash(rawSecret);
        Instant now = clock.now();

        PhysicalDevice device = PhysicalDevice.register(
            command.name(), command.location(), secretHash, command.expiresAt(), now
        );
        PhysicalDevice saved = deviceRepository.save(device);

        auditRepository.append(
            saved.getId(), actorId, "DEVICE_REGISTERED", null, PhysicalDeviceAuditSnapshot.registered(saved)
        );

        return new PhysicalDeviceSecretResult(PhysicalDeviceResultMapper.toView(saved), rawSecret);
    }
}
