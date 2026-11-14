package com.menta.bff.infrastructure.config;

import com.menta.bff.application.port.out.AccessTokenClaimsReader;
import com.menta.bff.application.port.out.AdminUsersApiClient;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.application.port.out.InstructorDisplayNameSource;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import com.menta.bff.application.port.out.PhysicalReceptionApiClient;
import com.menta.bff.application.port.out.PhysicalSessionsApiClient;
import com.menta.bff.application.port.out.SessionTokenRepository;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import com.menta.bff.application.port.out.VirtualApiClient;
import com.menta.bff.application.usecase.AccountActivationUseCase;
import com.menta.bff.application.usecase.AccountActivationUseCaseImpl;
import com.menta.bff.application.usecase.BatchCreateCourseSessionsUseCase;
import com.menta.bff.application.usecase.BatchCreateCourseSessionsUseCaseImpl;
import com.menta.bff.application.usecase.ChangeUserRoleUseCase;
import com.menta.bff.application.usecase.ChangeUserRoleUseCaseImpl;
import com.menta.bff.application.usecase.CreateCourseSessionUseCase;
import com.menta.bff.application.usecase.CreateCourseSessionUseCaseImpl;
import com.menta.bff.application.usecase.CreateManagedCourseUseCase;
import com.menta.bff.application.usecase.CreateManagedCourseUseCaseImpl;
import com.menta.bff.application.usecase.CreateUserUseCase;
import com.menta.bff.application.usecase.CreateUserUseCaseImpl;
import com.menta.bff.application.usecase.DescribeSessionConflictsUseCase;
import com.menta.bff.application.usecase.DescribeSessionConflictsUseCaseImpl;
import com.menta.bff.application.usecase.GetAssignedSessionsUseCase;
import com.menta.bff.application.usecase.GetAssignedSessionsUseCaseImpl;
import com.menta.bff.application.usecase.GetCatalogViewUseCase;
import com.menta.bff.application.usecase.GetCatalogViewUseCaseImpl;
import com.menta.bff.application.usecase.GetClassDetailViewUseCase;
import com.menta.bff.application.usecase.GetClassDetailViewUseCaseImpl;
import com.menta.bff.application.usecase.GetClassesViewUseCase;
import com.menta.bff.application.usecase.GetClassesViewUseCaseImpl;
import com.menta.bff.application.usecase.GetCourseDetailViewUseCase;
import com.menta.bff.application.usecase.GetCourseDetailViewUseCaseImpl;
import com.menta.bff.application.usecase.GetCourseSessionUseCase;
import com.menta.bff.application.usecase.GetCourseSessionUseCaseImpl;
import com.menta.bff.application.usecase.GetLessonViewUseCase;
import com.menta.bff.application.usecase.GetLessonViewUseCaseImpl;
import com.menta.bff.application.usecase.GetManagedCourseUseCase;
import com.menta.bff.application.usecase.GetManagedCourseUseCaseImpl;
import com.menta.bff.application.usecase.GetPaymentProgressUseCase;
import com.menta.bff.application.usecase.GetPaymentProgressUseCaseImpl;
import com.menta.bff.application.usecase.GetPlansViewUseCase;
import com.menta.bff.application.usecase.GetPlansViewUseCaseImpl;
import com.menta.bff.application.usecase.GetSessionRosterUseCase;
import com.menta.bff.application.usecase.GetSessionRosterUseCaseImpl;
import com.menta.bff.application.usecase.GetValidAccessTokenUseCase;
import com.menta.bff.application.usecase.GetValidAccessTokenUseCaseImpl;
import com.menta.bff.application.usecase.ListCourseSessionsUseCase;
import com.menta.bff.application.usecase.ListCourseSessionsUseCaseImpl;
import com.menta.bff.application.usecase.ListInstructorsUseCase;
import com.menta.bff.application.usecase.ListInstructorsUseCaseImpl;
import com.menta.bff.application.usecase.ListManagedCoursesUseCase;
import com.menta.bff.application.usecase.ListManagedCoursesUseCaseImpl;
import com.menta.bff.application.usecase.ListTodaySessionsUseCase;
import com.menta.bff.application.usecase.ListTodaySessionsUseCaseImpl;
import com.menta.bff.application.usecase.ListUsersUseCase;
import com.menta.bff.application.usecase.ListUsersUseCaseImpl;
import com.menta.bff.application.usecase.LoginUseCase;
import com.menta.bff.application.usecase.LoginUseCaseImpl;
import com.menta.bff.application.usecase.LogoutUseCase;
import com.menta.bff.application.usecase.LogoutUseCaseImpl;
import com.menta.bff.application.usecase.PayPhysicalPurchaseByTransferUseCase;
import com.menta.bff.application.usecase.PayPhysicalPurchaseByTransferUseCaseImpl;
import com.menta.bff.application.usecase.PayPhysicalPurchaseUseCase;
import com.menta.bff.application.usecase.PayPhysicalPurchaseUseCaseImpl;
import com.menta.bff.application.usecase.QuotePhysicalPurchaseUseCase;
import com.menta.bff.application.usecase.QuotePhysicalPurchaseUseCaseImpl;
import com.menta.bff.application.usecase.RegisterAccountUseCase;
import com.menta.bff.application.usecase.RegisterAccountUseCaseImpl;
import com.menta.bff.application.usecase.RegisterManualCheckInUseCase;
import com.menta.bff.application.usecase.RegisterManualCheckInUseCaseImpl;
import com.menta.bff.application.usecase.RequestPasswordResetUseCase;
import com.menta.bff.application.usecase.RequestPasswordResetUseCaseImpl;
import com.menta.bff.application.usecase.ResendActivationUseCase;
import com.menta.bff.application.usecase.ResendActivationUseCaseImpl;
import com.menta.bff.application.usecase.ResetPasswordUseCase;
import com.menta.bff.application.usecase.ResetPasswordUseCaseImpl;
import com.menta.bff.application.usecase.ResolveOwnInstructorUseCase;
import com.menta.bff.application.usecase.ResolveOwnInstructorUseCaseImpl;
import com.menta.bff.application.usecase.ResolveSessionWindowUseCase;
import com.menta.bff.application.usecase.ResolveSessionWindowUseCaseImpl;
import com.menta.bff.application.usecase.SubscribeToPlanUseCase;
import com.menta.bff.application.usecase.SubscribeToPlanUseCaseImpl;
import com.menta.bff.application.usecase.UpdateCourseSessionUseCase;
import com.menta.bff.application.usecase.UpdateCourseSessionUseCaseImpl;
import com.menta.bff.application.usecase.UpdateManagedCourseUseCase;
import com.menta.bff.application.usecase.UpdateManagedCourseUseCaseImpl;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
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
            SessionTokenRepository sessionTokenRepository,
            GetValidAccessTokenUseCase getValidAccessTokenUseCase
    ) {
        return new LogoutUseCaseImpl(authApiClient, sessionTokenRepository, getValidAccessTokenUseCase);
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
    public GetPlansViewUseCase getPlansViewUseCase(
            BillingApiClient billingApiClient, SubscriptionApiClient subscriptionApiClient,
            ClassesProperties classesProperties) {
        return new GetPlansViewUseCaseImpl(billingApiClient, subscriptionApiClient,
                classesProperties.getDisplayZone(), () -> UUID.randomUUID().toString());
    }

    @Bean
    public GetCatalogViewUseCase getCatalogViewUseCase(CatalogApiClient catalogApiClient) {
        return new GetCatalogViewUseCaseImpl(catalogApiClient);
    }

    @Bean
    public GetClassesViewUseCase getClassesViewUseCase(
            CatalogApiClient catalogApiClient, ClassesProperties classesProperties) {
        return new GetClassesViewUseCaseImpl(catalogApiClient, classesProperties.getDisplayZone());
    }

    @Bean
    public GetClassDetailViewUseCase getClassDetailViewUseCase(
            CatalogApiClient catalogApiClient, BillingApiClient billingApiClient,
            PhysicalSessionsApiClient physicalSessionsApiClient, ClassesProperties classesProperties) {
        return new GetClassDetailViewUseCaseImpl(
                catalogApiClient, billingApiClient, physicalSessionsApiClient, classesProperties.getDisplayZone());
    }

    @Bean
    public QuotePhysicalPurchaseUseCase quotePhysicalPurchaseUseCase(
            PhysicalPurchaseApiClient purchaseApiClient, CatalogApiClient catalogApiClient,
            ClassesProperties classesProperties) {
        return new QuotePhysicalPurchaseUseCaseImpl(purchaseApiClient, catalogApiClient,
                classesProperties.getDisplayZone(), () -> UUID.randomUUID().toString());
    }

    @Bean
    public PayPhysicalPurchaseUseCase payPhysicalPurchaseUseCase(PhysicalPurchaseApiClient purchaseApiClient) {
        return new PayPhysicalPurchaseUseCaseImpl(purchaseApiClient);
    }

    @Bean
    public SubscribeToPlanUseCase subscribeToPlanUseCase(SubscriptionApiClient subscriptionApiClient) {
        return new SubscribeToPlanUseCaseImpl(subscriptionApiClient);
    }

    @Bean
    public PayPhysicalPurchaseByTransferUseCase payPhysicalPurchaseByTransferUseCase(
            PhysicalPurchaseApiClient purchaseApiClient) {
        return new PayPhysicalPurchaseByTransferUseCaseImpl(purchaseApiClient);
    }

    @Bean
    public GetPaymentProgressUseCase getPaymentProgressUseCase(PhysicalPurchaseApiClient purchaseApiClient) {
        return new GetPaymentProgressUseCaseImpl(purchaseApiClient);
    }

    @Bean
    public GetAssignedSessionsUseCase getAssignedSessionsUseCase(
            PhysicalSessionsApiClient physicalSessionsApiClient, ObjectProvider<Clock> clockProvider,
            ClassesProperties classesProperties) {
        return new GetAssignedSessionsUseCaseImpl(physicalSessionsApiClient,
                clockProvider.getIfAvailable(Clock::systemUTC), classesProperties.getDisplayZone());
    }

    @Bean
    public ListUsersUseCase listUsersUseCase(AdminUsersApiClient adminUsersApiClient) {
        return new ListUsersUseCaseImpl(adminUsersApiClient);
    }

    @Bean
    public CreateUserUseCase createUserUseCase(AdminUsersApiClient adminUsersApiClient) {
        return new CreateUserUseCaseImpl(adminUsersApiClient);
    }

    @Bean
    public ChangeUserRoleUseCase changeUserRoleUseCase(
            AdminUsersApiClient adminUsersApiClient, AccessTokenClaimsReader accessTokenClaimsReader) {
        return new ChangeUserRoleUseCaseImpl(adminUsersApiClient, accessTokenClaimsReader);
    }

    /**
     * Agenda of the reception screen (#373). "Today" is the local day in the academy's display zone; the
     * {@link Clock} is injectable so tests can fix the instant, and falls back to the system UTC clock because
     * only the instant matters here (the use case applies the zone itself).
     */
    @Bean
    public ListTodaySessionsUseCase listTodaySessionsUseCase(
            PhysicalReceptionApiClient receptionApiClient, ObjectProvider<Clock> clockProvider,
            ClassesProperties classesProperties) {
        return new ListTodaySessionsUseCaseImpl(receptionApiClient,
                clockProvider.getIfAvailable(Clock::systemUTC), classesProperties.getDisplayZone());
    }

    @Bean
    public GetSessionRosterUseCase getSessionRosterUseCase(
            PhysicalReceptionApiClient receptionApiClient, ClassesProperties classesProperties) {
        return new GetSessionRosterUseCaseImpl(receptionApiClient, classesProperties.getDisplayZone());
    }

    @Bean
    public RegisterManualCheckInUseCase registerManualCheckInUseCase(
            PhysicalReceptionApiClient receptionApiClient, ClassesProperties classesProperties) {
        return new RegisterManualCheckInUseCaseImpl(receptionApiClient, classesProperties.getDisplayZone());
    }

    /**
     * Management of physical courses and sessions (#368). Every use case converts between the academy's display
     * zone and UTC, which is all the API speaks (ADR-0048).
     */
    @Bean
    public ListManagedCoursesUseCase listManagedCoursesUseCase(
            PhysicalCourseManagementApiClient courseApiClient, ClassesProperties classesProperties) {
        return new ListManagedCoursesUseCaseImpl(courseApiClient, classesProperties.getDisplayZone());
    }

    @Bean
    public GetManagedCourseUseCase getManagedCourseUseCase(
            PhysicalCourseManagementApiClient courseApiClient, ClassesProperties classesProperties) {
        return new GetManagedCourseUseCaseImpl(courseApiClient, classesProperties.getDisplayZone());
    }

    @Bean
    public ListInstructorsUseCase listInstructorsUseCase(AdminUsersApiClient adminUsersApiClient) {
        return new ListInstructorsUseCaseImpl(adminUsersApiClient);
    }

    @Bean
    public ResolveOwnInstructorUseCase resolveOwnInstructorUseCase(
            AccessTokenClaimsReader accessTokenClaimsReader, InstructorDisplayNameSource nameSource) {
        return new ResolveOwnInstructorUseCaseImpl(accessTokenClaimsReader, nameSource);
    }

    @Bean
    public CreateManagedCourseUseCase createManagedCourseUseCase(
            PhysicalCourseManagementApiClient courseApiClient, ClassesProperties classesProperties) {
        return new CreateManagedCourseUseCaseImpl(courseApiClient, classesProperties.getDisplayZone());
    }

    @Bean
    public UpdateManagedCourseUseCase updateManagedCourseUseCase(
            PhysicalCourseManagementApiClient courseApiClient, ClassesProperties classesProperties) {
        return new UpdateManagedCourseUseCaseImpl(courseApiClient, classesProperties.getDisplayZone());
    }

    @Bean
    public ListCourseSessionsUseCase listCourseSessionsUseCase(
            PhysicalCourseManagementApiClient courseApiClient, ClassesProperties classesProperties) {
        return new ListCourseSessionsUseCaseImpl(courseApiClient, classesProperties.getDisplayZone());
    }

    @Bean
    public CreateCourseSessionUseCase createCourseSessionUseCase(
            PhysicalCourseManagementApiClient courseApiClient, ClassesProperties classesProperties) {
        return new CreateCourseSessionUseCaseImpl(courseApiClient, classesProperties.getDisplayZone());
    }

    @Bean
    public BatchCreateCourseSessionsUseCase batchCreateCourseSessionsUseCase(
            PhysicalCourseManagementApiClient courseApiClient, ClassesProperties classesProperties) {
        return new BatchCreateCourseSessionsUseCaseImpl(courseApiClient, classesProperties.getDisplayZone());
    }

    @Bean
    public GetCourseSessionUseCase getCourseSessionUseCase(
            PhysicalCourseManagementApiClient courseApiClient, ClassesProperties classesProperties) {
        return new GetCourseSessionUseCaseImpl(courseApiClient, classesProperties.getDisplayZone());
    }

    @Bean
    public UpdateCourseSessionUseCase updateCourseSessionUseCase(
            PhysicalCourseManagementApiClient courseApiClient, ClassesProperties classesProperties) {
        return new UpdateCourseSessionUseCaseImpl(courseApiClient, classesProperties.getDisplayZone());
    }

    /**
     * The days of a course's sessions list (#368). "Today" is the local day in the academy's display zone; the
     * {@link Clock} is injectable so tests can fix the instant.
     */
    @Bean
    public ResolveSessionWindowUseCase resolveSessionWindowUseCase(
            ObjectProvider<Clock> clockProvider, ClassesProperties classesProperties) {
        return new ResolveSessionWindowUseCaseImpl(
                clockProvider.getIfAvailable(Clock::systemUTC), classesProperties.getDisplayZone());
    }

    @Bean
    public DescribeSessionConflictsUseCase describeSessionConflictsUseCase(ClassesProperties classesProperties) {
        return new DescribeSessionConflictsUseCaseImpl(classesProperties.getDisplayZone());
    }
}
