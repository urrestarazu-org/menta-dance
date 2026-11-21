package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PendingPaymentPage;
import com.menta.bff.application.port.out.AdminBillingApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link ListPendingPaymentsUseCase}: it only normalizes the paging, the API owns the rest. */
@RequiredArgsConstructor
public class ListPendingPaymentsUseCaseImpl implements ListPendingPaymentsUseCase {

    private final AdminBillingApiClient adminBillingApiClient;

    @Override
    public PendingPaymentPage execute(String accessToken, Integer page, Integer size) {
        String token = ManualPaymentSupport.token(accessToken);
        int pageIndex = page == null ? 0 : Math.max(page, 0);
        int pageSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return adminBillingApiClient.listPendingPayments(token, pageIndex, pageSize);
    }
}
