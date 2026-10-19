package com.menta.physical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.physical.domain.model.Attendance;
import com.menta.physical.domain.model.AttendanceKind;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.persistence.entity.AttendanceJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.AttendanceJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttendanceRepositoryAdapterTest {

    private final AttendanceJpaRepository attendanceRepository =
        mock(AttendanceJpaRepository.class);
    private final AttendanceRepositoryAdapter adapter =
        new AttendanceRepositoryAdapter(attendanceRepository);

    @Test
    void find_by_session_and_user_maps_the_entity_to_domain_when_present() {
        SessionId sessionId = SessionId.generate();
        UUID userId = UUID.randomUUID();
        AttendanceJpaEntity entity = new AttendanceJpaEntity(
            UUID.randomUUID(), sessionId.getValue(), userId, Instant.now(), "reader-01", "QR"
        );
        when(attendanceRepository.findBySessionIdAndUserId(sessionId.getValue(), userId))
            .thenReturn(Optional.of(entity));

        Optional<Attendance> result = adapter.findBySessionIdAndUserId(sessionId, userId);

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(userId);
    }

    @Test
    void find_by_session_and_user_returns_empty_when_absent() {
        SessionId sessionId = SessionId.generate();
        UUID userId = UUID.randomUUID();
        when(attendanceRepository.findBySessionIdAndUserId(sessionId.getValue(), userId))
            .thenReturn(Optional.empty());

        assertThat(adapter.findBySessionIdAndUserId(sessionId, userId)).isEmpty();
    }

    @Test
    void save_persists_and_maps_the_saved_entity_back_to_domain() {
        Attendance attendance = Attendance.record(
            SessionId.generate(), UUID.randomUUID(), Instant.now(), "reader-01", AttendanceKind.QR
        );
        when(attendanceRepository.save(any(AttendanceJpaEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Attendance saved = adapter.save(attendance);

        assertThat(saved.getId()).isEqualTo(attendance.getId());
        assertThat(saved.getSessionId()).isEqualTo(attendance.getSessionId());
        assertThat(saved.getUserId()).isEqualTo(attendance.getUserId());
    }
}
