package com.upeu.comedorupeu.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String datosNoValidos(HttpServletRequest peticion, RedirectAttributes flash) {
        flash.addFlashAttribute("error",
                "No se pudo guardar: revisa que los textos no sean demasiado largos y que los "
                        + "datos que no se pueden repetir (código, DNI, correo) no estén ya registrados.");
        return "redirect:" + volverA(peticion);
    }

    private String volverA(HttpServletRequest peticion) {
        String anterior = peticion.getHeader("Referer");
        if (anterior != null) {
            try {
                String ruta = java.net.URI.create(anterior).getPath();
                if (ruta != null && ruta.startsWith("/")) return ruta;
            } catch (IllegalArgumentException e) {

            }
        }
        return "/";
    }
}
