package com.menta.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

class SecurityConfigTest {

    private AnnotationConfigApplicationContext context;
    private AnnotationConfigWebApplicationContext webContext;

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
        if (webContext != null) {
            webContext.close();
        }
    }

    /**
     * A minimal, request-dispatching Spring Security context: the real filter chain runs, but
     * no controller is mapped — a request the chain <em>permits</em> reaches an unmatched
     * handler and gets {@code 404}, while a request the chain <em>denies</em> never reaches that
     * far and gets {@code 401}/{@code 403}. That distinction is exactly what lets these tests
     * regression-test {@code SecurityConfig}'s matchers without standing up real controllers.
     */
    private MockMvc buildSecurityFilterChainMockMvc() {
        webContext = new AnnotationConfigWebApplicationContext();
        webContext.setServletContext(new MockServletContext());
        webContext.register(SecurityConfig.class, TestSupportConfig.class);
        webContext.refresh();
        return MockMvcBuilders.webAppContextSetup(webContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    }

    /**
     * US-BILLING-011, #130 (A7). Before this rule existed, this path fell through to
     * {@code anyRequest().access(roleAuthorizationManager)}, which grants unmapped paths by
     * default (see the class Javadoc) — an unauthenticated caller would NOT have been rejected
     * here.
     */
    @Test
    void an_unauthenticated_delete_of_the_own_subscription_cancellation_route_is_rejected() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(delete("/api/v1/billing/subscriptions/me"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * US-BILLING-004, #32. Before this rule existed, both GET routes fell through to
     * {@code anyRequest().access(roleAuthorizationManager)}, which grants unmapped paths by
     * default — an unauthenticated caller reached the controller with a {@code null}
     * {@code Authentication} instead of being rejected here, surfaced end-to-end by
     * {@code SubscriptionStatusIntegrationTest}.
     */
    @Test
    void an_unauthenticated_get_of_the_current_subscription_route_is_rejected() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(get("/api/v1/billing/subscriptions/me"))
            .andExpect(status().isUnauthorized());
    }

    /** Same rule, the history path — a different endpoint than {@code /me}, same matcher. */
    @Test
    void an_unauthenticated_get_of_the_subscription_history_route_is_rejected() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(get("/api/v1/billing/subscriptions/me/history"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * #39, US-PHYSICAL-002, design C7. Before this matcher existed, this path fell through to
     * {@code anyRequest().access(roleAuthorizationManager)}, which grants unmapped paths by
     * default (see the class Javadoc) — an anonymous caller would NOT have been rejected here.
     */
    @Test
    void an_unauthenticated_get_of_the_self_attendance_history_route_is_rejected() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(get("/api/v1/physical/attendance/me").param("month", "2026-09"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * #39, US-PHYSICAL-002 P2, design C7. Before this matcher existed, this path fell through to
     * {@code anyRequest().access(roleAuthorizationManager)}, which grants unmapped paths by
     * default (see the class Javadoc) — an anonymous caller would NOT have been rejected here.
     */
    @Test
    void an_unauthenticated_get_of_the_elevated_attendance_history_route_is_rejected() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(get(
            "/api/v1/admin/physical/attendance/00000000-0000-0000-0000-000000000001"
        ).param("month", "2026-09"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * #39, US-PHYSICAL-002 P2, design C7/R8. Confirms the elevated matcher's role set does NOT
     * include STUDENT — a {@code hasAnyRole("ADMIN","INSTRUCTOR")} rule rejects it with
     * {@code 403}, never a {@code 401} (the caller IS authenticated) nor a silent pass-through.
     */
    @Test
    void an_authenticated_student_get_of_the_elevated_attendance_history_route_is_forbidden() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(get(
            "/api/v1/admin/physical/attendance/00000000-0000-0000-0000-000000000001"
        ).param("month", "2026-09").with(user("student").roles("STUDENT")))
            .andExpect(status().isForbidden());
    }

    /**
     * #39, US-PHYSICAL-002 P2, design C7 — the ordering regression this matcher exists to
     * prevent: pins that {@code INSTRUCTOR} reaches the (unmapped) dispatcher, NOT {@code 403}.
     * A future reorder that drops back to the generic {@code /api/v1/admin/**} →
     * {@code hasRole("ADMIN")} rule (which would 403 every instructor) fails THIS test instead
     * of silently locking out every instructor in production.
     */
    @Test
    void an_authenticated_instructor_get_of_the_elevated_attendance_history_route_passes_the_security_layer()
        throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(get(
            "/api/v1/admin/physical/attendance/00000000-0000-0000-0000-000000000001"
        ).param("month", "2026-09").with(user("instructor").roles("INSTRUCTOR")))
            .andExpect(status().isNotFound());
    }

    /** Same ordering guarantee, confirmed from the other admitted role: ADMIN also passes through. */
    @Test
    void an_authenticated_admin_get_of_the_elevated_attendance_history_route_passes_the_security_layer()
        throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(get(
            "/api/v1/admin/physical/attendance/00000000-0000-0000-0000-000000000001"
        ).param("month", "2026-09").with(user("admin").roles("ADMIN")))
            .andExpect(status().isNotFound());
    }

    /**
     * Confirms the admin cancellation route needs no new matcher: the existing generic
     * {@code /api/v1/admin/**} → {@code hasRole("ADMIN")} rule already rejects a non-admin.
     */
    @Test
    void an_authenticated_non_admin_delete_of_the_admin_cancellation_route_is_forbidden() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(delete(
            "/api/v1/admin/billing/subscriptions/00000000-0000-0000-0000-000000000001"
        ).with(user("student").roles("STUDENT")))
            .andExpect(status().isForbidden());
    }

    /** The admin route is not denied outright for an actual admin — it reaches the (unmapped) dispatcher instead. */
    @Test
    void an_authenticated_admin_delete_of_the_admin_cancellation_route_passes_the_security_layer() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(delete(
            "/api/v1/admin/billing/subscriptions/00000000-0000-0000-0000-000000000001"
        ).with(user("admin").roles("ADMIN")))
            .andExpect(status().isNotFound());
    }

    /**
     * US-BILLING-012 (#131), Phase 4 — confirms the trial-grant route also needs no new matcher:
     * it is covered by the same generic {@code /api/v1/admin/**} → {@code hasRole("ADMIN")} rule.
     */
    @Test
    void an_authenticated_non_admin_post_of_the_trial_grant_route_is_forbidden() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(post("/api/v1/admin/billing/subscriptions/trial")
            .with(user("student").roles("STUDENT")))
            .andExpect(status().isForbidden());
    }

    /** The trial-grant route is not denied outright for an actual admin — same shape as cancellation above. */
    @Test
    void an_authenticated_admin_post_of_the_trial_grant_route_passes_the_security_layer() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(post("/api/v1/admin/billing/subscriptions/trial")
            .with(user("admin").roles("ADMIN")))
            .andExpect(status().isNotFound());
    }

    /**
     * #52, US-VIRTUAL-005 (Slice 1). Before this matcher existed, {@code
     * /api/v1/virtual/lessons/**} was granted by {@code permitAll()} with no
     * {@code HttpMethod} restriction (see the class Javadoc), so this write
     * endpoint was anonymously reachable.
     */
    @Test
    void an_unauthenticated_put_of_the_lesson_progress_route_is_rejected() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(put("/api/v1/virtual/lessons/00000000-0000-0000-0000-000000000001/progress"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * #52, US-VIRTUAL-005 (Slice 1). Same {@code permitAll()} wildcard as the
     * PUT case above also covered this GET before this matcher existed.
     */
    @Test
    void an_unauthenticated_get_of_the_lesson_progress_route_is_rejected() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(get("/api/v1/virtual/lessons/00000000-0000-0000-0000-000000000001/progress"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * #52, US-VIRTUAL-005 (Slice 1). Same {@code permitAll()} wildcard as the
     * PUT case above also covered this POST before this matcher existed.
     */
    @Test
    void an_unauthenticated_post_of_the_lesson_complete_route_is_rejected() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(post("/api/v1/virtual/lessons/00000000-0000-0000-0000-000000000001/complete"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * #52, US-VIRTUAL-005 (Slice 1). Before this matcher existed, this path
     * had no matcher at all and fell through to {@code
     * anyRequest().access(roleAuthorizationManager)}, whose own fall-through
     * semantics grant unmapped paths regardless of authentication.
     */
    @Test
    void an_unauthenticated_get_of_the_course_progress_route_is_rejected() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(get("/api/v1/virtual/courses/00000000-0000-0000-0000-000000000001/progress"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * #41, US-PHYSICAL-004. Before this matcher existed, this path had no
     * matcher at all and fell through to {@code
     * anyRequest().access(roleAuthorizationManager)}, whose own fall-through
     * semantics grant unmapped paths regardless of authentication (see this
     * class's other tests for the same shape, e.g. #32's GET
     * /subscriptions/me above) — demonstrated for real here rather than
     * assumed, per the #32 precedent where the documentation had drifted
     * from the actual filter-chain behaviour.
     */
    @Test
    void an_unauthenticated_post_of_the_physical_purchases_route_is_rejected() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(post("/api/v1/billing/physical/purchases"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * #31, US-BILLING-003 (design C8). Before this matcher existed, this path had no matcher at
     * all and fell through to {@code anyRequest().access(roleAuthorizationManager)}, whose own
     * fall-through semantics grant unmapped paths regardless of authentication.
     */
    @Test
    void an_unauthenticated_post_of_the_proof_upload_route_is_rejected() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(post("/api/v1/billing/payments/00000000-0000-0000-0000-000000000001/proof"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * Regression (design C8): the new method-scoped {@code .../*\/proof} matcher must never
     * shadow the pre-existing {@code permitAll} webhook path. A 404 (unmapped dispatcher reached)
     * proves the request passed the security layer, unlike the 401 above.
     */
    @Test
    void the_mercadopago_webhook_route_stays_permit_all() throws Exception {
        MockMvc mockMvc = buildSecurityFilterChainMockMvc();

        mockMvc.perform(post("/api/v1/billing/payments/mercadopago/webhook"))
            .andExpect(status().isNotFound());
    }

    @Test
    void constructs_the_configuration_instance() {
        assertThat(new SecurityConfig()).isNotNull();
    }

    @Test
    void wires_the_role_authorization_manager_bean() {
        SecurityConfig config = new SecurityConfig();

        RoleAuthorizationManager manager = config.roleAuthorizationManager();

        assertThat(manager).isNotNull();
    }

    @Test
    void wires_the_user_details_service_bean() {
        SecurityConfig config = new SecurityConfig();

        UserDetailsService service = config.userDetailsService();

        assertThat(service).isInstanceOf(InMemoryUserDetailsManager.class);
    }

    @Test
    void builds_the_security_filter_chain_bean_via_a_minimal_spring_security_context() {
        context = new AnnotationConfigApplicationContext(SecurityConfig.class, TestSupportConfig.class);

        SecurityFilterChain chain = context.getBean(SecurityFilterChain.class);

        assertThat(chain).isNotNull();
        assertThat(chain.getFilters()).isNotEmpty();
    }

    @Configuration
    static class TestSupportConfig {

        /**
         * A no-op-authentication pass-through: it never sets a {@code SecurityContext}, so a
         * request with no explicit {@code with(user(...))} reaches the authorization rules as
         * anonymous — exactly {@code JwtAuthenticationFilter}'s real behavior for a request with
         * no Bearer token. Without forwarding to {@code chain}, the mock's default no-op {@code
         * doFilter} would silently swallow every request instead of letting it reach the rest of
         * the chain.
         */
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter()
            throws jakarta.servlet.ServletException, java.io.IOException {
            JwtAuthenticationFilter filter = mock(JwtAuthenticationFilter.class);
            doAnswer(invocation -> {
                ServletRequest request = invocation.getArgument(0);
                ServletResponse response = invocation.getArgument(1);
                FilterChain chain = invocation.getArgument(2);
                chain.doFilter(request, response);
                return null;
            }).when(filter).doFilter(
                ArgumentMatchers.any(ServletRequest.class), ArgumentMatchers.any(ServletResponse.class),
                ArgumentMatchers.any(FilterChain.class)
            );
            return filter;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        /**
         * Spring Security's string-pattern {@code requestMatchers(...)} resolves to {@code
         * MvcRequestMatcher} once it detects a servlet {@code WebApplicationContext} (needed
         * here for {@code MockMvcBuilders.webAppContextSetup}), and that requires this bean —
         * normally supplied by {@code @EnableWebMvc}, which this minimal context does not use.
         */
        @Bean
        org.springframework.web.servlet.handler.HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
            return new org.springframework.web.servlet.handler.HandlerMappingIntrospector();
        }
    }
}
