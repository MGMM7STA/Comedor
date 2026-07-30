package com.upeu.comedorupeu.config;

import com.upeu.comedorupeu.services.CambiosService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class CambiosInterceptor implements HandlerInterceptor {

    private final CambiosService cambios;

    public CambiosInterceptor(CambiosService cambios) {
        this.cambios = cambios;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if ("POST".equalsIgnoreCase(request.getMethod()) && response.getStatus() < 400 && ex == null) {
            cambios.tick();
        }
    }
}
