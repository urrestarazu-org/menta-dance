package com.menta.virtual.infrastructure.persistence.repository;

import java.util.UUID;

/** Aggregate projection: number of modules per course. */
public interface ModuleCountProjection {

    UUID getCourseId();

    Long getModuleCount();
}
