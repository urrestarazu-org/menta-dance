package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.infrastructure.config.ContactProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Legal and contact pages (#650) — anonymous-reachable, static text.
 *
 * <p>Each page lives at one exact path (the security rules list them one by one). Only the contact page
 * has data: its details come from {@link ContactProperties}, never from the templates.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class LegalController {

    private final ContactProperties contactProperties;

    @GetMapping("/terminos-condiciones")
    public String terms() {
        return "legal/terminos-condiciones";
    }

    @GetMapping("/politica-privacidad")
    public String privacy() {
        return "legal/politica-privacidad";
    }

    @GetMapping("/politica-cookies")
    public String cookies() {
        return "legal/politica-cookies";
    }

    @GetMapping("/politica-devolucion")
    public String refunds() {
        return "legal/politica-devolucion";
    }

    @GetMapping("/propiedad-intelectual")
    public String intellectualProperty() {
        return "legal/propiedad-intelectual";
    }

    @GetMapping("/accesibilidad")
    public String accessibility() {
        return "legal/accesibilidad";
    }

    @GetMapping("/contacto")
    public String contact(Model model) {
        model.addAttribute("contact", contactProperties);
        return "legal/contacto";
    }
}
