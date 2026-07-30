package com.upeu.comedorupeu.controller;

import com.upeu.comedorupeu.dto.ReporteIndividual;
import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.repository.ResidenteRepository;
import com.upeu.comedorupeu.services.ReporteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;

@Controller
public class PadresController {

    private final ResidenteRepository residenteRepo;
    private final ReporteService reporteService;
    private final com.upeu.comedorupeu.repository.SolicitudExtemporaneaRepository solicitudRepo;
    private final com.upeu.comedorupeu.repository.EventoEntregaRepository entregaRepo;

    public PadresController(ResidenteRepository residenteRepo, ReporteService reporteService,
                            com.upeu.comedorupeu.repository.SolicitudExtemporaneaRepository solicitudRepo,
                            com.upeu.comedorupeu.repository.EventoEntregaRepository entregaRepo) {
        this.residenteRepo = residenteRepo;
        this.reporteService = reporteService;
        this.solicitudRepo = solicitudRepo;
        this.entregaRepo = entregaRepo;
    }

    @GetMapping("/padres/{token}")
    public String reportePadres(@PathVariable String token,
                                @org.springframework.web.bind.annotation.RequestParam(required = false)
                                @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate desde,
                                @org.springframework.web.bind.annotation.RequestParam(required = false)
                                @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate hasta,
                                Model model) {
        Residente residente = residenteRepo.findByTokenAcceso(token).orElse(null);
        if (residente == null) {
            model.addAttribute("invalido", true);
            return "reportes/padres";
        }
        LocalDate hoy = LocalDate.now();
        if (desde == null) {
            desde = hoy.minusDays(29);
            if (residente.getFechaIngreso() != null && desde.isBefore(residente.getFechaIngreso())) {
                desde = residente.getFechaIngreso();
            }
        }
        if (hasta == null || hasta.isBefore(desde)) hasta = hoy;
        ReporteIndividual rep = reporteService.individual(residente, desde, hasta);
        model.addAttribute("rep", rep);

        model.addAttribute("reservasResidente", solicitudRepo
                .findByResidenteIdResidenteAndFechaBetweenOrderByFechaAsc(residente.getIdResidente(), rep.getDesde(), rep.getHasta()));
        model.addAttribute("entregasResidente", entregaRepo.findByResidenteIdResidente(residente.getIdResidente()).stream()
                .filter(en -> {
                    LocalDate f = en.getEvento().getFechaEvento();
                    return f != null && !f.isBefore(rep.getDesde()) && !f.isAfter(rep.getHasta());
                }).toList());
        return "reportes/padres";
    }
}
