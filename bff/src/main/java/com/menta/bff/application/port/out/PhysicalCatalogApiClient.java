package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.PhysicalClassDetail;
import com.menta.bff.application.dto.PhysicalClassSummary;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Port for the public catalog's in-person courses. Both calls are anonymous
 * and caller-agnostic, so neither takes an access token.
 */
public interface PhysicalCatalogApiClient {

    /**
     * Lists the catalog's {@code PHYSICAL} courses.
     *
     * <p>Calls GET /api/v1/catalog/courses and drops every other modality.
     * </p>
     *
     * @return physical courses, in upstream order
     * @throws ServiceUnavailableException if the upstream is unreachable or errors
     */
    List<PhysicalClassSummary> listClasses();

    /**
     * Retrieves one {@code PHYSICAL} course with its upcoming sessions.
     *
     * <p>Calls GET /api/v1/catalog/courses/{courseId}. A {@code 200} whose
     * {@code modality} is not {@code PHYSICAL} (a virtual id) is reported as
     * not found, since this port only ever describes in-person courses.
     * </p>
     *
     * @param courseId course identifier
     * @return the course and its sessions
     * @throws NotFoundException           if the id is unknown, inactive, or not physical
     * @throws ServiceUnavailableException if the upstream is unreachable or errors
     */
    PhysicalClassDetail getClassDetail(String courseId);

    /**
     * Thrown when the course does not exist as an in-person course (404).
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when the catalog is unreachable, rate-limited or errors.
     */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }

        public ServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
