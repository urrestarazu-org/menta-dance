package com.menta.virtual.infrastructure.persistence.adapter;

import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualModule;
import com.menta.virtual.infrastructure.persistence.mapper.VirtualModuleJpaMapper;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link VirtualModuleRepository}. */
@Component
public class VirtualModuleRepositoryAdapter implements VirtualModuleRepository {

    private final VirtualModuleJpaRepository moduleRepository;

    public VirtualModuleRepositoryAdapter(VirtualModuleJpaRepository moduleRepository) {
        this.moduleRepository = moduleRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<VirtualModule> findById(ModuleId moduleId) {
        return moduleRepository.findById(moduleId.getValue()).map(VirtualModuleJpaMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<VirtualModule> findByCourseId(CourseId courseId) {
        return moduleRepository.findByCourseIdOrderByDisplayOrderAsc(courseId.getValue()).stream()
            .map(VirtualModuleJpaMapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public int countByCourseId(CourseId courseId) {
        return (int) moduleRepository.countByCourseId(courseId.getValue());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public VirtualModule save(VirtualModule module) {
        return VirtualModuleJpaMapper.toDomain(moduleRepository.save(VirtualModuleJpaMapper.toEntity(module)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void saveAll(List<VirtualModule> modules) {
        moduleRepository.saveAll(modules.stream().map(VirtualModuleJpaMapper::toEntity).toList());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteByCourseId(CourseId courseId) {
        moduleRepository.deleteByCourseId(courseId.getValue());
    }
}
