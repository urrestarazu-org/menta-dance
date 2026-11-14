package com.menta.bff.infrastructure.web.dto;

import com.menta.bff.application.dto.CreateCourseCommand;
import com.menta.bff.application.dto.UpdateCourseCommand;
import com.menta.bff.application.usecase.ManagedCourseView;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form backing the course creation and edit pages ({@code /gestion/cursos-presenciales/nuevo} and
 * {@code /{courseId}/editar}, #368).
 *
 * <p>The constraints mirror the Physical API's ({@code CreatePhysicalCourseRequest}: title and description
 * required, duration and capacity positive, level one of the three, the title at most the column's 255) so the
 * user gets Spanish errors next to the field without a round trip; the API stays authoritative. Numbers and the
 * time travel as text and are checked with a pattern, so a malformed value is one Spanish message instead of a
 * binding failure. The day and the time are the ones in the academy's zone. The instructor is not part of what
 * the form decides: {@code professorId} is only the administrator's choice, which the controller checks against
 * the instructors listed, and the name is never submitted.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
public class CourseForm {

    private static final String POSITIVE_INTEGER = "[1-9][0-9]{0,5}";

    @NotBlank(message = "Ingresá el título")
    @Size(max = 255, message = "El título no puede superar los 255 caracteres")
    private String title;

    @NotBlank(message = "Ingresá la descripción")
    private String description;

    private String localDay;

    @NotNull(message = "Ingresá la hora de inicio con el formato HH:mm")
    @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]", message = "Ingresá la hora de inicio con el formato HH:mm")
    private String localTime;

    @NotNull(message = "Ingresá una duración de 1 minuto o más")
    @Pattern(regexp = POSITIVE_INTEGER, message = "Ingresá una duración de 1 minuto o más")
    private String durationMinutes;

    private String level;

    @NotNull(message = "Ingresá un cupo de 1 persona o más")
    @Pattern(regexp = POSITIVE_INTEGER, message = "Ingresá un cupo de 1 persona o más")
    private String capacity;

    /** Only the edit page shows it; empty on the create page. */
    private String status;

    /** Only the administrator's create page shows it: the chosen instructor's id. */
    private String professorId;

    /**
     * Prefills the edit form with the course as it is, in the academy's zone.
     *
     * @param course the course to edit
     * @return the form
     */
    public static CourseForm from(ManagedCourseView course) {
        CourseForm form = new CourseForm();
        form.title = course.title();
        form.description = course.description();
        form.localDay = course.localDay().name();
        form.localTime = course.localTime().toString();
        form.durationMinutes = String.valueOf(course.durationMinutes());
        form.level = course.level();
        form.capacity = String.valueOf(course.capacity());
        form.status = course.active() ? "ACTIVE" : "INACTIVE";
        return form;
    }

    @AssertTrue(message = "Elegí un día de la semana")
    public boolean isDayValid() {
        return localDay != null && CourseFormOptions.DAYS.containsKey(localDay);
    }

    @AssertTrue(message = "Elegí un nivel de la lista")
    public boolean isLevelValid() {
        return level != null && CourseFormOptions.LEVELS.containsKey(level);
    }

    /** Absent is fine (the create page has none); a present value must be one of the two statuses. */
    @AssertTrue(message = "Elegí un estado de la lista")
    public boolean isStatusValid() {
        return status == null || CourseFormOptions.STATUSES.containsKey(status);
    }

    /**
     * Builds the creation request.
     *
     * @param instructorId   the instructor the course is assigned to, decided by the controller
     * @param instructorName the instructor's name, decided by the controller
     * @return the command; only meaningful once the form validated
     */
    public CreateCourseCommand toCreateCommand(String instructorId, String instructorName) {
        return new CreateCourseCommand(title, description, instructorId, instructorName,
                DayOfWeek.valueOf(localDay), LocalTime.parse(localTime), Integer.parseInt(durationMinutes), level,
                Integer.parseInt(capacity));
    }

    /**
     * Builds the change request. The day and the time always travel together, because the UTC weekday depends on
     * the hour.
     *
     * @return the command; only meaningful once the form validated
     */
    public UpdateCourseCommand toUpdateCommand() {
        return new UpdateCourseCommand(title, description, DayOfWeek.valueOf(localDay), LocalTime.parse(localTime),
                Integer.parseInt(durationMinutes), level, Integer.parseInt(capacity), status);
    }

    /**
     * Stores the title without surrounding whitespace.
     *
     * @param title the submitted title, possibly null
     */
    public void setTitle(String title) {
        this.title = title == null ? null : title.strip();
    }

    /**
     * Stores the description without surrounding whitespace.
     *
     * @param description the submitted description, possibly null
     */
    public void setDescription(String description) {
        this.description = description == null ? null : description.strip();
    }

    /**
     * Stores the time without surrounding whitespace.
     *
     * @param localTime the submitted time, possibly null
     */
    public void setLocalTime(String localTime) {
        this.localTime = localTime == null ? null : localTime.strip();
    }

    /**
     * Stores the duration without surrounding whitespace.
     *
     * @param durationMinutes the submitted duration, possibly null
     */
    public void setDurationMinutes(String durationMinutes) {
        this.durationMinutes = durationMinutes == null ? null : durationMinutes.strip();
    }

    /**
     * Stores the capacity without surrounding whitespace.
     *
     * @param capacity the submitted capacity, possibly null
     */
    public void setCapacity(String capacity) {
        this.capacity = capacity == null ? null : capacity.strip();
    }
}
