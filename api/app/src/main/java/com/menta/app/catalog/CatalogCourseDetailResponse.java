package com.menta.app.catalog;

import com.menta.virtual.application.dto.VirtualCourseDetailView;
import com.menta.virtual.application.dto.VirtualLessonSummary;
import com.menta.virtual.application.dto.VirtualModuleDetail;
import java.util.List;

/**
 * Wire shape of the public course detail returned by
 * {@code GET /api/v1/catalog/courses/{courseId}} for {@code VIRTUAL} courses
 * (US-VIRTUAL-002 escenario 1, #47). It is one of the two shapes of
 * {@link CatalogCourseDetail}; a {@code PHYSICAL} id answers
 * {@link CatalogPhysicalCourseDetailResponse} instead (#107), so this shape
 * stays virtual-only and never carries a {@code sessions} field.
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
) implements CatalogCourseDetail {

    /**
     * Maps a published virtual course detail view to its public wire shape.
     *
     * <p>The view's image URL is exposed as {@code thumbnailUrl}, and the nested modules, lessons
     * and statistics are converted so that every duration reaches the wire as a formatted string
     * (see {@link Formats}).</p>
     *
     * @param view the published course detail supplied by the virtual module
     * @return the public detail of that course
     */
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

        /**
         * Maps a module of the virtual detail view, converting each of its lessons.
         *
         * @param module the module supplied by the virtual module
         * @return the public module with its lessons in the order the view provides
         */
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

        /**
         * Maps a lesson of the virtual detail view, rendering its duration as a display string.
         *
         * @param lesson the lesson supplied by the virtual module
         * @return the public lesson, with its duration formatted by
         *     {@link Formats#formatLessonDuration(int)}
         */
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

        /**
         * Maps the aggregate counters of a course, rendering the total duration as a display string.
         *
         * @param stats the course statistics supplied by the virtual module
         * @return the public statistics, with the total formatted by
         *     {@link Formats#formatTotalDuration(int)}
         */
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

        /**
         * Formats a lesson duration for display.
         *
         * <p>Negative values are clamped to zero. Under an hour the result is zero-padded
         * {@code "mm:00"}, because the domain has no seconds; from an hour on it is {@code "h:mm"}.</p>
         *
         * @param minutes the lesson length in whole minutes
         * @return the display string, for example {@code "05:00"} or {@code "1:05"}
         */
        public static String formatLessonDuration(int minutes) {
            int safe = Math.max(0, minutes);
            if (safe < 60) {
                return String.format("%02d:%02d", safe, 0);
            }
            int hours = safe / 60;
            int remaining = safe % 60;
            return String.format("%d:%02d", hours, remaining);
        }

        /**
         * Formats the total duration of a course for display.
         *
         * <p>Negative values are clamped to zero. Under an hour the result is {@code "Xm"}; from an
         * hour on it is {@code "Xh Ym"}, with the minutes part never zero-padded.</p>
         *
         * @param minutes the total length in whole minutes
         * @return the display string, for example {@code "45m"} or {@code "2h 30m"}
         */
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
