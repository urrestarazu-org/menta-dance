package com.menta.physical.infrastructure.persistence.adapter;

import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.PhysicalDevice;
import com.menta.physical.infrastructure.persistence.mapper.PhysicalDeviceJpaMapper;
import com.menta.physical.infrastructure.persistence.repository.PhysicalDeviceJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for the device registry (#44, US-PHYSICAL-007, design C3). */
@Component
public class PhysicalDeviceRepositoryAdapter implements PhysicalDeviceRepository {

    private final PhysicalDeviceJpaRepository deviceRepository;

    public PhysicalDeviceRepositoryAdapter(PhysicalDeviceJpaRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public PhysicalDevice save(PhysicalDevice device) {
        return PhysicalDeviceJpaMapper.toDomain(
            deviceRepository.save(PhysicalDeviceJpaMapper.toEntity(device))
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<PhysicalDevice> findById(DeviceId deviceId) {
        return deviceRepository.findById(deviceId.getValue()).map(PhysicalDeviceJpaMapper::toDomain);
    }

    /** Ordered created_at ASC, id ASC (design C3) -- unpaginated: a device fleet is a handful of readers. */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<PhysicalDevice> findAll() {
        return deviceRepository.findAllByOrderByCreatedAtAscIdAsc().stream()
            .map(PhysicalDeviceJpaMapper::toDomain)
            .toList();
    }
}
