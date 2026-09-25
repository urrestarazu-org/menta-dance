package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.PhysicalDeviceView;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.exception.DeviceNotFoundException;
import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.DeviceStatus;
import com.menta.physical.domain.model.PhysicalDevice;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetPhysicalDeviceUseCaseImplTest {

    private final PhysicalDeviceRepository deviceRepository = mock(PhysicalDeviceRepository.class);
    private final GetPhysicalDeviceUseCaseImpl useCase = new GetPhysicalDeviceUseCaseImpl(deviceRepository);

    @Test
    void returns_the_view_for_a_known_id() {
        DeviceId deviceId = DeviceId.generate();
        PhysicalDevice device = new PhysicalDevice(
            deviceId, "Puerta 1", "Salon 1", "a".repeat(64), DeviceStatus.ACTIVE, null, Instant.now(), Instant.now()
        );
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        PhysicalDeviceView view = useCase.get(deviceId.getValue().toString());

        assertThat(view.id()).isEqualTo(deviceId.getValue());
        assertThat(view.status()).isEqualTo(DeviceStatus.ACTIVE);
    }

    @Test
    void unknown_id_throws_device_not_found() {
        DeviceId deviceId = DeviceId.generate();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(deviceId.getValue().toString()))
            .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    void malformed_id_throws_illegal_argument() {
        assertThatThrownBy(() -> useCase.get("not-a-uuid")).isInstanceOf(IllegalArgumentException.class);
    }
}
