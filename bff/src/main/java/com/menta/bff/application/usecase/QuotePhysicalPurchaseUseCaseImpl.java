package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PhysicalCourseDetail;
import com.menta.bff.application.dto.PhysicalPurchaseType;
import com.menta.bff.application.dto.PhysicalQuote;
import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import java.time.ZoneId;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link QuotePhysicalPurchaseUseCase}. The price, the period
 * and the availability are decided upstream; this class only names the course
 * and words the figures in Spanish.
 */
@RequiredArgsConstructor
public class QuotePhysicalPurchaseUseCaseImpl implements QuotePhysicalPurchaseUseCase {

    private final PhysicalPurchaseApiClient purchaseApiClient;
    private final CatalogApiClient catalogApiClient;
    private final ZoneId displayZone;
    private final Supplier<String> idempotencyKeys;

    @Override
    public PurchaseQuoteView execute(
            String accessToken, String courseId, PhysicalPurchaseType purchaseType, String selectedSessionId) {
        // The catalog is read first: an unknown or virtual course must not leave a quote behind.
        PhysicalCourseDetail course = catalogApiClient.getPhysicalCourse(courseId);
        PhysicalQuote quote = purchaseApiClient.createQuote(accessToken, courseId, purchaseType, selectedSessionId);
        return new PurchaseQuoteView(
                quote.id(), courseId, course.title(), modality(quote.purchaseType()),
                ClassLabels.amount(quote.amount(), quote.currency()), quote.scheduledSessionCount(),
                sessionLabel(course, quote),
                ClassLabels.moment(quote.expiresAt(), displayZone), quote.unavailable(),
                idempotencyKeys.get());
    }

    private static String modality(PhysicalPurchaseType type) {
        return type == PhysicalPurchaseType.MONTHLY ? "Mensualidad: todas las clases del mes" : "Clase individual";
    }

    private String sessionLabel(PhysicalCourseDetail course, PhysicalQuote quote) {
        if (quote.selectedSessionId() == null) {
            return null;
        }
        return course.sessions().stream()
                .filter(s -> s.sessionId().equals(quote.selectedSessionId()))
                .map(s -> ClassLabels.moment(s.scheduledAt(), displayZone))
                .findFirst()
                .orElse(null);
    }
}
