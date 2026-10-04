package com.menta.virtual.infrastructure.persistence.repository;

import java.util.UUID;

/** Aggregate projection: number of modules per course. */
public interface ModuleCountProjection {

    /** Returns the id of the course the count belongs to. */
    UUID getCourseId();

    /** Returns the number of modules of the course. */
    Long getModuleCount();
}
