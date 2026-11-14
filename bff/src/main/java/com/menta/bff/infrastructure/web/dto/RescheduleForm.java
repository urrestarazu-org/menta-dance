package com.menta.bff.infrastructure.web.dto;

import com.menta.bff.application.dto.UpdateSessionCommand;
import com.menta.bff.application.usecase.ManagedSessionView;
import com.menta.bff.infrastructure.web.validation.IsoDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form of the reschedule page, {@code /gestion/cursos-presenciales/{courseId}/sesiones/{sessionId}/reprogramar}
 * (#368): the new date and time of a session, in the academy's zone. Both are required because they always
 * travel together: the UTC date the API receives depends on the hour. Nothing else of the session changes, and
 * whether the session can be moved is the API's decision. The values travel as text so a malformed one is one
 * Spanish message instead of a binding failure.
 */
@Getter
@Setter
@NoArgsConstructor
public class RescheduleForm {

    private static final String TIME_MESSAGE = "Ingresá la hora con el formato HH:mm";

    @NotBlank(message = "Ingresá la nueva fecha")
    @IsoDate(message = "Ingresá una fecha válida, con el formato aaaa-mm-dd")
    private String localDate;

    @NotNull(message = TIME_MESSAGE)
    @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]", message = TIME_MESSAGE)
    private String localTime;

    /**
     * A form with the time the session has now, so that moving it to another day is one field; the date is left
     * empty so that submitting without choosing one does not look like a change.
     *
     * @param session the session to move
     * @return the form
     */
    public static RescheduleForm from(ManagedSessionView session) {
        RescheduleForm form = new RescheduleForm();
        form.localTime = session.localTime().toString();
        return form;
    }

    /**
     * Builds the change request: the new date and time, and nothing else.
     *
     * @return the command; only meaningful once the form validated
     */
    public UpdateSessionCommand toCommand() {
        return new UpdateSessionCommand(LocalDate.parse(localDate), LocalTime.parse(localTime), null, null, null);
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
}
