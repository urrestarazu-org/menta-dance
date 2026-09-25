package com.menta.physical.infrastructure.persistence.adapter;

import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.PhysicalDevice;
import com.menta.physical.infrastructure.persistence.mapper.PhysicalDeviceJpaMapper;
import com.menta.physical.infrastructure.persistence.repository.PhysicalDeviceJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for the device registry (#44, US-PHYSICAL-007, design C3). Wired to the {@code
 * PhysicalDeviceRepository} out-port in P2 -- that port does not exist yet in this slice.
 */
@Component
public class PhysicalDeviceRepositoryAdapter {

    private final PhysicalDeviceJpaRepository deviceRepository;

    public PhysicalDeviceRepositoryAdapter(PhysicalDeviceJpaRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PhysicalDevice save(PhysicalDevice device) {
        return PhysicalDeviceJpaMapper.toDomain(
            deviceRepository.save(PhysicalDeviceJpaMapper.toEntity(device))
        );
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<PhysicalDevice> findById(DeviceId deviceId) {
        return deviceRepository.findById(deviceId.getValue()).map(PhysicalDeviceJpaMapper::toDomain);
    }

    /** Ordered created_at ASC, id ASC (design C3) -- unpaginated: a device fleet is a handful of readers. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<PhysicalDevice> findAll() {
        return deviceRepository.findAllByOrderByCreatedAtAscIdAsc().stream()
            .map(PhysicalDeviceJpaMapper::toDomain)
            .toList();
    }
}
