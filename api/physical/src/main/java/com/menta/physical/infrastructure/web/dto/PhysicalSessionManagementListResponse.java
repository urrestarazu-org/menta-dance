package com.menta.physical.infrastructure.web.dto;

import java.util.List;

/**
 * Wire shape wrapping the sessions of a course under a {@code sessions} key.
 *
 * <p>Mirrors the wrapper used by the device and course lists instead of returning a bare array.
 */
public record PhysicalSessionManagementListResponse(List<PhysicalSessionManagementResponse> sessions) {
}
