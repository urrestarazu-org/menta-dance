package com.menta.virtual.infrastructure.persistence.repository;

import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VirtualModuleJpaRepository extends JpaRepository<VirtualModuleJpaEntity, UUID> {

    @Query(
        "SELECT m.courseId AS courseId, COUNT(m) AS moduleCount FROM VirtualModuleJpaEntity m "
            + "WHERE m.courseId IN :courseIds GROUP BY m.courseId"
    )
    List<ModuleCountProjection> countByCourseIdIn(@Param("courseIds") List<UUID> courseIds);

    /** Management view (US-VIRTUAL-006) — every module of a course, ordered for display/reorder. */
    List<VirtualModuleJpaEntity> findByCourseIdOrderByDisplayOrderAsc(UUID courseId);

    long countByCourseId(UUID courseId);
}
