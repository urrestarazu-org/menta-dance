package com.menta.virtual.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of a student's course-lesson progress joined with curriculum ordering (US-VIRTUAL-005,
 * Slice 3). Declared here — not in {@code infrastructure} — because it is the return shape of the
 * {@link LessonProgressRepository} out port; the ArchUnit rule {@code
 * application_should_not_depend_on_infrastructure} forbids the port from referencing an
 * infrastructure-owned type. The JPA repository (infrastructure) implements it via Spring Data's
 * interface-projection mechanism, same pattern as an ordinary out-port contract.
 *
 * <p>Rows arrive pre-ordered by {@code ORDER BY position_updated_at DESC, module.display_order
 * ASC, lesson.display_order ASC, lesson_id ASC} (design.md) — {@link CourseProgressAssembler}
 * never re-sorts or compares timestamps itself.</p>
 */
public interface CourseProgressRowProjection {

    /** Returns the id of the lesson the row describes. */
    UUID getLessonId();

    /** Returns the id of the module that contains the lesson. */
    UUID getModuleId();

    /** Returns the last saved playback position, in seconds. */
    int getPositionSeconds();

    /** Returns whether the student completed the lesson. */
    boolean isCompleted();

    /**
     * Returns when the position was last saved; it is {@code null} for a row created only by
     * completing a lesson that was never played.
     */
    Instant getPositionUpdatedAt();

    /** Returns the display order of the lesson inside its module. */
    int getLessonOrder();

    /** Returns the display order of the module inside its course. */
    int getModuleOrder();
}
