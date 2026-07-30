package com.upeu.comedorupeu.config;

import com.upeu.comedorupeu.repository.EventoEspecialRepository;
import com.upeu.comedorupeu.repository.IncidenciaRepository;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class NotificacionesAdvice {

    private final EventoEspecialRepository eventoRepo;
    private final IncidenciaRepository incidenciaRepo;

    public NotificacionesAdvice(EventoEspecialRepository eventoRepo, IncidenciaRepository incidenciaRepo) {
        this.eventoRepo = eventoRepo;
        this.incidenciaRepo = incidenciaRepo;
    }

    @ModelAttribute
    public void agregarNotificaciones(Model model) {
        model.addAttribute("notifEventos", eventoRepo.countByEstado("PENDIENTE"));
        model.addAttribute("notifPeticiones", incidenciaRepo.contarPendientes());

        model.addAttribute("relojServidorMs", System.currentTimeMillis());
    }
}
