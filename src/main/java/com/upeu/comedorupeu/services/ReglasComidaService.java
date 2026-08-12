package com.upeu.comedorupeu.services;

import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.repository.MarcacionRepository;
import com.upeu.comedorupeu.repository.SolicitudExtemporaneaRepository;
import com.upeu.comedorupeu.repository.TurnoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ReglasComidaService {

    private final MarcacionRepository marcacionRepo;
    private final SolicitudExtemporaneaRepository solicitudRepo;
    private final TurnoRepository turnoRepo;
    private final JustificacionService justificacionService;

    public ReglasComidaService(MarcacionRepository marcacionRepo,
                               SolicitudExtemporaneaRepository solicitudRepo,
                               TurnoRepository turnoRepo,
                               JustificacionService justificacionService) {
        this.marcacionRepo = marcacionRepo;
        this.solicitudRepo = solicitudRepo;
        this.turnoRepo = turnoRepo;
        this.justificacionService = justificacionService;
    }

    public boolean yaIngreso(Residente residente, LocalDate fecha, String tipoComida) {
        return turnoRepo.findByFechaAndTipo(fecha, tipoComida)
                .map(t -> !marcacionRepo.consumosVigentes(residente.getIdResidente(), t.getIdTurno()).isEmpty())
                .orElse(false);
    }

    public boolean yaTieneReserva(Residente residente, LocalDate fecha, String tipoComida) {
        return tieneReservaEn(residente, fecha, tipoComida, "PENDIENTE")
                || tieneReservaEn(residente, fecha, tipoComida, "ATENDIDA");
    }

    public boolean yaJustificado(Residente residente, LocalDate fecha, String tipoComida) {
        return justificacionService.buscar(residente, fecha, tipoComida).isPresent();
    }

    private boolean tieneReservaEn(Residente residente, LocalDate fecha, String tipoComida, String estado) {
        return solicitudRepo.findFirstByResidenteIdResidenteAndFechaAndTipoComidaAndEstado(
                residente.getIdResidente(), fecha, tipoComida, estado).isPresent();
    }
}
