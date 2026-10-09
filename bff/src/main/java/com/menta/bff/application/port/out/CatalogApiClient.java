package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.CatalogCourse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Port for the upstream public course catalog list. Part of Clean Architecture
 * application layer — abstracts HTTP/REST details.
 *
 * <p>Kept apart from {@link VirtualApiClient}: the list is composed upstream
 * from both modalities and has a single failure mode, so it does not share
 * that port's detail/lesson exceptions.
 * </p>
 */
public interface CatalogApiClient {

    /**
     * Retrieves every published course, virtual and physical.
     *
     * <p>Calls GET /api/v1/catalog/courses. Never sends an {@code Authorization}
     * header — the catalog is public and takes no access-token parameter.
     * </p>
     *
     * @return the courses in upstream order, possibly empty
     * @throws ServiceUnavailableException if the upstream is unreachable, times out, errors or answers unusably
     */
    List<CatalogCourse> listCourses();

    /**
     * Exception thrown when the catalog cannot be obtained for any reason. Its
     * message is for logs only and must never reach the browser.
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
