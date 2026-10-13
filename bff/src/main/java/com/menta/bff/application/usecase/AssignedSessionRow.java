package com.menta.bff.application.usecase;

/**
 * One assigned session ready to render on the payment return page.
 *
 * @param sessionId   session identifier
 * @param courseName  name of the class
 * @param momentLabel Spanish date and time in the academy's zone
 */
public record AssignedSessionRow(String sessionId, String courseName, String momentLabel) {
}
