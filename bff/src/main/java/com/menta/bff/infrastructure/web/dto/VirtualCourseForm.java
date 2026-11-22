package com.menta.bff.infrastructure.web.dto;

import com.menta.bff.application.dto.CreateVirtualCourseCommand;
import com.menta.bff.application.dto.VirtualCourseChanges;
import com.menta.bff.application.dto.VirtualCourseDetail;
import com.menta.bff.application.dto.VirtualCourseLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form backing the virtual course creation and edit pages ({@code /gestion/cursos-virtuales/nuevo} and
 * {@code /{courseId}/editar}, #367).
 *
 * <p>The Virtual API only requires the five texts and the level and validates no length, and its errors name no
 * field, so the user gets Spanish messages next to each field here. The limits are the columns' (title 255, short
 * description 500, image URL 500, category 100); the description is a TEXT column, and 5000 is the BFF's own
 * reasonable cap. The controller trims every text before binding. The level travels as one of the API's
 * three names. {@code premium} only exists on the edit page (the API creates every course as a draft and does not
 * take it then) and {@code professorId} is only the administrator's optional choice when creating: the controller
 * decides whether it is sent.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
public class VirtualCourseForm {

    @NotBlank(message = "Ingresá el título.")
    @Size(max = 255, message = "El título puede tener hasta 255 caracteres.")
    private String title;

    @NotBlank(message = "Ingresá la descripción corta.")
    @Size(max = 500, message = "La descripción corta puede tener hasta 500 caracteres.")
    private String shortDescription;

    @NotBlank(message = "Ingresá la descripción.")
    @Size(max = 5000, message = "La descripción puede tener hasta 5000 caracteres.")
    private String description;

    /** An empty value is allowed by the pattern so that a blank field reports only the required message. */
    @NotBlank(message = "Ingresá la URL de la imagen.")
    @Size(max = 500, message = "La URL de la imagen puede tener hasta 500 caracteres.")
    @Pattern(regexp = "(https?://\\S+)?", message = "La URL de la imagen debe empezar con http:// o https://.")
    private String imageUrl;

    @NotBlank(message = "Ingresá la categoría.")
    @Size(max = 100, message = "La categoría puede tener hasta 100 caracteres.")
    private String category;

    @NotNull(message = "Elegí un nivel.")
    @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED", message = "Elegí un nivel.")
    private String level;

    /** Only the edit page shows it. */
    private boolean premium;

    /** Only the administrator's create page shows it: the chosen instructor's id, empty for themselves. */
    private String professorId;

    /**
     * Prefills the edit form with the course as it is.
     *
     * @param course the course to edit
     * @return the form; the level stays empty when the BFF does not know the API's value
     */
    public static VirtualCourseForm from(VirtualCourseDetail course) {
        VirtualCourseForm form = new VirtualCourseForm();
        form.title = course.title();
        form.shortDescription = course.shortDescription();
        form.description = course.description();
        form.imageUrl = course.thumbnailUrl();
        form.category = course.category();
        form.level = course.level() == VirtualCourseLevel.UNKNOWN ? null : course.level().name();
        form.premium = course.premium();
        return form;
    }

    /**
     * Builds the creation request.
     *
     * @param professorId the instructor to assign the course to, or {@code null} to leave it to the caller
     * @return the command; only meaningful once the form validated
     */
    public CreateVirtualCourseCommand toCommand(String professorId) {
        return new CreateVirtualCourseCommand(title, shortDescription, description, imageUrl, category,
                VirtualCourseLevel.valueOf(level), professorId);
    }

    /**
     * Builds the change request with every field of the form.
     *
     * @return the changes; only meaningful once the form validated
     */
    public VirtualCourseChanges toChanges() {
        return new VirtualCourseChanges(title, shortDescription, description, imageUrl, category,
                VirtualCourseLevel.valueOf(level), premium);
    }
}
