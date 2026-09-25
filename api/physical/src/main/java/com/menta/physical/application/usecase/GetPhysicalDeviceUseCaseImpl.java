package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalDeviceView;
import com.menta.physical.application.port.in.GetPhysicalDeviceUseCase;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.exception.DeviceNotFoundException;
import com.menta.physical.domain.model.DeviceId;

/** #44, US-PHYSICAL-007, design C4. */
public class GetPhysicalDeviceUseCaseImpl implements GetPhysicalDeviceUseCase {

    private final PhysicalDeviceRepository deviceRepository;

    public GetPhysicalDeviceUseCaseImpl(PhysicalDeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    public PhysicalDeviceView get(String deviceId) {
        return deviceRepository.findById(DeviceId.of(deviceId))
            .map(PhysicalDeviceResultMapper::toView)
            .orElseThrow(DeviceNotFoundException::new);
    }
}
