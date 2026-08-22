package com.menta.app.catalog;

import com.menta.virtual.application.dto.VirtualCourseDetailView;
import com.menta.virtual.application.dto.VirtualLessonSummary;
import com.menta.virtual.application.dto.VirtualModuleDetail;
import java.util.List;

/**
 * Wire shape of the public course detail returned by
 * {@code GET /api/v1/catalog/courses/{courseId}} for {@code VIRTUAL} courses
 * (US-VIRTUAL-002 escenario 1, #47).
 *
 * <p>For this MVP the endpoint resolves <strong>only against Virtual's detail
 * port</strong> — physical detail is out of scope and will return the
 * standard 404 problem. That is the explicit trade-off made in #47's scope:
 * the composition method {@code CatalogCompositionService.getCourseDetail}
 * delegates to {@code virtualPort.findPublishedDetailById} alone and lets
 * physical fall through to {@code CourseNotFoundException}. Once the physical
 * side adds a comparable detail read a future change symmetrically adds a
 * {@code fromPhysical} factory here — until then do not add one or the
 * composition will silently misclassify physical detail as virtual.</p>
 *
 * <p>Duration strings are NOT domain primitives: {@code VirtualLesson.durationMinutes}
 * is the integer-minute domain value; this record turns it into a UI string.
 * See {@link Formats} for the chosen conventions — recorded inline so the
 * contract is auditable against the US examples.</p>
 */
public record CatalogCourseDetailResponse(
    String courseId,
    String title,
    String description,
    String thumbnailUrl,
    String category,
    String level,
    boolean isPremium,
    List<PublicModuleDetail> modules,
    PublicStats stats
) {

    public static CatalogCourseDetailResponse fromVirtual(VirtualCourseDetailView view) {
        return new CatalogCourseDetailResponse(
            view.courseId(),
            view.title(),
            view.description(),
            view.imageUrl(),
            view.category(),
            view.level(),
            view.isPremium(),
            view.modules().stream().map(PublicModuleDetail::from).toList(),
            PublicStats.of(view.stats())
        );
    }

    public record PublicModuleDetail(
        String moduleId,
        String title,
        int order,
        List<PublicLessonDetail> lessons
    ) {

        public static PublicModuleDetail from(VirtualModuleDetail module) {
            return new PublicModuleDetail(
                module.moduleId(),
                module.title(),
                module.order(),
                module.lessons().stream().map(PublicLessonDetail::from).toList()
            );
        }
    }

    public record PublicLessonDetail(
        String lessonId,
        String title,
        String duration,
        boolean isFree,
        int order
    ) {

        public static PublicLessonDetail from(VirtualLessonSummary lesson) {
            return new PublicLessonDetail(
                lesson.lessonId(),
                lesson.title(),
                Formats.formatLessonDuration(lesson.durationMinutes()),
                lesson.isFree(),
                lesson.order()
            );
        }
    }

    public record PublicStats(
        int moduleCount,
        int lessonCount,
        String totalDuration
    ) {

        public static PublicStats of(com.menta.virtual.application.dto.VirtualCourseStats stats) {
            return new PublicStats(
                stats.moduleCount(),
                stats.lessonCount(),
                Formats.formatTotalDuration(stats.totalDurationMinutes())
            );
        }
    }

    /**
     * Duration formatting rules used by {@link PublicLessonDetail} and
     * {@link PublicStats} (#47).
     *
     * <p>Decision: the domain carries integer minutes only, so every duration
     * string is derived — there are no seconds to render. US-VIRTUAL-002's
     * example {@code "10:30"} would suggest half-minute granularity; we
     * cannot synthesize it from integer minutes, so a 10-minute lesson
     * renders as {@code "10:00"} (zero seconds). This trade-off is explicit
     * and documented here so a future PR that adds second granularity (e.g.
     * tracking video progress) widens these formatters coherently.</p>
     *
     * <ul>
     *   <li>Lessons — {@code "mm:ss"} (zero-padded) under 60 minutes;
     *       {@code "h:mm"} once the lesson crosses an hour. Examples:
     *       {@code 5→"05:00"}, {@code 10→"10:00"}, {@code 65→"1:05"},
     *       {@code 90→"1:30"}.</li>
     *   <li>Totals — {@code "Xm"} under 60 minutes; {@code "Xh Ym"} once
     *       over. Examples: {@code 45→"45m"}, {@code 150→"2h 30m"},
     *       {@code 65→"1h 5m"}.</li>
     * </ul>
     *
     * <p>Negative or zero inputs are clamped to {@code 0} before formatting
     * so nobody ever sees {@code "-1:-1"} on the wire.</p>
     */
    public static final class Formats {

        private Formats() {
        }

        public static String formatLessonDuration(int minutes) {
            int safe = Math.max(0, minutes);
            if (safe < 60) {
                return String.format("%02d:%02d", safe, 0);
            }
            int hours = safe / 60;
            int remaining = safe % 60;
            return String.format("%d:%02d", hours, remaining);
        }

        public static String formatTotalDuration(int minutes) {
            int safe = Math.max(0, minutes);
            if (safe < 60) {
                return safe + "m";
            }
            int hours = safe / 60;
            int remaining = safe % 60;
            return hours + "h " + remaining + "m";
        }
    }
}
