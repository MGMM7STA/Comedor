package com.upeu.comedorupeu.controller;

import com.upeu.comedorupeu.services.CambiosService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {

    private final CambiosService cambios;

    public ApiController(CambiosService cambios) {
        this.cambios = cambios;
    }

    @GetMapping("/api/version")
    public String version() {
        int bloqueHorario = java.time.LocalTime.now().toSecondOfDay() / 60;
        return cambios.version() + "." + bloqueHorario;
    }
}
