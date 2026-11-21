package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ExceptionPurchasePage;
import com.menta.bff.application.port.out.AdminBillingApiClient;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link ListExceptionPurchasesUseCase}: it normalizes the paging and words the rows. */
@RequiredArgsConstructor
public class ListExceptionPurchasesUseCaseImpl implements ListExceptionPurchasesUseCase {

    private final AdminBillingApiClient adminBillingApiClient;
    private final ZoneId displayZone;

    @Override
    public ExceptionPurchasesView execute(String accessToken, Integer page, Integer size) {
        String token = ManualPaymentSupport.token(accessToken);
        ExceptionPurchasePage result = adminBillingApiClient.listPurchasesInException(
                token, ManualPaymentSupport.pageIndex(page), ManualPaymentSupport.pageSize(size));
        PaymentViewFactory factory = new PaymentViewFactory(displayZone);
        return new ExceptionPurchasesView(result.items().stream().map(factory::of).toList(), result.page(),
                result.size(), result.totalPages(), result.totalElements(), false);
    }
}
