package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PendingPaymentPage;
import com.menta.bff.application.port.out.AdminBillingApiClient;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link ListPendingPaymentsUseCase}: it normalizes the paging and words the rows, the API owns
 * the rest.
 */
@RequiredArgsConstructor
public class ListPendingPaymentsUseCaseImpl implements ListPendingPaymentsUseCase {

    private final AdminBillingApiClient adminBillingApiClient;
    private final ZoneId displayZone;

    @Override
    public PendingPaymentsView execute(String accessToken, Integer page, Integer size) {
        String token = ManualPaymentSupport.token(accessToken);
        PendingPaymentPage result = adminBillingApiClient.listPendingPayments(
                token, ManualPaymentSupport.pageIndex(page), ManualPaymentSupport.pageSize(size));
        PaymentViewFactory factory = new PaymentViewFactory(displayZone);
        List<PaymentVerificationView> rows = result.items().stream()
                .map(p -> factory.of(p.paymentId(), p.userId(), p.targetModality(), p.amount(), p.currency(),
                        p.statusType(), p.hasProof(), p.createdAt()))
                .toList();
        return new PendingPaymentsView(rows, result.page(), result.size(), result.totalPages(),
                result.totalElements(), false);
    }
}
