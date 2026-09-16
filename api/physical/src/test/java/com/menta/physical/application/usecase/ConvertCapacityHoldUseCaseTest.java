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

import com.menta.physical.application.port.out.HeldSessionRow;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentWriter;
import com.menta.physical.application.port.out.PhysicalCapacityHoldWriter;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * {@code convertAll} is the confirmation-time twin of
 * {@code AssignCapacityUseCase#assignAll} (design B3, step 8): it converts
 * an existing hold into real assignments instead of running a fresh
 * capacity claim, per session in the hold's own claim order, marking each
 * hold row converted BEFORE calling the unchanged
 * {@link PhysicalCapacityAssignmentWriter#assertAssignment} — see the
 * class javadoc for why that ordering keeps the invariant arithmetic
 * balanced with zero new invariant logic.
 */
class ConvertCapacityHoldUseCaseTest {

    @Test
    void convertAll_returns_HoldNotFound_when_no_hold_rows_exist_for_the_payment() {
        PhysicalCapacityHoldWriter holdWriter = mock(PhysicalCapacityHoldWriter.class);
        PhysicalCapacityAssignmentWriter assignmentWriter = mock(PhysicalCapacityAssignmentWriter.class);
        ConvertCapacityHoldUseCase useCase = new ConvertCapacityHoldUseCase(holdWriter, assignmentWriter);

        UUID paymentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(holdWriter.findByPaymentIdOrdered(paymentId)).thenReturn(List.of());

        ConvertOutcome outcome = useCase.convertAll(paymentId, studentId);

        assertThat(outcome).isInstanceOf(ConvertOutcome.HoldNotFound.class);
        verify(assignmentWriter, never()).assertAssignment(any(), any());
        verify(holdWriter, never()).markConverted(any(), any());
    }

    @Test
    void convertAll_returns_AlreadyConverted_when_every_row_is_already_converted() {
        PhysicalCapacityHoldWriter holdWriter = mock(PhysicalCapacityHoldWriter.class);
        PhysicalCapacityAssignmentWriter assignmentWriter = mock(PhysicalCapacityAssignmentWriter.class);
        ConvertCapacityHoldUseCase useCase = new ConvertCapacityHoldUseCase(holdWriter, assignmentWriter);

        UUID paymentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID session1 = UUID.randomUUID();
        UUID session2 = UUID.randomUUID();
        when(holdWriter.findByPaymentIdOrdered(paymentId)).thenReturn(List.of(
            new HeldSessionRow(session1, true), new HeldSessionRow(session2, true)
        ));

        ConvertOutcome outcome = useCase.convertAll(paymentId, studentId);

        assertThat(outcome).isInstanceOf(ConvertOutcome.AlreadyConverted.class);
        verify(assignmentWriter, never()).assertAssignment(any(), any());
        verify(holdWriter, never()).markConverted(any(), any());
    }

    @Test
    void convertAll_marks_converted_before_asserting_assignment_per_session_in_order() {
        PhysicalCapacityHoldWriter holdWriter = mock(PhysicalCapacityHoldWriter.class);
        PhysicalCapacityAssignmentWriter assignmentWriter = mock(PhysicalCapacityAssignmentWriter.class);
        ConvertCapacityHoldUseCase useCase = new ConvertCapacityHoldUseCase(holdWriter, assignmentWriter);

        UUID paymentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID session1 = UUID.randomUUID();
        UUID session2 = UUID.randomUUID();
        when(holdWriter.findByPaymentIdOrdered(paymentId)).thenReturn(List.of(
            new HeldSessionRow(session1, false), new HeldSessionRow(session2, false)
        ));
        when(assignmentWriter.assertAssignment(any(), eq(studentId))).thenReturn(Instant.EPOCH);

        ConvertOutcome outcome = useCase.convertAll(paymentId, studentId);

        assertThat(outcome).isInstanceOf(ConvertOutcome.Converted.class);
        assertThat(((ConvertOutcome.Converted) outcome).sessionIds()).containsExactly(session1, session2);

        InOrder order = inOrder(holdWriter, assignmentWriter);
        order.verify(holdWriter).markConverted(paymentId, session1);
        order.verify(assignmentWriter).assertAssignment(session1, studentId);
        order.verify(holdWriter).markConverted(paymentId, session2);
        order.verify(assignmentWriter).assertAssignment(session2, studentId);
    }

    @Test
    void convertAll_maps_a_unique_row_collision_to_CapacityBelowAssigned() {
        PhysicalCapacityHoldWriter holdWriter = mock(PhysicalCapacityHoldWriter.class);
        PhysicalCapacityAssignmentWriter assignmentWriter = mock(PhysicalCapacityAssignmentWriter.class);
        ConvertCapacityHoldUseCase useCase = new ConvertCapacityHoldUseCase(holdWriter, assignmentWriter);

        UUID paymentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID session1 = UUID.randomUUID();
        when(holdWriter.findByPaymentIdOrdered(paymentId)).thenReturn(List.of(new HeldSessionRow(session1, false)));
        when(assignmentWriter.assertAssignment(session1, studentId))
            .thenThrow(new DataIntegrityViolationException("uq_physical_assignment_session_student"));

        assertThatThrownBy(() -> useCase.convertAll(paymentId, studentId))
            .isInstanceOf(CapacityBelowAssignedException.class);

        verify(holdWriter).markConverted(paymentId, session1);
    }

    @Test
    void convertAll_propagates_CapacityBelowAssigned_and_stops_at_the_first_failure() {
        PhysicalCapacityHoldWriter holdWriter = mock(PhysicalCapacityHoldWriter.class);
        PhysicalCapacityAssignmentWriter assignmentWriter = mock(PhysicalCapacityAssignmentWriter.class);
        ConvertCapacityHoldUseCase useCase = new ConvertCapacityHoldUseCase(holdWriter, assignmentWriter);

        UUID paymentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID session1 = UUID.randomUUID();
        UUID session2 = UUID.randomUUID();
        when(holdWriter.findByPaymentIdOrdered(paymentId)).thenReturn(List.of(
            new HeldSessionRow(session1, false), new HeldSessionRow(session2, false)
        ));
        when(assignmentWriter.assertAssignment(session1, studentId))
            .thenThrow(new CapacityBelowAssignedException());

        assertThatThrownBy(() -> useCase.convertAll(paymentId, studentId))
            .isInstanceOf(CapacityBelowAssignedException.class);

        verify(holdWriter).markConverted(paymentId, session1);
        verify(holdWriter, never()).markConverted(paymentId, session2);
        verify(assignmentWriter, never()).assertAssignment(eq(session2), any());
    }
}
