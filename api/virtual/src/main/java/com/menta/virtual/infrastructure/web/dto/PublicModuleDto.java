package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.PublicModuleRef;

/** Wire form of the compact module reference carried inside a lesson detail. */
public record PublicModuleDto(
    String moduleId,
    String title
) {

    /** Maps the application reference to its wire form. */
    public static PublicModuleDto from(PublicModuleRef ref) {
        return new PublicModuleDto(ref.moduleId(), ref.title());
    }
}
