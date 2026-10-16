package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.AssignedSessionRow;
import com.menta.physical.application.dto.AssignedSessionView;
import com.menta.physical.application.dto.AssignedSessionsView;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.domain.model.CheckInWindow;
import com.menta.shared.physical.AssignmentType;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListMyPhysicalSessionsUseCaseImplTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final CheckInWindow WINDOW = new CheckInWindow(Duration.ofMinutes(30), Duration.ofHours(2));
    private static final UUID STUDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    // 2026-10-10 15:00 in Buenos Aires.
    private static final Instant NOW = Instant.parse("2026-10-10T18:00:00Z");

    private final PhysicalCapacityAssignmentRepository repository = mock(PhysicalCapacityAssignmentRepository.class);
    private final Clock clock = mock(Clock.class);

    private ListMyPhysicalSessionsUseCaseImpl useCase(int windowDays) {
        when(clock.now()).thenReturn(NOW);
        return new ListMyPhysicalSessionsUseCaseImpl(repository, clock, BUENOS_AIRES, windowDays, WINDOW);
    }

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
        AssignedSessionRow monthly = row("2026-10-11T22:00:00Z", AssignmentType.MONTHLY);
        AssignedSessionRow individual = row("2026-10-12T22:00:00Z", AssignmentType.INDIVIDUAL);
        when(repository.findAssignedSessions(STUDENT_ID, Instant.parse("2026-10-10T03:00:00Z"),
            Instant.parse("2026-12-09T03:00:00Z"))).thenReturn(List.of(monthly, individual));

        List<AssignedSessionView> sessions = useCase(60).list(STUDENT_ID).sessions();

        assertThat(sessions).extracting(AssignedSessionView::assignmentType)
            .containsExactly(AssignmentType.MONTHLY, AssignmentType.INDIVIDUAL);
    }

    @Test
    void the_range_starts_at_the_beginning_of_today_in_the_academy_zone_and_spans_the_configured_days() {
        when(repository.findAssignedSessions(STUDENT_ID, Instant.parse("2026-10-10T03:00:00Z"),
            Instant.parse("2026-12-09T03:00:00Z"))).thenReturn(List.of());

        AssignedSessionsView view = useCase(60).list(STUDENT_ID);

        assertThat(view.from()).isEqualTo(Instant.parse("2026-10-10T03:00:00Z"));
        assertThat(view.to()).isEqualTo(Instant.parse("2026-12-09T03:00:00Z"));
        assertThat(view.sessions()).isEmpty();
        verify(repository).findAssignedSessions(
            STUDENT_ID, Instant.parse("2026-10-10T03:00:00Z"), Instant.parse("2026-12-09T03:00:00Z")
        );
    }

    @Test
    void maps_rows_in_repository_order_and_flags_only_the_sessions_whose_window_is_open_now() {
        AssignedSessionRow openNow = row("2026-10-10T18:20:00Z");
        AssignedSessionRow tonightTooEarly = row("2026-10-10T22:00:00Z");
        AssignedSessionRow tomorrow = row("2026-10-11T22:00:00Z");
        when(repository.findAssignedSessions(STUDENT_ID, Instant.parse("2026-10-10T03:00:00Z"),
            Instant.parse("2026-12-09T03:00:00Z"))).thenReturn(List.of(openNow, tonightTooEarly, tomorrow));

        List<AssignedSessionView> sessions = useCase(60).list(STUDENT_ID).sessions();

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
    void a_session_earlier_today_whose_window_already_closed_is_listed_with_check_in_closed() {
        AssignedSessionRow morning = row("2026-10-10T13:00:00Z");
        when(repository.findAssignedSessions(STUDENT_ID, Instant.parse("2026-10-10T03:00:00Z"),
            Instant.parse("2026-12-09T03:00:00Z"))).thenReturn(List.of(morning));

        List<AssignedSessionView> sessions = useCase(60).list(STUDENT_ID).sessions();

        assertThat(sessions).singleElement().extracting(AssignedSessionView::checkInOpen).isEqualTo(false);
    }

    @Test
    void rejects_a_non_positive_window() {
        assertThatThrownBy(() -> new ListMyPhysicalSessionsUseCaseImpl(repository, clock, BUENOS_AIRES, 0, WINDOW))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
