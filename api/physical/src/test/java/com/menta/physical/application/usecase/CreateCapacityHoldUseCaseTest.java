package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.port.out.PhysicalCapacityHoldWriter;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.shared.physical.MultiSessionCapacityHoldCommand;
import com.menta.shared.physical.SessionClaim;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * {@code holdAll} is the hold-side twin of
 * {@code AssignCapacityUseCase#assignAll} (design step 3): iterates claims
 * in order, delegates the invariant decision entirely to
 * {@link PhysicalCapacityHoldWriter#assertHold}, and stops at the first
 * failure — the surrounding {@code REQUIRES_NEW} transaction rolls back
 * every earlier insert in the same call automatically.
 */
class CreateCapacityHoldUseCaseTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-08-24T13:30:00Z");

    private static SessionClaim claim(UUID sessionId, long offsetSeconds) {
        return new SessionClaim(sessionId, Instant.EPOCH.plusSeconds(offsetSeconds));
    }

    @Test
    void holdAll_claims_every_session_in_order_and_returns_them_in_claim_order() {
        PhysicalCapacityHoldWriter writer = mock(PhysicalCapacityHoldWriter.class);
        ReleaseCapacityHoldUseCase releaseUseCase = new ReleaseCapacityHoldUseCase(writer);
        CreateCapacityHoldUseCase useCase = new CreateCapacityHoldUseCase(writer, releaseUseCase);

        UUID session1 = UUID.randomUUID();
        UUID session2 = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        MultiSessionCapacityHoldCommand command = new MultiSessionCapacityHoldCommand(
            List.of(claim(session1, 0), claim(session2, 1)), paymentId
        );

        CapacityHolds result = useCase.holdAll(command, EXPIRES_AT);

        assertThat(result.heldSessionIds()).containsExactly(session1, session2);

        InOrder order = inOrder(writer);
        order.verify(writer).assertHold(session1, paymentId, EXPIRES_AT);
        order.verify(writer).assertHold(session2, paymentId, EXPIRES_AT);
    }

    @Test
    void holdAll_stops_at_the_first_failure_and_never_claims_later_sessions() {
        PhysicalCapacityHoldWriter writer = mock(PhysicalCapacityHoldWriter.class);
        ReleaseCapacityHoldUseCase releaseUseCase = new ReleaseCapacityHoldUseCase(writer);
        CreateCapacityHoldUseCase useCase = new CreateCapacityHoldUseCase(writer, releaseUseCase);

        UUID session1 = UUID.randomUUID();
        UUID session2 = UUID.randomUUID();
        UUID session3 = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(writer.assertHold(eq(session2), any(UUID.class), any(Instant.class)))
            .thenThrow(new CapacityBelowAssignedException());

        MultiSessionCapacityHoldCommand command = new MultiSessionCapacityHoldCommand(
            List.of(claim(session1, 0), claim(session2, 1), claim(session3, 2)), paymentId
        );

        assertThatThrownBy(() -> useCase.holdAll(command, EXPIRES_AT))
            .isInstanceOf(CapacityBelowAssignedException.class);

        verify(writer).assertHold(session1, paymentId, EXPIRES_AT);
        verify(writer).assertHold(session2, paymentId, EXPIRES_AT);
        verify(writer, never()).assertHold(eq(session3), any(UUID.class), any(Instant.class));
    }

    @Test
    void holdAll_maps_a_unique_row_collision_to_CapacityBelowAssigned() {
        PhysicalCapacityHoldWriter writer = mock(PhysicalCapacityHoldWriter.class);
        ReleaseCapacityHoldUseCase releaseUseCase = new ReleaseCapacityHoldUseCase(writer);
        CreateCapacityHoldUseCase useCase = new CreateCapacityHoldUseCase(writer, releaseUseCase);

        UUID session1 = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(writer.assertHold(eq(session1), any(UUID.class), any(Instant.class)))
            .thenThrow(new DataIntegrityViolationException("uq_physical_holds_payment_session"));

        MultiSessionCapacityHoldCommand command = new MultiSessionCapacityHoldCommand(
            List.of(claim(session1, 0)), paymentId
        );

        assertThatThrownBy(() -> useCase.holdAll(command, EXPIRES_AT))
            .isInstanceOf(CapacityBelowAssignedException.class);
    }

    @Test
    void release_delegates_to_the_release_use_case() {
        PhysicalCapacityHoldWriter writer = mock(PhysicalCapacityHoldWriter.class);
        ReleaseCapacityHoldUseCase releaseUseCase = mock(ReleaseCapacityHoldUseCase.class);
        CreateCapacityHoldUseCase useCase = new CreateCapacityHoldUseCase(writer, releaseUseCase);
        UUID paymentId = UUID.randomUUID();

        useCase.release(paymentId);

        verify(releaseUseCase).release(paymentId);
    }
}
