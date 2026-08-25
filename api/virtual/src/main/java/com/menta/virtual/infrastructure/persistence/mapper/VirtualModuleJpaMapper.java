package com.menta.virtual.infrastructure.persistence.mapper;

import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualModule;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;

/** Manual mapper JPA entity ↔ domain — no MapStruct (unused in this project, see #96). */
public final class VirtualModuleJpaMapper {

    private VirtualModuleJpaMapper() {
    }

    public static VirtualModule toDomain(VirtualModuleJpaEntity entity) {
        return new VirtualModule(
            ModuleId.of(entity.getId()), CourseId.of(entity.getCourseId()), entity.getTitle(), entity.isPreview(),
            entity.getDisplayOrder()
        );
    }

    public static VirtualModuleJpaEntity toEntity(VirtualModule module) {
        return new VirtualModuleJpaEntity(
            module.getId().getValue(), module.getCourseId().getValue(), module.getTitle(), module.isPreview(),
            module.getOrder()
        );
    }
}
