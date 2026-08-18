package com.menta.billing.domain.model;

/**
 * What a {@link Payment} pays for — exactly one modality, mirroring #95's
 * catalog composition: a course is either physical or virtual, never both,
 * and the type system should make the other case unrepresentable rather
 * than carrying two nullable ids.
 */
public sealed interface PaymentTarget {

    record Physical(String sessionId) implements PaymentTarget {
        public Physical {
            if (sessionId == null || sessionId.isBlank()) {
                throw new IllegalArgumentException("sessionId cannot be null or blank");
            }
        }
    }

    record Virtual(String courseId) implements PaymentTarget {
        public Virtual {
            if (courseId == null || courseId.isBlank()) {
                throw new IllegalArgumentException("courseId cannot be null or blank");
            }
        }
    }
}
