package com.menta.bff.infrastructure.adapter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menta.bff.application.dto.CreateVirtualCourseCommand;
import com.menta.bff.application.dto.CreateVirtualLessonCommand;
import com.menta.bff.application.dto.CreateVirtualModuleCommand;
import com.menta.bff.application.dto.ManagedVirtualCourse;
import com.menta.bff.application.dto.ManagedVirtualLesson;
import com.menta.bff.application.dto.ManagedVirtualModule;
import com.menta.bff.application.dto.VirtualCourseChanges;
import com.menta.bff.application.dto.VirtualCourseDetail;
import com.menta.bff.application.dto.VirtualCourseDetail.VirtualCourseStats;
import com.menta.bff.application.dto.VirtualCourseDetail.VirtualLessonDetail;
import com.menta.bff.application.dto.VirtualCourseDetail.VirtualModuleDetail;
import com.menta.bff.application.dto.VirtualCourseLevel;
import com.menta.bff.application.dto.VirtualCourseStatus;
import com.menta.bff.application.dto.VirtualLessonChanges;
import com.menta.bff.application.dto.VirtualModuleChanges;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient adapter for the management of virtual courses (#367): the calls of the Virtual API under
 * {@code /api/v1/admin/virtual/courses}, and the modules and lessons of a course under {@code /modules} and
 * {@code /lessons}, as {@code virtual-v1.yaml} describes them.
 *
 * <p>The token only ever travels in the {@code Authorization} header; it, identifiers, titles and response bodies
 * never reach a log or an exception message. A refusal with one of the expected codes (400, 403, 404 or 409)
 * becomes a {@link VirtualCourseManagementApiClient.VirtualCourseManagementException}; every other outcome
 * becomes a {@link VirtualCourseManagementApiClient.ServiceUnavailableException}.
 * </p>
 *
 * <p>The only fields read from an error body are the {@code code} and, for {@code COURSE_NOT_PUBLISHABLE}, the
 * {@code detail}: the one message the API words in Spanish for the screens, bounded to
 * {@value #MAX_PUBLISH_DETAIL} characters. Requests send only the fields that are present, so a partial update
 * never overwrites what it does not name. Uses an explicit constructor because {@code @Qualifier} cannot be
 * carried by Lombok's {@code @RequiredArgsConstructor}.
 * </p>
 */
@Slf4j
@Component
public class VirtualCourseManagementApiAdapter implements VirtualCourseManagementApiClient {

    private static final String COURSES_ENDPOINT = "/api/v1/admin/virtual/courses";
    private static final String COURSE_ENDPOINT = COURSES_ENDPOINT + "/{courseId}";
    private static final String PUBLISH_ENDPOINT = COURSE_ENDPOINT + "/publish";
    private static final String UNPUBLISH_ENDPOINT = COURSE_ENDPOINT + "/unpublish";
    private static final String MODULES_ENDPOINT = COURSE_ENDPOINT + "/modules";
    private static final String REORDER_ENDPOINT = MODULES_ENDPOINT + "/reorder";
    private static final String MODULE_ENDPOINT = "/api/v1/admin/virtual/modules/{moduleId}";
    private static final String LESSONS_ENDPOINT = MODULE_ENDPOINT + "/lessons";
    private static final String LESSON_ENDPOINT = "/api/v1/admin/virtual/lessons/{lessonId}";
    private static final Set<Integer> REFUSAL_STATUSES = Set.of(400, 403, 404, 409);
    static final int MAX_PUBLISH_DETAIL = 300;

    private final WebClient webClient;
    private final VirtualApiProperties properties;

    public VirtualCourseManagementApiAdapter(
            @Qualifier("virtualApiWebClient") WebClient webClient, VirtualApiProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public List<ManagedVirtualCourse> listManagedCourses(String accessToken) {
        return toCourses(call("list managed courses", webClient.get().uri(COURSES_ENDPOINT), accessToken, 200,
                CoursesWire.class));
    }

    @Override
    public VirtualCourseDetail getManagedCourse(String accessToken, String courseId) {
        return toDetail(call("get managed course", webClient.get().uri(COURSE_ENDPOINT, courseId), accessToken, 200,
                DetailWire.class));
    }

    @Override
    public ManagedVirtualCourse createCourse(String accessToken, CreateVirtualCourseCommand command) {
        CourseWriteWire body = new CourseWriteWire(command.title(), command.shortDescription(), command.description(),
                command.imageUrl(), command.category(), command.level().name(), null, command.professorId());
        return toCourse(call("create course", webClient.post().uri(COURSES_ENDPOINT).bodyValue(body), accessToken,
                201, CourseWire.class));
    }

    @Override
    public ManagedVirtualCourse updateCourse(String accessToken, String courseId, VirtualCourseChanges changes) {
        CourseWriteWire body = new CourseWriteWire(changes.title(), changes.shortDescription(), changes.description(),
                changes.imageUrl(), changes.category(), changes.level() == null ? null : changes.level().name(),
                changes.premium(), null);
        return toCourse(call("update course", webClient.put().uri(COURSE_ENDPOINT, courseId).bodyValue(body),
                accessToken, 200, CourseWire.class));
    }

    @Override
    public ManagedVirtualCourse publishCourse(String accessToken, String courseId) {
        return toCourse(call("publish course", webClient.put().uri(PUBLISH_ENDPOINT, courseId), accessToken, 200,
                CourseWire.class));
    }

    @Override
    public ManagedVirtualCourse unpublishCourse(String accessToken, String courseId) {
        return toCourse(call("unpublish course", webClient.put().uri(UNPUBLISH_ENDPOINT, courseId), accessToken,
                200, CourseWire.class));
    }

    @Override
    public void deleteCourse(String accessToken, String courseId) {
        call("delete course", webClient.delete().uri(COURSE_ENDPOINT, courseId), accessToken, 204, Void.class);
    }

    @Override
    public ManagedVirtualModule createModule(String accessToken, String courseId, CreateVirtualModuleCommand command) {
        ModuleWriteWire body = new ModuleWriteWire(command.title(), command.preview(), command.order());
        return toManagedModule(call("create module", webClient.post().uri(MODULES_ENDPOINT, courseId).bodyValue(body),
                accessToken, 201, ManagedModuleWire.class));
    }

    @Override
    public ManagedVirtualModule updateModule(String accessToken, String moduleId, VirtualModuleChanges changes) {
        ModuleWriteWire body = new ModuleWriteWire(changes.title(), changes.preview(), changes.order());
        return toManagedModule(call("update module", webClient.put().uri(MODULE_ENDPOINT, moduleId).bodyValue(body),
                accessToken, 200, ManagedModuleWire.class));
    }

    @Override
    public List<ManagedVirtualModule> reorderModules(String accessToken, String courseId, List<String> moduleIds) {
        ModulesWire wire = call("reorder modules",
                webClient.put().uri(REORDER_ENDPOINT, courseId).bodyValue(new ReorderWire(moduleIds)), accessToken,
                200, ModulesWire.class);
        if (wire.modules() == null) {
            throw unavailable("modules list is incomplete");
        }
        return wire.modules().stream().map(VirtualCourseManagementApiAdapter::toManagedModule).toList();
    }

    @Override
    public ManagedVirtualLesson createLesson(String accessToken, String moduleId, CreateVirtualLessonCommand command) {
        LessonWriteWire body = new LessonWriteWire(command.title(), command.description(), command.videoId(),
                command.durationMinutes(), command.free(), command.order());
        return toManagedLesson(call("create lesson", webClient.post().uri(LESSONS_ENDPOINT, moduleId).bodyValue(body),
                accessToken, 201, ManagedLessonWire.class));
    }

    @Override
    public ManagedVirtualLesson updateLesson(String accessToken, String lessonId, VirtualLessonChanges changes) {
        LessonWriteWire body = new LessonWriteWire(changes.title(), changes.description(), changes.videoId(),
                changes.durationMinutes(), changes.free(), changes.order());
        return toManagedLesson(call("update lesson", webClient.put().uri(LESSON_ENDPOINT, lessonId).bodyValue(body),
                accessToken, 200, ManagedLessonWire.class));
    }

    @Override
    public void deleteLesson(String accessToken, String lessonId) {
        call("delete lesson", webClient.delete().uri(LESSON_ENDPOINT, lessonId), accessToken, 204, Void.class);
    }

    /**
     * Sends the request with the bearer token and reads the answer: {@code okStatus} is the success, the expected
     * refusals are typed, everything else is unavailable. With {@code Void} as wire type the body is not read and
     * the result is {@code null}; with any other type a missing body is unavailable.
     */
    private <W> W call(String operation, WebClient.RequestHeadersSpec<?> request, String accessToken, int okStatus,
            Class<W> wireType) {
        try {
            W wire = request
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .exchangeToMono(response -> read(response, okStatus, wireType))
                    .timeout(properties.getTimeout())
                    .block();
            if (wire == null && wireType != Void.class) {
                throw unavailable(operation + " answered without a body");
            }
            return wire;
        } catch (VirtualCourseManagementException | ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Class name only: nothing from the request (headers, body) or the response may reach a log.
            log.warn("Virtual API {} call failed: {}", operation, e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Virtual API unavailable", e);
        }
    }

    /** Only {@code okStatus} is success; a refusal needs its code, any other error body is released unread. */
    private static <W> Mono<W> read(ClientResponse response, int okStatus, Class<W> wireType) {
        int status = response.statusCode().value();
        if (status == okStatus) {
            return response.bodyToMono(wireType);
        }
        if (REFUSAL_STATUSES.contains(status)) {
            return response.bodyToMono(ProblemWire.class)
                    .flatMap(problem -> Mono.<W>error(refusal(status, problem)))
                    .switchIfEmpty(Mono.error(unavailable("answered " + status + " without a body")));
        }
        return response.releaseBody().then(Mono.error(unavailable("answered " + status)));
    }

    private static RuntimeException refusal(int status, ProblemWire problem) {
        for (VirtualCourseManagementException.Code code : VirtualCourseManagementException.Code.values()) {
            if (code.name().equals(problem.code())) {
                return code == VirtualCourseManagementException.Code.COURSE_NOT_PUBLISHABLE
                        ? new VirtualCourseManagementException(code, boundedDetail(problem.detail()))
                        : new VirtualCourseManagementException(code);
            }
        }
        return unavailable("answered " + status);
    }

    /** The API's explanation, trimmed and cut at {@value #MAX_PUBLISH_DETAIL} characters; {@code null} if empty. */
    private static String boundedDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        String text = detail.strip();
        return text.codePointCount(0, text.length()) <= MAX_PUBLISH_DETAIL
                ? text : text.substring(0, text.offsetByCodePoints(0, MAX_PUBLISH_DETAIL));
    }

    private static List<ManagedVirtualCourse> toCourses(CoursesWire wire) {
        if (wire.courses() == null) {
            throw unavailable("courses list is incomplete");
        }
        return wire.courses().stream().map(VirtualCourseManagementApiAdapter::toCourse).toList();
    }

    private static ManagedVirtualCourse toCourse(CourseWire item) {
        if (item == null || item.courseId() == null || item.title() == null || item.shortDescription() == null
                || item.description() == null || item.professorId() == null || item.imageUrl() == null
                || item.category() == null || item.level() == null || item.premium() == null
                || item.status() == null) {
            throw unavailable("course is incomplete");
        }
        return new ManagedVirtualCourse(item.courseId(), item.title(), item.shortDescription(), item.description(),
                item.professorId(), item.imageUrl(), item.category(),
                ApiEnums.parse(VirtualCourseLevel.class, item.level()), item.premium(),
                ApiEnums.parse(VirtualCourseStatus.class, item.status()));
    }

    private static VirtualCourseDetail toDetail(DetailWire item) {
        if (item.courseId() == null || item.title() == null || item.shortDescription() == null
                || item.description() == null || item.professorId() == null || item.thumbnailUrl() == null
                || item.category() == null || item.level() == null || item.isPremium() == null
                || item.status() == null || item.modules() == null || item.stats() == null
                || item.stats().moduleCount() == null || item.stats().lessonCount() == null
                || item.stats().totalDuration() == null) {
            throw unavailable("course detail is incomplete");
        }
        return new VirtualCourseDetail(item.courseId(), item.title(), item.shortDescription(), item.description(),
                item.professorId(), item.thumbnailUrl(), item.category(),
                ApiEnums.parse(VirtualCourseLevel.class, item.level()), item.isPremium(),
                ApiEnums.parse(VirtualCourseStatus.class, item.status()),
                item.modules().stream().map(VirtualCourseManagementApiAdapter::toModule).toList(),
                new VirtualCourseStats(item.stats().moduleCount(), item.stats().lessonCount(),
                        item.stats().totalDuration()));
    }

    private static VirtualModuleDetail toModule(ModuleWire item) {
        if (item == null || item.moduleId() == null || item.title() == null || item.preview() == null
                || item.order() == null || item.lessons() == null) {
            throw unavailable("module is incomplete");
        }
        return new VirtualModuleDetail(item.moduleId(), item.title(), item.preview(), item.order(),
                item.lessons().stream().map(VirtualCourseManagementApiAdapter::toLesson).toList());
    }

    private static VirtualLessonDetail toLesson(LessonWire item) {
        if (item == null || item.lessonId() == null || item.title() == null || item.description() == null
                || item.durationMinutes() == null || item.isFree() == null || item.order() == null) {
            throw unavailable("lesson is incomplete");
        }
        return new VirtualLessonDetail(item.lessonId(), item.title(), item.description(), item.durationMinutes(),
                item.isFree(), item.order(), item.videoId());
    }

    private static ManagedVirtualModule toManagedModule(ManagedModuleWire item) {
        if (item == null || item.moduleId() == null || item.courseId() == null || item.title() == null
                || item.preview() == null || item.order() == null) {
            throw unavailable("managed module is incomplete");
        }
        return new ManagedVirtualModule(item.moduleId(), item.courseId(), item.title(), item.preview(), item.order());
    }

    private static ManagedVirtualLesson toManagedLesson(ManagedLessonWire item) {
        if (item.lessonId() == null || item.moduleId() == null || item.courseId() == null || item.title() == null
                || item.description() == null || item.durationMinutes() == null || item.free() == null
                || item.order() == null) {
            throw unavailable("managed lesson is incomplete");
        }
        return new ManagedVirtualLesson(item.lessonId(), item.moduleId(), item.courseId(), item.title(),
                item.description(), item.videoId(), item.durationMinutes(), item.free(), item.order());
    }

    private static ServiceUnavailableException unavailable(String reason) {
        return new ServiceUnavailableException("Virtual API unavailable: " + reason);
    }

    /** Response of the courses list. */
    private record CoursesWire(List<CourseWire> courses) {
    }

    /** {@code VirtualCourseManagementResponse}. */
    private record CourseWire(
            String courseId, String title, String shortDescription, String description, String professorId,
            String imageUrl, String category, String level, Boolean premium, String status) {
    }

    /** {@code VirtualCourseAdminDetailResponse}. */
    private record DetailWire(
            String courseId, String title, String shortDescription, String description, String professorId,
            String thumbnailUrl, String category, String level, Boolean isPremium, String status,
            List<ModuleWire> modules, StatsWire stats) {
    }

    private record ModuleWire(String moduleId, String title, Boolean preview, Integer order, List<LessonWire> lessons) {
    }

    /** {@code videoId} is nullable: a lesson has none until a video is assigned. */
    private record LessonWire(
            String lessonId, String title, String description, Integer durationMinutes, Boolean isFree,
            Integer order, String videoId) {
    }

    private record StatsWire(Integer moduleCount, Integer lessonCount, String totalDuration) {
    }

    /** Create and update bodies; fields that are {@code null} are not sent. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CourseWriteWire(
            String title, String shortDescription, String description, String imageUrl, String category,
            String level, Boolean premium, String professorId) {
    }

    /** {@code VirtualModuleManagementResponse}. */
    private record ManagedModuleWire(
            String moduleId, String courseId, String title, Boolean preview, Integer order) {
    }

    /** Response of the reorder: the modules in their new order. */
    private record ModulesWire(List<ManagedModuleWire> modules) {
    }

    /** {@code VirtualLessonManagementResponse}; {@code videoId} is nullable. */
    private record ManagedLessonWire(
            String lessonId, String moduleId, String courseId, String title, String description, String videoId,
            Integer durationMinutes, Boolean free, Integer order) {
    }

    /** Create and update bodies of a module; fields that are {@code null} are not sent. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ModuleWriteWire(String title, Boolean preview, Integer order) {
    }

    /** Create and update bodies of a lesson; fields that are {@code null} are not sent. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record LessonWriteWire(
            String title, String description, String videoId, Integer durationMinutes, Boolean free, Integer order) {
    }

    private record ReorderWire(List<String> moduleIds) {
    }

    /** The only fields read from an error body. */
    private record ProblemWire(String code, String detail) {
    }
}
