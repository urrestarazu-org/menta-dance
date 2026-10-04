package com.menta.bff.infrastructure.config;

import com.menta.bff.application.port.out.AccessTokenClaimsReader;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.SessionTokenRepository;
import com.menta.bff.application.port.out.VirtualApiClient;
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
}
