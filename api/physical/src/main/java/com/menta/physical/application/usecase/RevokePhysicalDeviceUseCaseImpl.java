package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalDeviceView;
import com.menta.physical.application.port.in.RevokePhysicalDeviceUseCase;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalDeviceAuditRepository;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.exception.DeviceNotFoundException;
import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.PhysicalDevice;
import java.util.UUID;

/** #44, US-PHYSICAL-007, design C4. */
public class RevokePhysicalDeviceUseCaseImpl implements RevokePhysicalDeviceUseCase {

    private final PhysicalDeviceRepository deviceRepository;
    private final PhysicalDeviceAuditRepository auditRepository;
    private final Clock clock;

    public RevokePhysicalDeviceUseCaseImpl(
        PhysicalDeviceRepository deviceRepository, PhysicalDeviceAuditRepository auditRepository, Clock clock
    ) {
        this.deviceRepository = deviceRepository;
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    @Override
    public PhysicalDeviceView revoke(String deviceId, UUID actorId) {
        DeviceId id = DeviceId.of(deviceId);
        PhysicalDevice device = deviceRepository.findById(id).orElseThrow(DeviceNotFoundException::new);
        String previousSnapshot = PhysicalDeviceAuditSnapshot.statusAndHashPrefix(device);

        PhysicalDevice revoked = device.revoke(clock.now());
        PhysicalDevice saved = deviceRepository.save(revoked);

        auditRepository.append(
            id, actorId, "DEVICE_REVOKED", previousSnapshot, PhysicalDeviceAuditSnapshot.statusAndHashPrefix(saved)
        );

        return PhysicalDeviceResultMapper.toView(saved);
    }
}
