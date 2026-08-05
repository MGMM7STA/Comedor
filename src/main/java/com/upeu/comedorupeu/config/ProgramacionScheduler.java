package com.upeu.comedorupeu.config;

import com.upeu.comedorupeu.models.ConfigTurno;
import com.upeu.comedorupeu.models.ProgramacionHorario;
import com.upeu.comedorupeu.models.PuntoAtencion;
import com.upeu.comedorupeu.models.Turno;
import com.upeu.comedorupeu.repository.ProgramacionHorarioRepository;
import com.upeu.comedorupeu.repository.PuntoAtencionRepository;
import com.upeu.comedorupeu.services.CambiosService;
import com.upeu.comedorupeu.services.TurnoService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
public class ProgramacionScheduler {

    private final ProgramacionHorarioRepository programacionRepo;
    private final PuntoAtencionRepository puntoRepo;
    private final TurnoService turnoService;
    private final com.upeu.comedorupeu.repository.TurnoRepository turnoRepo;
    private final com.upeu.comedorupeu.repository.ConfigTurnoRepository configRepo;
    private final CambiosService cambios;

    public ProgramacionScheduler(ProgramacionHorarioRepository programacionRepo,
                                 PuntoAtencionRepository puntoRepo,
                                 TurnoService turnoService,
                                 com.upeu.comedorupeu.repository.TurnoRepository turnoRepo,
                                 com.upeu.comedorupeu.repository.ConfigTurnoRepository configRepo,
                                 CambiosService cambios) {
        this.programacionRepo = programacionRepo;
        this.puntoRepo = puntoRepo;
        this.turnoService = turnoService;
        this.turnoRepo = turnoRepo;
        this.configRepo = configRepo;
        this.cambios = cambios;
    }

    @Scheduled(fixedRate = 60000, initialDelay = 15000)
    @Transactional
    public void aplicarAgenda() {
        boolean huboCambios = false;
        int hoyDia = LocalDate.now().getDayOfWeek().getValue();
        LocalTime ahora = LocalTime.now();

        huboCambios |= aplicarTurnos(hoyDia, ahora);
        huboCambios |= aplicarPuntos(hoyDia, ahora);

        if (huboCambios) cambios.tick();
    }

    private boolean aplicarTurnos(int hoyDia, LocalTime ahora) {
        boolean cambio = false;
        for (String tipo : TurnoService.TIPOS) {

            LocalTime ini = null, fin = null;
            ProgramacionHorario celda = programacionRepo
                    .findFirstByObjetivoAndTipoTurnoAndDiaSemana("TURNO", tipo, hoyDia)
                    .orElse(null);
            if (celda != null && (celda.getHoraInicio() != null || celda.getHoraFin() != null)) {
                ini = celda.getHoraInicio();
                fin = celda.getHoraFin();
            } else {
                ConfigTurno cfg = configRepo.findByTipo(tipo).orElse(null);
                if (cfg != null && Boolean.TRUE.equals(cfg.getUsarHorario())
                        && (cfg.getHoraInicio() != null || cfg.getHoraFin() != null)) {
                    ini = cfg.getHoraInicio();
                    fin = cfg.getHoraFin();
                }
            }
            if (ini == null && fin == null) continue;

            boolean dentro;
            LocalDateTime limite = null;
            if (ini == null) {
                dentro = false;
                if (ahora.isAfter(fin)) limite = LocalDate.now().atTime(fin);
            } else {
                LocalTime vFin = (fin != null) ? fin : LocalTime.MAX;
                dentro = !ahora.isBefore(ini) && !ahora.isAfter(vFin);
                if (dentro) limite = LocalDate.now().atTime(ini);
                else if (ahora.isAfter(vFin)) limite = LocalDate.now().atTime(vFin);
            }
            if (limite == null) continue;

            Turno turno = turnoRepo.findByFechaAndTipo(LocalDate.now(), tipo).orElse(null);
            if (turno == null) continue;

            if (turno.getUltimaAccionManual() != null && !turno.getUltimaAccionManual().isBefore(limite)) continue;

            if (dentro && !"ACTIVO".equals(turno.getEstado())) {
                turno.setEstado("ACTIVO");
                turnoRepo.save(turno);
                cambio = true;
            } else if (!dentro && "ACTIVO".equals(turno.getEstado())) {

                turno.setEstado("CERRADO");
                turnoRepo.save(turno);
                cambio = true;
            }
        }
        return cambio;
    }

    private boolean aplicarPuntos(int hoyDia, LocalTime ahora) {
        boolean cambio = false;
        for (PuntoAtencion punto : puntoRepo.vigentes()) {

            if (punto.isOperativo() && turnoService.selloEsDeOtroDia(punto.getUltimaAccionManual())) {
                punto.setActivo(false);
                punto.setTurnoManual(null);
                puntoRepo.save(punto);
                cambio = true;
                continue;
            }

            List<ProgramacionHorario> celdas = programacionRepo
                    .findByObjetivoAndPuntoIdPuntoAndDiaSemana("TURNO_PUNTO", punto.getIdPunto(), hoyDia)
                    .stream()
                    .filter(c -> !Boolean.FALSE.equals(c.getActivo()))
                    .filter(c -> c.getHoraInicio() != null || c.getHoraFin() != null)
                    .toList();
            if (celdas.isEmpty()) continue;

            ProgramacionHorario actual = null;
            LocalTime ultimoFinPasado = null;
            for (ProgramacionHorario c : celdas) {
                LocalTime vFin = (c.getHoraFin() != null) ? c.getHoraFin() : LocalTime.MAX;
                if (c.getHoraInicio() != null && !ahora.isBefore(c.getHoraInicio()) && !ahora.isAfter(vFin)) {
                    actual = c;
                } else if (c.getHoraFin() != null && ahora.isAfter(c.getHoraFin())
                        && (ultimoFinPasado == null || c.getHoraFin().isAfter(ultimoFinPasado))) {
                    ultimoFinPasado = c.getHoraFin();
                }
            }

            LocalDateTime limite = null;
            boolean debeAbrir = (actual != null);
            if (debeAbrir) {
                limite = LocalDate.now().atTime(actual.getHoraInicio());
            } else if (ultimoFinPasado != null) {
                limite = LocalDate.now().atTime(ultimoFinPasado);
            }
            if (limite == null) continue;

            if (punto.getUltimaAccionManual() != null && !punto.getUltimaAccionManual().isBefore(limite)) continue;

            if (debeAbrir && !punto.isOperativo()) {

                punto.setModo("MANUAL");
                punto.setActivo(true);
                punto.setHoraInicio(actual.getHoraInicio());
                punto.setHoraFin(actual.getHoraFin());
                if (actual.getCajero() != null) punto.setCajero(actual.getCajero());
                puntoRepo.save(punto);
                cambio = true;
            } else if (!debeAbrir && Boolean.TRUE.equals(punto.getActivo())) {

                punto.setModo("MANUAL");
                punto.setActivo(false);
                puntoRepo.save(punto);
                cambio = true;
            }
        }
        return cambio;
    }
}
