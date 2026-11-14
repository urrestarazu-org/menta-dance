package com.menta.bff.application.usecase;

/**
 * The signed-in instructor as the course form needs it: who a course created by them is assigned to.
 *
 * @param id   account identifier, always a canonical UUID
 * @param name display name, never blank
 */
public record OwnInstructor(String id, String name) {
}
