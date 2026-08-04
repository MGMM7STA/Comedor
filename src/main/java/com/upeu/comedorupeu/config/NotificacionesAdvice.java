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
    private final com.upeu.comedorupeu.services.SemestreService semestreService;
    private final com.upeu.comedorupeu.services.PendientesService pendientesService;

    public NotificacionesAdvice(EventoEspecialRepository eventoRepo, IncidenciaRepository incidenciaRepo,
                                com.upeu.comedorupeu.services.SemestreService semestreService,
                                com.upeu.comedorupeu.services.PendientesService pendientesService) {
        this.eventoRepo = eventoRepo;
        this.incidenciaRepo = incidenciaRepo;
        this.semestreService = semestreService;
        this.pendientesService = pendientesService;
    }

    @ModelAttribute
    public void agregarNotificaciones(Model model) {
        model.addAttribute("notifEventos", eventoRepo.countByEstado("PENDIENTE"));
        model.addAttribute("notifPeticiones", incidenciaRepo.contarPendientes());

        model.addAttribute("relojServidorMs", System.currentTimeMillis());

        model.addAttribute("semestres", semestreService.disponibles());
        model.addAttribute("semestreActual", semestreService.actual());

        model.addAttribute("diasIncidencias", pendientesService.incidencias());
        model.addAttribute("diasReservas", pendientesService.reservas());
        model.addAttribute("diasEventos", pendientesService.eventos());
    }
}
