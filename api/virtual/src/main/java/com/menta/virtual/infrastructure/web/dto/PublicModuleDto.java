package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.PublicModuleRef;

public record PublicModuleDto(
    String moduleId,
    String title
) {

    public static PublicModuleDto from(PublicModuleRef ref) {
        return new PublicModuleDto(ref.moduleId(), ref.title());
    }
}
