package com.menta.physical.application.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.menta.physical.application.port.out.PhysicalCapacityHoldWriter;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code release} is a thin delegation to {@link PhysicalCapacityHoldWriter#release}
 * (design step 3): the writer's own {@code findByPaymentIdOrdered} + delete
 * is already a no-op on an unknown payment id, so no branching is needed
 * here.
 */
class ReleaseCapacityHoldUseCaseTest {

    @Test
    void release_delegates_to_the_writer_and_is_a_noop_on_an_unknown_payment() {
        PhysicalCapacityHoldWriter writer = mock(PhysicalCapacityHoldWriter.class);
        ReleaseCapacityHoldUseCase useCase = new ReleaseCapacityHoldUseCase(writer);
        UUID unknownPaymentId = UUID.randomUUID();

        useCase.release(unknownPaymentId);

        verify(writer).release(unknownPaymentId);
    }
}
