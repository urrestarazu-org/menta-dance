package com.menta.physical.infrastructure.persistence.repository;

import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Schema/seeding repository — see {@link PhysicalCapacityAssignmentJpaEntity}'s Javadoc. */
public interface PhysicalCapacityAssignmentJpaRepository
    extends JpaRepository<PhysicalCapacityAssignmentJpaEntity, UUID> {

    /**
     * US-PHYSICAL-001 escenarios 1, 3, 7: a row here IS a confirmed
     * assignment — no status column to filter on (see {@link
     * PhysicalCapacityAssignmentJpaEntity}'s Javadoc and {@code
     * V7__physical_courses.sql}).
     *
     * <p>Derived query name, deliberately NOT a native {@code SELECT
     * EXISTS(...)} (unlike {@code PhysicalSessionJpaRepository
     * #existsFutureAssignedSession}): a native boolean-returning {@code
     * EXISTS(...)}/comparison expression comes back from MySQL as
     * {@code BIGINT}, which MySQL Connector/J maps to {@code Long}, not
     * {@code Boolean} — Spring Data's proxy then throws {@code
     * ClassCastException} coercing it to this method's {@code boolean}
     * return type. Confirmed against real MySQL via Testcontainers in
     * {@code PhysicalCheckInIntegrationTest}; a plain repository-mock unit
     * test never exercises the JDBC layer and cannot catch this. Spring
     * Data's own JPQL-derived exists-query has no such native-type
     * coercion step, so it is the safe shape here.</p>
     */
    boolean existsBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    /**
     * Plain (non-locking) live count of the rows assigned to a session.
     *
     * <p><b>Not for the capacity decision.</b> This is a consistent read:
     * under REPEATABLE READ it answers from the transaction's MVCC
     * snapshot, which may predate a peer's commit. It is fine for
     * read-only reporting and for tests asserting the final state, and it
     * is what callers outside the claim path should use. The claim path
     * must use {@link #countBySessionIdForUpdate} instead — see issue
     * #216.</p>
     */
    long countBySessionId(UUID sessionId);

    /**
     * Step 2 of the capacity-assignment claim (issue #216): the count that
     * the capacity decision is actually made on, as a LOCKING read.
     *
     * <p>Three properties, all of them load-bearing:</p>
     * <ul>
     *   <li>A locking read reads the latest committed version of every row
     *       it touches, bypassing the transaction's MVCC snapshot. That is
     *       why this count cannot be stale, whatever the transaction read
     *       before it.</li>
     *   <li>With zero matching rows it still takes a gap lock on
     *       {@code idx_physical_assignments_session} for this
     *       {@code session_id}, so a concurrent claim for the same session
     *       blocks here instead of inserting underneath us. The row lock on
     *       {@code physical_sessions} alone does not do this — it is only
     *       taken first
     *       ({@link PhysicalSessionJpaRepository#lockCapacityForUpdate})
     *       to give every claimant the same lock order.</li>
     *   <li>The {@code WHERE session_id = ?} predicate is served by
     *       {@code idx_physical_assignments_session} (V7), so the lock is
     *       scoped to one session's index range — not the whole table.</li>
     * </ul>
     *
     * <p>Measured on MySQL 8.0 with two raw connections: this is the only
     * shape that holds with AND without an earlier plain read in the same
     * transaction. A plain {@code COUNT(*)} taken while merely holding the
     * session row lock ({@link #countBySessionId}) oversells 2/1 as soon as
     * any non-locking read has already fixed the snapshot.</p>
     */
    @Query(
        value = "SELECT COUNT(*) FROM physical_capacity_assignments a "
            + "WHERE a.session_id = :sessionId FOR UPDATE",
        nativeQuery = true
    )
    long countBySessionIdForUpdate(@Param("sessionId") UUID sessionId);

    /**
     * Monthly attendance history (#39, US-PHYSICAL-002, design C1). Drives from this table (the
     * denominator, D1), joins {@code physical_sessions} for the date filter and
     * {@code physical_courses} for display fields AND the instructor-ownership predicate, and
     * {@code LEFT JOIN}s {@code physical_attendances} for the live {@code ATTENDED}/{@code
     * ABSENT} derivation (D5). Because the list and the aggregate come from the same row set,
     * they cannot disagree.
     *
     * <p>{@code :professorId IS NULL OR c.professorId = :professorId} is the same nullable-
     * parameter idiom {@link PhysicalCourseJpaRepository#findByStatusAfterCursor} already uses —
     * the adapter is the only caller and it never lets the unrestricted value cross the
     * application boundary as a literal {@code null} on the out-port itself (two port methods,
     * design C1).</p>
     *
     * <p>Explicit entity joins, not association navigation: these entities carry plain
     * {@code UUID} columns and no {@code @ManyToOne} (see each entity's Javadoc).</p>
     */
    @Query(
        "SELECT s.id AS sessionId, s.scheduledAt AS scheduledAt, c.title AS courseName, "
            + "c.professorName AS instructorName, a.recordedAt AS recordedAt "
            + "FROM PhysicalCapacityAssignmentJpaEntity asg "
            + "JOIN PhysicalSessionJpaEntity s ON s.id = asg.sessionId "
            + "JOIN PhysicalCourseJpaEntity c ON c.id = s.courseId "
            + "LEFT JOIN AttendanceJpaEntity a ON a.sessionId = s.id AND a.userId = asg.studentId "
            + "WHERE asg.studentId = :studentId "
            + "AND s.scheduledAt >= :monthStart AND s.scheduledAt < :monthNext "
            + "AND (:professorId IS NULL OR c.professorId = :professorId) "
            + "ORDER BY s.scheduledAt ASC, s.id ASC"
    )
    List<MonthlyAttendanceRowProjection> findMonthlyAttendance(
        @Param("studentId") UUID studentId, @Param("monthStart") Instant monthStart,
        @Param("monthNext") Instant monthNext, @Param("professorId") UUID professorId
    );
}
