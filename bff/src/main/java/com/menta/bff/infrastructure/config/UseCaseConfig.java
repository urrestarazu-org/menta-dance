package com.menta.bff.infrastructure.config;

import com.menta.bff.application.port.out.AccessTokenClaimsReader;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import com.menta.bff.application.port.out.SessionTokenRepository;
import com.menta.bff.application.port.out.VirtualApiClient;
import com.menta.bff.application.usecase.AccountActivationUseCase;
import com.menta.bff.application.usecase.AccountActivationUseCaseImpl;
import com.menta.bff.application.usecase.GetCatalogViewUseCase;
import com.menta.bff.application.usecase.GetCatalogViewUseCaseImpl;
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
import com.menta.bff.application.usecase.QuotePhysicalPurchaseUseCase;
import com.menta.bff.application.usecase.QuotePhysicalPurchaseUseCaseImpl;
import com.menta.bff.application.usecase.RegisterAccountUseCase;
import com.menta.bff.application.usecase.RegisterAccountUseCaseImpl;
import com.menta.bff.application.usecase.RequestPasswordResetUseCase;
import com.menta.bff.application.usecase.RequestPasswordResetUseCaseImpl;
import com.menta.bff.application.usecase.ResendActivationUseCase;
import com.menta.bff.application.usecase.ResendActivationUseCaseImpl;
import com.menta.bff.application.usecase.ResetPasswordUseCase;
import com.menta.bff.application.usecase.ResetPasswordUseCaseImpl;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
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
    public RegisterAccountUseCase registerAccountUseCase(AuthApiClient authApiClient) {
        return new RegisterAccountUseCaseImpl(authApiClient);
    }

    @Bean
    public ResendActivationUseCase resendActivationUseCase(AuthApiClient authApiClient) {
        return new ResendActivationUseCaseImpl(authApiClient);
    }

    @Bean
    public RequestPasswordResetUseCase requestPasswordResetUseCase(AuthApiClient authApiClient) {
        return new RequestPasswordResetUseCaseImpl(authApiClient);
    }

    @Bean
    public ResetPasswordUseCase resetPasswordUseCase(AuthApiClient authApiClient) {
        return new ResetPasswordUseCaseImpl(authApiClient);
    }

    @Bean
    public AccountActivationUseCase accountActivationUseCase(AuthApiClient authApiClient) {
        return new AccountActivationUseCaseImpl(authApiClient);
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
    public GetCatalogViewUseCase getCatalogViewUseCase(CatalogApiClient catalogApiClient) {
        return new GetCatalogViewUseCaseImpl(catalogApiClient);
    }

    @Bean
    public GetClassesViewUseCase getClassesViewUseCase(CatalogApiClient catalogApiClient) {
        return new GetClassesViewUseCaseImpl(catalogApiClient);
    }

    @Bean
    public GetClassDetailViewUseCase getClassDetailViewUseCase(
            CatalogApiClient catalogApiClient, ClassesProperties classesProperties) {
        return new GetClassDetailViewUseCaseImpl(catalogApiClient, classesProperties.getDisplayZone());
    }

    @Bean
    public QuotePhysicalPurchaseUseCase quotePhysicalPurchaseUseCase(
            PhysicalPurchaseApiClient purchaseApiClient, CatalogApiClient catalogApiClient,
            ClassesProperties classesProperties) {
        return new QuotePhysicalPurchaseUseCaseImpl(
                purchaseApiClient, catalogApiClient, classesProperties.getDisplayZone());
    }
}
