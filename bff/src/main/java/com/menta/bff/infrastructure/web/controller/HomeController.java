package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.usecase.GetCatalogViewUseCase;
import com.menta.bff.application.usecase.GetMembershipLandingViewUseCase;
import com.menta.bff.application.usecase.MembershipLandingView;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Home controller — the anonymous-reachable landing page showing the public
 * course catalog (#358).
 *
 * <p>The optional {@code modality} query parameter ({@code virtual} or
 * {@code physical}) is passed through raw; the use case decides that anything
 * else means "all". An upstream outage does not reach this controller: the use
 * case degrades to a flagged view, so the page always renders.
 * </p>
 *
 * <p>While the temporary membership landing is enabled (#685), {@code /} serves it instead of the catalog. The
 * landing use case bean exists only then, so its absence means "serve the catalog, exactly as before"; the
 * catalog use case is not called while the landing is served. Every other route is untouched.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
public class HomeController {

    private final GetCatalogViewUseCase getCatalogViewUseCase;
    private final GetMembershipLandingViewUseCase membershipLandingViewUseCase;

    /** Serves the catalog home only (the temporary landing is off). */
    public HomeController(GetCatalogViewUseCase getCatalogViewUseCase) {
        this(getCatalogViewUseCase, Optional.empty());
    }

    /**
     * Constructor used by Spring.
     *
     * @param getCatalogViewUseCase        builds the catalog home
     * @param membershipLandingViewUseCase present only while the temporary membership landing is enabled
     */
    @Autowired
    public HomeController(
            GetCatalogViewUseCase getCatalogViewUseCase,
            Optional<GetMembershipLandingViewUseCase> membershipLandingViewUseCase) {
        this.getCatalogViewUseCase = getCatalogViewUseCase;
        this.membershipLandingViewUseCase = membershipLandingViewUseCase.orElse(null);
    }

    @GetMapping("/")
    public String home(@RequestParam(name = "modality", required = false) String modality, Model model) {
        if (membershipLandingViewUseCase != null) {
            MembershipLandingView landing = membershipLandingViewUseCase.execute();
            model.addAttribute("priceRows", landing.priceRows());
            model.addAttribute("salesClosed", landing.salesClosed());
            model.addAttribute("serverNow", landing.serverNow());
            return "membership-landing";
        }
        model.addAttribute("catalog", getCatalogViewUseCase.execute(modality));
        return "index";
    }

    @GetMapping("/health")
    public String health() {
        return "health";
    }
}
