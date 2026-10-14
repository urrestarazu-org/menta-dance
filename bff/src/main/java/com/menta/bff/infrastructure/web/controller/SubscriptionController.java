package com.menta.bff.infrastructure.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Plan subscription entry points (#361).
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
public class SubscriptionController {

    /**
     * Landing point of the anonymous "Suscribirme" link. The route is authenticated, so a visitor
     * is sent to login first and the saved request brings them back here; the redirect then
     * lands them on the plans page. The target is fixed: nothing from the request is used.
     *
     * @return redirect to the plans page
     */
    @GetMapping("/plans/suscribirme")
    public String entry() {
        return "redirect:/plans";
    }
}
