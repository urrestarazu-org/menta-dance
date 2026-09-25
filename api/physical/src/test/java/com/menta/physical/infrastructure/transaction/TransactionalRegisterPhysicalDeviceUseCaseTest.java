package com.menta.physical.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.PhysicalDeviceSecretResult;
import com.menta.physical.application.dto.PhysicalDeviceView;
import com.menta.physical.application.port.in.RegisterPhysicalDeviceUseCase;
import com.menta.physical.application.dto.RegisterPhysicalDeviceCommand;
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
class TransactionalRegisterPhysicalDeviceUseCaseTest {

    @Mock private RegisterPhysicalDeviceUseCase delegate;

    @Test
    void delegates_the_register_call() {
        RegisterPhysicalDeviceCommand command = new RegisterPhysicalDeviceCommand("Puerta 1", "Salon 1", null);
        UUID actorId = UUID.randomUUID();
        PhysicalDeviceSecretResult result = new PhysicalDeviceSecretResult(
            new PhysicalDeviceView(
                UUID.randomUUID(), "Puerta 1", "Salon 1", DeviceStatus.ACTIVE, null, Instant.now(), Instant.now()
            ),
            "raw-secret"
        );
        when(delegate.register(command, actorId)).thenReturn(result);

        TransactionalRegisterPhysicalDeviceUseCase decorator = new TransactionalRegisterPhysicalDeviceUseCase(delegate);
        PhysicalDeviceSecretResult actual = decorator.register(command, actorId);

        assertThat(actual).isEqualTo(result);
        verify(delegate).register(command, actorId);
    }

    @Test
    void marks_the_register_method_transactional_so_the_mutation_and_its_audit_row_share_one_commit()
        throws NoSuchMethodException {
        Method registerMethod = TransactionalRegisterPhysicalDeviceUseCase.class.getMethod(
            "register", RegisterPhysicalDeviceCommand.class, UUID.class
        );

        assertThat(registerMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
