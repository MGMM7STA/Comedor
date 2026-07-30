package com.upeu.comedorupeu.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/login-error")
    public String loginError(org.springframework.ui.Model model) {
        model.addAttribute("error", true);
        return "login";
    }

    @GetMapping("/login-bloqueado")
    public String loginBloqueado(org.springframework.ui.Model model) {
        model.addAttribute("bloqueado", true);
        return "login";
    }
}
