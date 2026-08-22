package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.VirtualCourseAdminDetailView;
import com.menta.virtual.application.dto.VirtualLessonAdminSummary;
import com.menta.virtual.application.dto.VirtualModuleAdminDetail;
import java.util.List;

/**
 * Wire shape of the admin course detail returned by
 * {@code GET /api/v1/admin/virtual/courses/{courseId}}
 * (US-VIRTUAL-002 escenario 5, #125). Mirrors
 * {@code api:app}'s {@code CatalogCourseDetailResponse} contract pair but
 * for the admin audience:
 * <ul>
 *   <li>every lesson exposes {@code videoId} (operators need the Bunny.net
 *       reference to wire the editor),</li>
 *   <li>the course carries {@code status} ({@code DRAFT}/{@code PUBLISHED}/
 *       {@code ARCHIVED}) so the UI can render a "visible to visitors?"
 *       badge and an unpublish/publish affordance,</li>
 *   <li>{@code stats.totalDuration} is a formatted string ({@code "Xm"} /
 *       {@code "Xh Ym"}) so the UI is not pinned to making minutes look
 *       presentable.</li>
 * </ul>
 *
 * <p>Carries its own {@link Formats} helper rather than depending on
 * {@code api:app}'s {@code CatalogCourseDetailResponse.Formats}: ADR-0039
 * forbids cross-module API-surface coupling, and the formatter is part of
 * the wire contract — so duplicating it here is the lesser evil. The
 * duplication is auditable: the rules below come from {@code
 * CatalogCourseDetailResponse.Formats} (#47), and any future PR that
 * widens the rules (e.g. adds second granularity) MUST update both
 * copies or risk presenting inconsistent durations to admin operators vs.
 * visitors.</p>
 */
public record VirtualCourseAdminDetailResponse(
    String courseId,
    String title,
    String description,
    String thumbnailUrl,
    String category,
    String level,
    boolean isPremium,
    String status,
    List<AdminModuleDetail> modules,
    AdminStats stats
) {

    public static VirtualCourseAdminDetailResponse fromVirtualAdmin(VirtualCourseAdminDetailView view) {
        return new VirtualCourseAdminDetailResponse(
            view.courseId(),
            view.title(),
            view.description(),
            view.imageUrl(),
            view.category(),
            view.level(),
            view.isPremium(),
            view.status().name(),
            view.modules().stream().map(AdminModuleDetail::from).toList(),
            AdminStats.of(view.stats().moduleCount(), view.stats().lessonCount(), view.stats().totalDurationMinutes())
        );
    }

    public record AdminModuleDetail(
        String moduleId,
        String title,
        int order,
        List<AdminLessonDetail> lessons
    ) {

        public static AdminModuleDetail from(VirtualModuleAdminDetail module) {
            return new AdminModuleDetail(
                module.moduleId(),
                module.title(),
                module.order(),
                module.lessons().stream().map(AdminLessonDetail::from).toList()
            );
        }
    }

    public record AdminLessonDetail(
        String lessonId,
        String title,
        int durationMinutes,
        boolean isFree,
        int order,
        String videoId
    ) {

        public static AdminLessonDetail from(VirtualLessonAdminSummary lesson) {
            return new AdminLessonDetail(
                lesson.lessonId(),
                lesson.title(),
                lesson.durationMinutes(),
                lesson.isFree(),
                lesson.order(),
                lesson.videoId()
            );
        }
    }

    public record AdminStats(
        int moduleCount,
        int lessonCount,
        String totalDuration
    ) {

        public static AdminStats of(int moduleCount, int lessonCount, int totalDurationMinutes) {
            return new AdminStats(
                moduleCount,
                lessonCount,
                Formats.formatTotalDuration(totalDurationMinutes)
            );
        }
    }

    /**
     * Duration formatting rules for {@link AdminLessonDetail} and
     * {@link AdminStats}. Mirrors {@code api:app}'s
     * {@code CatalogCourseDetailResponse.Formats} (#47) — see the class
     * Javadoc above for the duplication rationale.
     *
     * <ul>
     *   <li>Totals — {@code "Xm"} under 60 minutes; {@code "Xh Ym"} once
     *       over. Examples: {@code 45→"45m"}, {@code 150→"2h 30m"},
     *       {@code 65→"1h 5m"}. Negative or zero inputs are clamped to
     *       {@code 0} before formatting.</li>
     * </ul>
     *
     * <p>The admin view does NOT format per-lesson duration to {@code
     * "mm:ss"}: an operator viewing a course detail values the raw minute
     * count for editing, where the public detail view pads seconds for the
     * visitor display. This is the one admin-side divergence from the
     * public formatter.</p>
     */
    public static final class Formats {

        private Formats() {
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
