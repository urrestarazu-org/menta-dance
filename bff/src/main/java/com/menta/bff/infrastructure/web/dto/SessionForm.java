package com.menta.bff.infrastructure.web.dto;

import com.menta.bff.application.dto.CreateSessionCommand;
import com.menta.bff.application.usecase.ManagedCourseView;
import com.menta.bff.infrastructure.web.validation.IsoDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form of the single-session creation on {@code /gestion/cursos-presenciales/{courseId}/sesiones} (#368).
 *
 * <p>The constraints mirror the Physical API's ({@code CreatePhysicalSessionRequest}: date and time required,
 * capacity at least 1 or absent to inherit the course's, notes at most the column's 500) so the user gets
 * Spanish errors next to the field without a round trip; the API stays authoritative. The date and the time are
 * the ones in the academy's zone, and travel as text so a malformed value is one Spanish message instead of a
 * binding failure. Whether the course already has a session at that moment is the API's decision, not this
 * form's.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
public class SessionForm {

    private static final String POSITIVE_INTEGER = "[1-9][0-9]{0,5}";
    private static final String TIME_MESSAGE = "Ingresá la hora con el formato HH:mm";

    @NotBlank(message = "Ingresá la fecha de la sesión")
    @IsoDate(message = "Ingresá una fecha válida, con el formato aaaa-mm-dd")
    private String localDate;

    @NotNull(message = TIME_MESSAGE)
    @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]", message = TIME_MESSAGE)
    private String localTime;

    /** Empty means "the course's capacity". */
    @Pattern(regexp = POSITIVE_INTEGER, message = "Ingresá un cupo de 1 persona o más")
    private String capacity;

    @Size(max = 500, message = "Las notas no pueden superar los 500 caracteres")
    private String notes;

    /**
     * An empty form whose time is the course's usual one, which is what the administrator will mostly want.
     *
     * @param course the course the session is for
     * @return the form
     */
    public static SessionForm forCourse(ManagedCourseView course) {
        SessionForm form = new SessionForm();
        form.localTime = course.localTime().toString();
        return form;
    }

    /**
     * Builds the creation request.
     *
     * @return the command; only meaningful once the form validated
     */
    public CreateSessionCommand toCommand() {
        return new CreateSessionCommand(LocalDate.parse(localDate), LocalTime.parse(localTime),
                capacity == null ? null : Integer.valueOf(capacity), notes);
    }

    /**
     * Stores the date without surrounding whitespace.
     *
     * @param localDate the submitted date, possibly null
     */
    public void setLocalDate(String localDate) {
        this.localDate = localDate == null ? null : localDate.strip();
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
     * Stores the capacity without surrounding whitespace; an empty one is none.
     *
     * @param capacity the submitted capacity, possibly null or blank
     */
    public void setCapacity(String capacity) {
        this.capacity = blankToNull(capacity);
    }

    /**
     * Stores the notes without surrounding whitespace; empty ones are none.
     *
     * @param notes the submitted notes, possibly null or blank
     */
    public void setNotes(String notes) {
        this.notes = blankToNull(notes);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
