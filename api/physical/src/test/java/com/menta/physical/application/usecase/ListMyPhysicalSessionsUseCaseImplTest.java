package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.AssignedSessionRow;
import com.menta.physical.application.dto.AssignedSessionView;
import com.menta.physical.application.dto.AssignedSessionsView;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.domain.exception.InvalidSessionRangeException;
import com.menta.physical.domain.model.CheckInWindow;
import com.menta.shared.physical.AssignmentType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListMyPhysicalSessionsUseCaseImplTest {

    private static final CheckInWindow WINDOW = new CheckInWindow(Duration.ofMinutes(30), Duration.ofHours(2));
    private static final UUID STUDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    // 2026-10-10 15:00 in Buenos Aires.
    private static final Instant NOW = Instant.parse("2026-10-10T18:00:00Z");
    // The local day 2026-10-10 in Buenos Aires, as the client computes it.
    private static final Instant FROM = Instant.parse("2026-10-10T03:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-11T03:00:00Z");

    private final PhysicalCapacityAssignmentRepository repository = mock(PhysicalCapacityAssignmentRepository.class);
    private final Clock clock = mock(Clock.class);
    private final ListMyPhysicalSessionsUseCaseImpl useCase =
        new ListMyPhysicalSessionsUseCaseImpl(repository, clock, WINDOW);

    private static AssignedSessionRow row(String scheduledAt) {
        return row(scheduledAt, AssignmentType.MONTHLY);
    }

    private static AssignedSessionRow row(String scheduledAt, AssignmentType type) {
        return new AssignedSessionRow(
            UUID.randomUUID(), UUID.randomUUID(), "Salsa Intermedio", "Ana Perez", Instant.parse(scheduledAt), type
        );
    }

    @Test
    void carries_each_rows_assignment_type_into_its_view() {
        when(clock.now()).thenReturn(NOW);
        AssignedSessionRow monthly = row("2026-10-10T22:00:00Z", AssignmentType.MONTHLY);
        AssignedSessionRow individual = row("2026-10-10T23:00:00Z", AssignmentType.INDIVIDUAL);
        when(repository.findAssignedSessions(STUDENT_ID, FROM, TO)).thenReturn(List.of(monthly, individual));

        List<AssignedSessionView> sessions = useCase.list(STUDENT_ID, FROM, TO).sessions();

        assertThat(sessions).extracting(AssignedSessionView::assignmentType)
            .containsExactly(AssignmentType.MONTHLY, AssignmentType.INDIVIDUAL);
    }

    @Test
    void the_given_range_goes_to_the_repository_untouched_and_is_echoed_in_the_view() {
        when(clock.now()).thenReturn(NOW);
        when(repository.findAssignedSessions(STUDENT_ID, FROM, TO)).thenReturn(List.of());

        AssignedSessionsView view = useCase.list(STUDENT_ID, FROM, TO);

        assertThat(view.from()).isEqualTo(FROM);
        assertThat(view.to()).isEqualTo(TO);
        assertThat(view.sessions()).isEmpty();
        verify(repository).findAssignedSessions(STUDENT_ID, FROM, TO);
    }

    /** The clock never decides the range: the same query a month later asks for exactly the same bounds. */
    @Test
    void the_range_does_not_depend_on_the_clock() {
        when(clock.now()).thenReturn(NOW.plus(Duration.ofDays(30)));
        when(repository.findAssignedSessions(STUDENT_ID, FROM, TO)).thenReturn(List.of());

        AssignedSessionsView view = useCase.list(STUDENT_ID, FROM, TO);

        assertThat(view.from()).isEqualTo(FROM);
        assertThat(view.to()).isEqualTo(TO);
        verify(repository).findAssignedSessions(STUDENT_ID, FROM, TO);
    }

    @Test
    void maps_rows_in_repository_order_and_flags_only_the_sessions_whose_window_is_open_now() {
        when(clock.now()).thenReturn(NOW);
        AssignedSessionRow openNow = row("2026-10-10T18:20:00Z");
        AssignedSessionRow tonightTooEarly = row("2026-10-10T22:00:00Z");
        AssignedSessionRow tomorrow = row("2026-10-11T02:30:00Z");
        when(repository.findAssignedSessions(STUDENT_ID, FROM, TO))
            .thenReturn(List.of(openNow, tonightTooEarly, tomorrow));

        List<AssignedSessionView> sessions = useCase.list(STUDENT_ID, FROM, TO).sessions();

        assertThat(sessions).extracting(AssignedSessionView::sessionId)
            .containsExactly(openNow.sessionId(), tonightTooEarly.sessionId(), tomorrow.sessionId());
        assertThat(sessions).extracting(AssignedSessionView::checkInOpen).containsExactly(true, false, false);
        AssignedSessionView first = sessions.get(0);
        assertThat(first.courseId()).isEqualTo(openNow.courseId());
        assertThat(first.courseName()).isEqualTo("Salsa Intermedio");
        assertThat(first.instructorName()).isEqualTo("Ana Perez");
        assertThat(first.scheduledAt()).isEqualTo(openNow.scheduledAt());
    }

    @Test
    void a_session_earlier_in_the_range_whose_window_already_closed_is_listed_with_check_in_closed() {
        when(clock.now()).thenReturn(NOW);
        AssignedSessionRow morning = row("2026-10-10T13:00:00Z");
        when(repository.findAssignedSessions(STUDENT_ID, FROM, TO)).thenReturn(List.of(morning));

        List<AssignedSessionView> sessions = useCase.list(STUDENT_ID, FROM, TO).sessions();

        assertThat(sessions).singleElement().extracting(AssignedSessionView::checkInOpen).isEqualTo(false);
    }

    @Test
    void a_range_of_exactly_92_days_is_accepted() {
        when(clock.now()).thenReturn(NOW);
        Instant to = FROM.plus(Duration.ofDays(92));
        when(repository.findAssignedSessions(STUDENT_ID, FROM, to)).thenReturn(List.of());

        AssignedSessionsView view = useCase.list(STUDENT_ID, FROM, to);

        assertThat(view.to()).isEqualTo(to);
    }

    @Test
    void a_range_of_92_days_and_one_nanosecond_is_rejected_without_reading() {
        Instant to = FROM.plus(Duration.ofDays(92)).plusNanos(1);

        assertThatThrownBy(() -> useCase.list(STUDENT_ID, FROM, to))
            .isInstanceOf(InvalidSessionRangeException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void an_empty_range_is_rejected_without_reading() {
        assertThatThrownBy(() -> useCase.list(STUDENT_ID, FROM, FROM))
            .isInstanceOf(InvalidSessionRangeException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void an_inverted_range_is_rejected_without_reading() {
        assertThatThrownBy(() -> useCase.list(STUDENT_ID, TO, FROM))
            .isInstanceOf(InvalidSessionRangeException.class);

        verifyNoInteractions(repository);
    }
}
