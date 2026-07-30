package com.upeu.comedorupeu.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

    @GetMapping("/")
    public String index(Authentication auth) {
        if (auth == null) return "redirect:/login";

        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean cajero = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CAJERO"));
        boolean preceptor = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PRECEPTOR"));

        if (admin) return "redirect:/admin/puntos";
        if (cajero) return "redirect:/cajero/validar";
        if (preceptor) return "redirect:/preceptor/residentes";
        return "redirect:/login";
    }
}
