package com.menta.bff.infrastructure.config;

import com.menta.bff.application.port.out.AccessTokenClaimsReader;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.PhysicalCatalogApiClient;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import com.menta.bff.application.port.out.SessionTokenRepository;
import com.menta.bff.application.port.out.VirtualApiClient;
import com.menta.bff.application.usecase.GetClassDetailViewUseCase;
import com.menta.bff.application.usecase.GetClassDetailViewUseCaseImpl;
import com.menta.bff.application.usecase.GetClassesViewUseCase;
import com.menta.bff.application.usecase.GetClassesViewUseCaseImpl;
import com.menta.bff.application.usecase.GetCourseDetailViewUseCase;
import com.menta.bff.application.usecase.GetCourseDetailViewUseCaseImpl;
import com.menta.bff.application.usecase.GetLessonViewUseCase;
import com.menta.bff.application.usecase.GetLessonViewUseCaseImpl;
import com.menta.bff.application.usecase.GetPlansViewUseCase;
import com.menta.bff.application.usecase.GetPlansViewUseCaseImpl;
import com.menta.bff.application.usecase.GetValidAccessTokenUseCase;
import com.menta.bff.application.usecase.GetValidAccessTokenUseCaseImpl;
import com.menta.bff.application.usecase.LoginUseCase;
import com.menta.bff.application.usecase.LoginUseCaseImpl;
import com.menta.bff.application.usecase.LogoutUseCase;
import com.menta.bff.application.usecase.LogoutUseCaseImpl;
import com.menta.bff.application.usecase.PurchaseClassUseCase;
import com.menta.bff.application.usecase.PurchaseClassUseCaseImpl;
import com.menta.bff.application.usecase.QuoteClassUseCase;
import com.menta.bff.application.usecase.QuoteClassUseCaseImpl;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for use case beans.
 *
 * <p>Wires up application layer use cases with infrastructure adapters.
 * </p>
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public LoginUseCase loginUseCase(
            AuthApiClient authApiClient,
            SessionTokenRepository sessionTokenRepository,
            AccessTokenClaimsReader accessTokenClaimsReader
    ) {
        return new LoginUseCaseImpl(authApiClient, sessionTokenRepository, accessTokenClaimsReader);
    }

    @Bean
    public LogoutUseCase logoutUseCase(
            AuthApiClient authApiClient,
            SessionTokenRepository sessionTokenRepository
    ) {
        return new LogoutUseCaseImpl(authApiClient, sessionTokenRepository);
    }

    @Bean
    public GetValidAccessTokenUseCase getValidAccessTokenUseCase(
            AuthApiClient authApiClient,
            SessionTokenRepository sessionTokenRepository
    ) {
        return new GetValidAccessTokenUseCaseImpl(authApiClient, sessionTokenRepository);
    }

    @Bean
    public TokenRefreshFilter tokenRefreshFilter(GetValidAccessTokenUseCase getValidAccessTokenUseCase) {
        return new TokenRefreshFilter(getValidAccessTokenUseCase);
    }

    @Bean
    public GetCourseDetailViewUseCase getCourseDetailViewUseCase(VirtualApiClient virtualApiClient) {
        return new GetCourseDetailViewUseCaseImpl(virtualApiClient);
    }

    @Bean
    public GetLessonViewUseCase getLessonViewUseCase(VirtualApiClient virtualApiClient) {
        return new GetLessonViewUseCaseImpl(virtualApiClient);
    }

    @Bean
    public GetPlansViewUseCase getPlansViewUseCase(BillingApiClient billingApiClient) {
        return new GetPlansViewUseCaseImpl(billingApiClient);
    }

    @Bean
    public GetClassesViewUseCase getClassesViewUseCase(PhysicalCatalogApiClient physicalCatalogApiClient) {
        return new GetClassesViewUseCaseImpl(physicalCatalogApiClient);
    }

    @Bean
    public GetClassDetailViewUseCase getClassDetailViewUseCase(
            PhysicalCatalogApiClient physicalCatalogApiClient,
            PhysicalPurchaseApiClient physicalPurchaseApiClient,
            @Value("${menta.classes.display-zone}") ZoneId classesDisplayZone
    ) {
        return new GetClassDetailViewUseCaseImpl(
                physicalCatalogApiClient, physicalPurchaseApiClient, classesDisplayZone);
    }

    @Bean
    public QuoteClassUseCase quoteClassUseCase(
            PhysicalPurchaseApiClient physicalPurchaseApiClient,
            @Value("${menta.classes.display-zone}") ZoneId classesDisplayZone
    ) {
        return new QuoteClassUseCaseImpl(
                physicalPurchaseApiClient, classesDisplayZone, () -> UUID.randomUUID().toString());
    }

    @Bean
    public PurchaseClassUseCase purchaseClassUseCase(PhysicalPurchaseApiClient physicalPurchaseApiClient) {
        return new PurchaseClassUseCaseImpl(physicalPurchaseApiClient);
    }
}
