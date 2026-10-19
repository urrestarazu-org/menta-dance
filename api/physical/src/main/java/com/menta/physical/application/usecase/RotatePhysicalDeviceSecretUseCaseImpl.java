package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalDeviceSecretResult;
import com.menta.physical.application.port.in.RotatePhysicalDeviceSecretUseCase;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.DeviceSecretGenerator;
import com.menta.physical.application.port.out.DeviceSecretHasher;
import com.menta.physical.application.port.out.PhysicalDeviceAuditRepository;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.exception.DeviceNotFoundException;
import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.PhysicalDevice;
import java.util.UUID;

/** #44, US-PHYSICAL-007, design C4. */
public class RotatePhysicalDeviceSecretUseCaseImpl implements RotatePhysicalDeviceSecretUseCase {

    private final PhysicalDeviceRepository deviceRepository;
    private final PhysicalDeviceAuditRepository auditRepository;
    private final DeviceSecretGenerator secretGenerator;
    private final DeviceSecretHasher secretHasher;
    private final Clock clock;

    public RotatePhysicalDeviceSecretUseCaseImpl(
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
    public PhysicalDeviceSecretResult rotate(String deviceId, UUID actorId) {
        DeviceId id = DeviceId.of(deviceId);
        PhysicalDevice device = deviceRepository.findById(id).orElseThrow(DeviceNotFoundException::new);
        String previousSnapshot = PhysicalDeviceAuditSnapshot.statusAndHashPrefix(device);

        String rawSecret = secretGenerator.generate();
        String newSecretHash = secretHasher.hash(rawSecret);
        PhysicalDevice rotated = device.rotateSecret(newSecretHash, clock.now());
        PhysicalDevice saved = deviceRepository.save(rotated);

        auditRepository.append(
            id, actorId, "DEVICE_SECRET_ROTATED", previousSnapshot,
            PhysicalDeviceAuditSnapshot.statusAndHashPrefix(saved)
        );

        return new PhysicalDeviceSecretResult(PhysicalDeviceResultMapper.toView(saved), rawSecret);
    }
}
