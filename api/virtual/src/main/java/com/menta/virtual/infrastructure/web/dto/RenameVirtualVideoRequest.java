package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.port.in.RenameLibraryVideoUseCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code PUT /api/v1/admin/virtual/videos/{videoId}} (#841): the new title, not blank and at most
 * {@value RenameLibraryVideoUseCase#MAX_TITLE_LENGTH} characters. Surrounding whitespace is stripped by the use case.
 */
public record RenameVirtualVideoRequest(
    @NotBlank @Size(max = RenameLibraryVideoUseCase.MAX_TITLE_LENGTH) String title
) {
}
