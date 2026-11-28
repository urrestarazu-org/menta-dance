package com.menta.bff.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form backing the rename page of a video ({@code /gestion/videos/{videoId}/renombrar}, #842). The API rejects a
 * blank title or one over 255 characters with a {@code 400} that names no field, so both limits are checked here, with
 * a Spanish message next to the field. The controller trims the text before binding.
 */
@Getter
@Setter
@NoArgsConstructor
public class VideoRenameForm {

    @NotBlank(message = "Ingresá el título.")
    @Size(max = 255, message = "El título puede tener hasta 255 caracteres.")
    private String title;

    /** Prefills the page with the title that came in the link. */
    public static VideoRenameForm of(String title) {
        VideoRenameForm form = new VideoRenameForm();
        form.title = title;
        return form;
    }
}
