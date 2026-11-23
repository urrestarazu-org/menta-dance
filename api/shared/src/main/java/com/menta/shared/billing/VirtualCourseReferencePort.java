package com.menta.shared.billing;

/**
 * Read contract through which Virtual asks Billing whether a course is still referenced before deleting
 * it (#856).
 *
 * <p>Billing keeps its references to a virtual course by value, with no foreign key into Virtual's
 * schema, so the database cannot stop the delete: this port is the only guard. Any reference counts,
 * historical ones included: a plan that includes the course (whatever its status), or a subscription
 * whose frozen snapshot includes it (whatever its status). The payments of a virtual subscription cite
 * the plan, never the course, so they are covered through those two. The facts never expose Billing
 * entities, plans, or persistence details.</p>
 */
public interface VirtualCourseReferencePort {

    /**
     * Whether any plan or subscription of Billing, active or historical, references the course.
     *
     * @param courseId the opaque Virtual course identifier, as Billing stores it
     * @return {@code true} when deleting the course would leave a dangling reference
     */
    boolean isReferenced(String courseId);
}
