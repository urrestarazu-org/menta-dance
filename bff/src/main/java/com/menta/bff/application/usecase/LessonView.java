package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.dto.LessonDetail;
import com.menta.bff.application.dto.LessonSummary;
import com.menta.bff.application.dto.Nav;

/**
 * Result of {@link GetLessonViewUseCase}: the upstream lesson endpoint's
 * access decision, rendered by the BFF rather than re-evaluated by it
 * (design decision B).
 * <p>
 * A sealed interface makes the no-stream-leak guarantee structural instead
 * of a template condition someone has to remember: {@link Sample} has no
 * {@code videoId} or {@code streamUrl} field at all, so a denied lesson
 * cannot carry playback data even by accident. Java 21 pattern matching also
 * makes the controller's render switch exhaustive at compile time — the
 * compiler, not review, guarantees both branches exist.
 * </p>
 */
public sealed interface LessonView {

    /**
     * A granted lesson (free or entitled) with a signed playback URL.
     * <p>
     * Every granted lesson reaches this branch, free ones included — a
     * {@code 200} from the lesson endpoint means access was granted, not
     * that a playable URL was supplied with it (see design's "A granted
     * lesson always needs the stream call" note). {@code nav} comes from
     * the lesson endpoint's own {@code navigation} block, never from
     * course-detail ordering.
     * </p>
     *
     * @param course    the course detail fetched for this request
     * @param lesson    the granted lesson detail
     * @param streamUrl signed CDN URL obtained from the stream endpoint
     * @param nav       previous/next pointers from the lesson endpoint's own navigation block
     */
    record Playable(CourseDetail course, LessonDetail lesson, String streamUrl, Nav nav) implements LessonView {
    }

    /**
     * A BFF-assembled sample view rendered on a bare 403 from the lesson
     * endpoint. Built entirely from data already fetched in the course-detail
     * call — the 403 response carries no lesson metadata to parse.
     * <p>
     * Deliberately carries no {@code plansUrl} or any other navigable link:
     * the subscription call-to-action is a message with no destination until
     * issue #177 (BFF plans page) lands. A placeholder link was explicitly
     * rejected — it would repeat the exact debt pattern (#56) that motivated
     * filing #170 in the first place.
     * </p>
     *
     * @param course the course detail fetched for this request
     * @param lesson the lesson summary derived from the course-detail projection
     * @param nav    previous/next pointers derived from course-detail module/lesson order
     */
    record Sample(CourseDetail course, LessonSummary lesson, Nav nav) implements LessonView {
    }
}
