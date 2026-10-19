package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalDeviceView;
import com.menta.physical.application.port.in.ListPhysicalDevicesUseCase;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import java.util.List;

/** #44, US-PHYSICAL-007, design C3/C4. Unpaginated: a device fleet is a handful of readers. */
public class ListPhysicalDevicesUseCaseImpl implements ListPhysicalDevicesUseCase {

    private final PhysicalDeviceRepository deviceRepository;

    public ListPhysicalDevicesUseCaseImpl(PhysicalDeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    public List<PhysicalDeviceView> list() {
        return deviceRepository.findAll().stream().map(PhysicalDeviceResultMapper::toView).toList();
    }
}
