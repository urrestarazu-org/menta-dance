package com.menta.billing.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.PhysicalCoursePricingResult;
import com.menta.billing.application.dto.UpdatePhysicalCoursePricingCommand;
import com.menta.billing.application.port.in.UpdatePhysicalCoursePricingUseCase;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class TransactionalUpdatePhysicalCoursePricingUseCaseTest {

    @Test
    void delegates_the_update_call_and_returns_its_result() {
        UpdatePhysicalCoursePricingUseCase delegate = mock(UpdatePhysicalCoursePricingUseCase.class);
        UpdatePhysicalCoursePricingCommand command =
            new UpdatePhysicalCoursePricingCommand(new BigDecimal("100.00"), "ARS", new BigDecimal("10"), "motivo");
        UUID actingUserId = UUID.randomUUID();
        PhysicalCoursePricingResult expected =
            new PhysicalCoursePricingResult("course-1", new BigDecimal("100.00"), "ARS", new BigDecimal("10"), 1,
                Instant.now());
        when(delegate.update("course-1", command, actingUserId, false)).thenReturn(expected);

        TransactionalUpdatePhysicalCoursePricingUseCase decorator =
            new TransactionalUpdatePhysicalCoursePricingUseCase(delegate);
        PhysicalCoursePricingResult result = decorator.update("course-1", command, actingUserId, false);

        assertThat(result).isEqualTo(expected);
        verify(delegate).update("course-1", command, actingUserId, false);
    }

    @Test
    void marks_the_update_method_transactional_so_the_pricing_save_and_audit_append_commit_atomically()
        throws NoSuchMethodException {
        Method updateMethod = TransactionalUpdatePhysicalCoursePricingUseCase.class.getMethod(
            "update", String.class, UpdatePhysicalCoursePricingCommand.class, UUID.class, boolean.class
        );

        assertThat(updateMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
