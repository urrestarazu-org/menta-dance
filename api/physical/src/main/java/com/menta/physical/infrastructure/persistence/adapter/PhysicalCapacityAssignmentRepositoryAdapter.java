package com.menta.physical.infrastructure.persistence.adapter;

import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link PhysicalCapacityAssignmentRepository}. */
@Component
public class PhysicalCapacityAssignmentRepositoryAdapter
    implements PhysicalCapacityAssignmentRepository {

    private final PhysicalCapacityAssignmentJpaRepository assignmentRepository;

    public PhysicalCapacityAssignmentRepositoryAdapter(
        PhysicalCapacityAssignmentJpaRepository assignmentRepository
    ) {
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public boolean existsConfirmedAssignment(SessionId sessionId, UUID studentId) {
        return assignmentRepository.existsBySessionIdAndStudentId(sessionId.getValue(), studentId);
    }
}
