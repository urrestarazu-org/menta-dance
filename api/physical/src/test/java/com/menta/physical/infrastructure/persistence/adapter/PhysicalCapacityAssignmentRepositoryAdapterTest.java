package com.menta.physical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalCapacityAssignmentRepositoryAdapterTest {

    private final PhysicalCapacityAssignmentJpaRepository assignmentRepository =
        mock(PhysicalCapacityAssignmentJpaRepository.class);
    private final PhysicalCapacityAssignmentRepositoryAdapter adapter =
        new PhysicalCapacityAssignmentRepositoryAdapter(assignmentRepository);

    @Test
    void delegates_to_the_repository_when_an_assignment_exists() {
        SessionId sessionId = SessionId.generate();
        UUID studentId = UUID.randomUUID();
        when(assignmentRepository.existsBySessionIdAndStudentId(sessionId.getValue(), studentId))
            .thenReturn(true);

        assertThat(adapter.existsConfirmedAssignment(sessionId, studentId)).isTrue();
    }

    @Test
    void delegates_to_the_repository_when_no_assignment_exists() {
        SessionId sessionId = SessionId.generate();
        UUID studentId = UUID.randomUUID();
        when(assignmentRepository.existsBySessionIdAndStudentId(sessionId.getValue(), studentId))
            .thenReturn(false);

        assertThat(adapter.existsConfirmedAssignment(sessionId, studentId)).isFalse();
    }
}
