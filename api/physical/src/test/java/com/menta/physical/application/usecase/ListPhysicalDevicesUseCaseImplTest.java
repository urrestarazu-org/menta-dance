package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.PhysicalDeviceView;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.DeviceStatus;
import com.menta.physical.domain.model.PhysicalDevice;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListPhysicalDevicesUseCaseImplTest {

    private final PhysicalDeviceRepository deviceRepository = mock(PhysicalDeviceRepository.class);
    private final ListPhysicalDevicesUseCaseImpl useCase = new ListPhysicalDevicesUseCaseImpl(deviceRepository);

    @Test
    void returns_all_devices_active_and_revoked_with_no_filtering() {
        PhysicalDevice active = new PhysicalDevice(
            DeviceId.generate(), "Puerta 1", "Salon 1", "a".repeat(64), DeviceStatus.ACTIVE, null,
            Instant.now(), Instant.now()
        );
        PhysicalDevice revoked = new PhysicalDevice(
            DeviceId.generate(), "Puerta 2", "Salon 2", "b".repeat(64), DeviceStatus.REVOKED, null,
            Instant.now(), Instant.now()
        );
        when(deviceRepository.findAll()).thenReturn(List.of(active, revoked));

        List<PhysicalDeviceView> views = useCase.list();

        assertThat(views).hasSize(2);
        assertThat(views).extracting(PhysicalDeviceView::status)
            .containsExactly(DeviceStatus.ACTIVE, DeviceStatus.REVOKED);
    }
}
