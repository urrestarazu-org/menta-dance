package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.PlansView;
import com.menta.bff.application.dto.PlansViewer;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.usecase.GetPlansViewUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Plans listing controller — anonymous-reachable (spec: {@code
 * bff-plans-view}, "Anonymous-reachable plans route").
 *
 * <p>Delegates to {@link GetPlansViewUseCase} with the kind of viewer and, for a student, the
 * caller's access token. A student is {@code ROLE_STUDENT} <em>and</em> a token present: a student
 * whose token is missing fails closed, because the subscription state cannot be verified. The
 * shared plan catalog stays token-less and cached upstream; only the response built for a signed-in
 * viewer is marked {@code Cache-Control: no-store}, so no per-user state can be stored by a cache.
 * </p>
 *
 * <p>No try/catch, mirroring {@link CourseDetailController}: uncaught, {@code
 * @ResponseStatus}-annotated upstream failures resolve through Spring Boot's default {@code
 * /error} → {@code error.html} path, so no upstream problem-detail body ever reaches the
 * browser (design D6).
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class PlansController {

    private static final String STUDENT_AUTHORITY = "ROLE_STUDENT";

    private final GetPlansViewUseCase getPlansViewUseCase;

    @GetMapping("/plans")
    public String plans(Principal principal, HttpServletRequest request, HttpServletResponse response, Model model) {
        PlansViewer viewer = viewer(principal);
        String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
        if (viewer == PlansViewer.STUDENT && (accessToken == null || accessToken.isBlank())) {
            throw new BillingApiClient.ServiceUnavailableException("Student request carries no access token");
        }
        PlansView view = getPlansViewUseCase.execute(viewer, accessToken);
        if (viewer != PlansViewer.ANONYMOUS) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        model.addAttribute("plans", view.plans());
        model.addAttribute("offer", view.offer());
        return "plans";
    }

    private static PlansViewer viewer(Principal principal) {
        if (!(principal instanceof Authentication authentication)) {
            return PlansViewer.ANONYMOUS;
        }
        boolean student = authentication.getAuthorities().stream()
                .anyMatch(authority -> STUDENT_AUTHORITY.equals(authority.getAuthority()));
        return student ? PlansViewer.STUDENT : PlansViewer.OTHER;
    }
}
