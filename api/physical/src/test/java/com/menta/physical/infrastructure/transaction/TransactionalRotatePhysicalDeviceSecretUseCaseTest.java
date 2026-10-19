package com.menta.physical.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.PhysicalDeviceSecretResult;
import com.menta.physical.application.dto.PhysicalDeviceView;
import com.menta.physical.application.port.in.RotatePhysicalDeviceSecretUseCase;
import com.menta.physical.domain.model.DeviceStatus;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalRotatePhysicalDeviceSecretUseCaseTest {

    @Mock private RotatePhysicalDeviceSecretUseCase delegate;

    @Test
    void delegates_the_rotate_call() {
        String deviceId = UUID.randomUUID().toString();
        UUID actorId = UUID.randomUUID();
        PhysicalDeviceSecretResult result = new PhysicalDeviceSecretResult(
            new PhysicalDeviceView(
                UUID.randomUUID(), "Puerta 1", "Salon 1", DeviceStatus.ACTIVE, null, Instant.now(), Instant.now()
            ),
            "raw-secret"
        );
        when(delegate.rotate(deviceId, actorId)).thenReturn(result);

        TransactionalRotatePhysicalDeviceSecretUseCase decorator =
            new TransactionalRotatePhysicalDeviceSecretUseCase(delegate);
        PhysicalDeviceSecretResult actual = decorator.rotate(deviceId, actorId);

        assertThat(actual).isEqualTo(result);
        verify(delegate).rotate(deviceId, actorId);
    }

    @Test
    void marks_the_rotate_method_transactional_so_the_mutation_and_its_audit_row_share_one_commit()
        throws NoSuchMethodException {
        Method rotateMethod =
            TransactionalRotatePhysicalDeviceSecretUseCase.class.getMethod("rotate", String.class, UUID.class);

        assertThat(rotateMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
