package com.menta.virtual.application.dto;

import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.ModuleId;

/** Compact module reference carried inside a public lesson detail. */
public record PublicModuleRef(
    String moduleId,
    String title
) {

    public static PublicModuleRef of(ModuleId id, String title) {
        return new PublicModuleRef(id.toString(), title);
    }
}
