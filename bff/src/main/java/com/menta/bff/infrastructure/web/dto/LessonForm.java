package com.menta.bff.infrastructure.web.dto;

import com.menta.bff.application.dto.CreateVirtualLessonCommand;
import com.menta.bff.application.dto.VirtualCourseDetail.VirtualLessonDetail;
import com.menta.bff.application.dto.VirtualLessonChanges;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form backing the lesson creation and edit pages of a virtual course (#367). The Virtual API validates no length
 * and its errors name no field, so the limits are checked here, with a Spanish message each: title 255 (the column's),
 * description 5000 (a TEXT column; the same cap as the course form), video id 128. The video id is the opaque id of a
 * video that already exists, never an upload. There is no order field: a new lesson goes last.
 *
 * <p>The duration is a text, like the other numeric forms of the BFF, so that one message covers a non-numeric,
 * negative or out of range value: whole minutes from 0 to 9999, and empty is 0. A video cannot be cleared once
 * assigned, so an empty video id is "no video" on creation and "do not change it" on edition: it is not sent.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
public class LessonForm {

    @NotBlank(message = "Ingresá el título de la lección.")
    @Size(max = 255, message = "El título puede tener hasta 255 caracteres.")
    private String title;

    @NotBlank(message = "Ingresá la descripción de la lección.")
    @Size(max = 5000, message = "La descripción puede tener hasta 5000 caracteres.")
    private String description;

    @Size(max = 128, message = "El identificador del video puede tener hasta 128 caracteres.")
    @Pattern(regexp = "\\S*", message = "El identificador del video no puede tener espacios.")
    private String videoId;

    @Pattern(regexp = "\\d{0,4}", message = "Ingresá la duración en minutos: un número entero de 0 a 9999.")
    private String durationMinutes = "0";

    /** Whether the lesson can be seen without a subscription. */
    private boolean free;

    /** Prefills the edit form with the lesson as it is. */
    public static LessonForm from(VirtualLessonDetail lesson) {
        LessonForm form = new LessonForm();
        form.title = lesson.title();
        form.description = lesson.description();
        form.videoId = lesson.videoId();
        form.durationMinutes = String.valueOf(lesson.durationMinutes());
        form.free = lesson.free();
        return form;
    }

    /** Builds the creation request; no order, so the API appends the lesson. Only meaningful once validated. */
    public CreateVirtualLessonCommand toCommand() {
        return new CreateVirtualLessonCommand(title, description, videoToSend(), minutes(), free, null);
    }

    /** Builds the change request with every field of the form; the order is not touched. */
    public VirtualLessonChanges toChanges() {
        return new VirtualLessonChanges(title, description, videoToSend(), minutes(), free, null);
    }

    private String videoToSend() {
        return videoId == null || videoId.isEmpty() ? null : videoId;
    }

    private int minutes() {
        return durationMinutes == null || durationMinutes.isEmpty() ? 0 : Integer.parseInt(durationMinutes);
    }
}
