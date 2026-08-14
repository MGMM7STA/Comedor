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

    private com.upeu.comedorupeu.services.AgendaService agendaService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setAgendaService(com.upeu.comedorupeu.services.AgendaService agendaService) {
        this.agendaService = agendaService;
    }

    @Transactional
    public void ponerAlDia() {
        aplicarAgenda();
    }

    @Scheduled(fixedRate = 60000, initialDelay = 15000)
    @Transactional
    public void aplicarAgenda() {
        boolean huboCambios = false;
        int hoyDia = LocalDate.now().getDayOfWeek().getValue();
        LocalTime ahora = LocalTime.now();

        huboCambios |= aplicarTurnos(hoyDia, ahora);
        huboCambios |= aplicarPuntos(hoyDia, ahora);
        huboCambios |= cerrarManualesDelMismoOperador();
        huboCambios |= cerrarTurnosHuerfanos(hoyDia, ahora);
        huboCambios |= asegurarTurnoDeLosAbiertos();

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

                if (atendidoPorAlguienAbierto(tipo)) continue;
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

            List<ProgramacionHorario> celdas = agendaService
                    .listaDe(LocalDate.now(), punto.getIdPunto())
                    .stream()
                    .filter(c -> !Boolean.FALSE.equals(c.getActivo()))
                    .filter(c -> c.getHoraInicio() != null || c.getHoraFin() != null)
                    .toList();
            if (celdas.isEmpty()) continue;

            ProgramacionHorario actual = null;
            ProgramacionHorario celdaPasada = null;
            LocalTime ultimoFinPasado = null;
            for (ProgramacionHorario c : celdas) {
                LocalTime vFin = (c.getHoraFin() != null) ? c.getHoraFin() : LocalTime.MAX;
                if (c.getHoraInicio() != null && !ahora.isBefore(c.getHoraInicio()) && !ahora.isAfter(vFin)) {
                    actual = c;
                } else if (c.getHoraFin() != null && ahora.isAfter(c.getHoraFin())
                        && (ultimoFinPasado == null || c.getHoraFin().isAfter(ultimoFinPasado))) {
                    ultimoFinPasado = c.getHoraFin();
                    celdaPasada = c;
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

                liberarOtroPuntoDe(actual, punto);
                turnoService.abrirPorAgenda(actual.getTipoTurno());

                punto.setModo("HORARIO");
                punto.setActivo(true);
                punto.setTurnoManual(null);
                punto.setHoraInicio(actual.getHoraInicio());
                punto.setHoraFin(actual.getHoraFin());
                if (actual.getCajero() != null) punto.setCajero(actual.getCajero());
                puntoRepo.save(punto);
                cambio = true;
            } else if (debeAbrir && (cambiaDeTurno(punto, actual) || cambioDeOperador(punto, actual))) {

                String servia = turnoService.turnoQueLeTocaA(punto);
                liberarOtroPuntoDe(actual, punto);

                punto.setModo("HORARIO");
                punto.setTurnoManual(null);
                punto.setUltimaAccionManual(null);
                punto.setHoraInicio(actual.getHoraInicio());
                punto.setHoraFin(actual.getHoraFin());
                if (actual.getCajero() != null) punto.setCajero(actual.getCajero());
                puntoRepo.save(punto);

                turnoService.abrirPorAgenda(actual.getTipoTurno());
                if (servia != null && !servia.equals(actual.getTipoTurno())
                        && !atendidoPorAlguienAbierto(servia) && turnoService.cerrarPorRelevo(servia)) {
                    System.out.println(">> Se cerró el " + servia.toLowerCase() + ": ya nadie lo atiende.");
                }
                System.out.println(">> " + punto.getNombre() + " ya estaba abierto y pasó a "
                        + actual.getTipoTurno().toLowerCase() + " con "
                        + (actual.getCajero() != null ? actual.getCajero().getNombreCompleto() : "su mismo operador")
                        + " por la programación de las " + actual.getHoraInicio() + ".");
                cambio = true;
            } else if (!debeAbrir && Boolean.TRUE.equals(punto.getActivo())) {

                punto.setModo("HORARIO");
                punto.setActivo(false);
                punto.setTurnoManual(null);
                puntoRepo.save(punto);

                if (celdaPasada != null && celdaPasada.getTipoTurno() != null
                        && nadieMasAtiende(celdaPasada.getTipoTurno(), punto.getIdPunto(), ahora)) {
                    turnoService.cerrarPorAgenda(celdaPasada.getTipoTurno());
                }
                cambio = true;
            }
        }
        return cambio;
    }

    private boolean cambiaDeTurno(PuntoAtencion punto, ProgramacionHorario celda) {
        if (celda.getTipoTurno() == null) return false;
        return !celda.getTipoTurno().equals(turnoService.turnoQueLeTocaA(punto));
    }

    private boolean cambioDeOperador(PuntoAtencion punto, ProgramacionHorario celda) {
        if (celda.getCajero() == null) return false;
        if (punto.getCajero() == null) return true;
        return !punto.getCajero().getIdUsuario().equals(celda.getCajero().getIdUsuario());
    }

    private void liberarOtroPuntoDe(ProgramacionHorario celda, PuntoAtencion destino) {
        if (celda.getCajero() == null) return;
        for (PuntoAtencion otro : puntoRepo.vigentes()) {
            if (otro.getIdPunto().equals(destino.getIdPunto())) continue;
            if (!otro.isOperativo() || otro.getCajero() == null) continue;
            if (!otro.getCajero().getIdUsuario().equals(celda.getCajero().getIdUsuario())) continue;

            String servia = turnoService.turnoQueLeTocaA(otro);
            otro.setActivo(false);
            otro.setTurnoManual(null);
            otro.setUltimaAccionManual(null);
            puntoRepo.save(otro);
            System.out.println(">> " + otro.getNombre() + " se cerró: su operador pasa a "
                    + destino.getNombre() + " por la programación de las " + celda.getHoraInicio() + ".");

            if (servia != null && !servia.equals(celda.getTipoTurno()) && !atendidoPorAlguienAbierto(servia)
                    && turnoService.cerrarPorRelevo(servia)) {
                System.out.println(">> Se cerró el " + servia.toLowerCase()
                        + ": ya nadie lo estaba atendiendo.");
            }
        }
    }

    private boolean atendidoPorAlguienAbierto(String tipo) {
        for (PuntoAtencion p : puntoRepo.vigentes()) {
            if (!p.isOperativo()) continue;
            if (tipo.equals(turnoService.turnoQueLeTocaA(p))) return true;
        }
        return false;
    }

    private boolean cerrarManualesDelMismoOperador() {
        boolean cambio = false;
        for (PuntoAtencion porAgenda : puntoRepo.vigentes()) {
            if (!porAgenda.isOperativo() || porAgenda.getCajero() == null) continue;
            if (!"HORARIO".equals(porAgenda.getModo())) continue;

            for (PuntoAtencion otro : puntoRepo.vigentes()) {
                if (otro.getIdPunto().equals(porAgenda.getIdPunto())) continue;
                if (!otro.isOperativo() || otro.getCajero() == null) continue;
                if ("HORARIO".equals(otro.getModo())) continue;
                if (!otro.getCajero().getIdUsuario().equals(porAgenda.getCajero().getIdUsuario())) continue;

                String servia = turnoService.turnoQueLeTocaA(otro);
                otro.setActivo(false);
                otro.setTurnoManual(null);
                otro.setUltimaAccionManual(null);
                puntoRepo.save(otro);
                System.out.println(">> " + otro.getNombre() + " se cerró: "
                        + porAgenda.getCajero().getNombreCompleto() + " ya está atendiendo en "
                        + porAgenda.getNombre() + " por la programación.");

                if (servia != null && !atendidoPorAlguienAbierto(servia) && turnoService.cerrarPorRelevo(servia)) {
                    System.out.println(">> Se cerró el " + servia.toLowerCase() + ": ya nadie lo atiende.");
                }
                cambio = true;
            }
        }
        return cambio;
    }

    private boolean cerrarTurnosHuerfanos(int hoyDia, LocalTime ahora) {
        boolean cambio = false;
        for (Turno t : turnoService.turnosDeHoy()) {
            if (!"ACTIVO".equals(t.getEstado())) continue;
            if (atendidoPorAlguienAbierto(t.getTipo())) continue;
            if (ventanaDelTurnoCubre(t.getTipo(), hoyDia, ahora)) continue;

            if (turnoService.cerrarPorRelevo(t.getTipo())) {
                System.out.println(">> Se cerró el " + t.getTipo().toLowerCase()
                        + ": ninguna entrada lo está atendiendo.");
                cambio = true;
            }
        }
        return cambio;
    }

    private boolean ventanaDelTurnoCubre(String tipo, int hoyDia, LocalTime ahora) {
        LocalTime ini = null, fin = null;
        ProgramacionHorario celda = programacionRepo
                .findFirstByObjetivoAndTipoTurnoAndDiaSemana("TURNO", tipo, hoyDia).orElse(null);
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
        if (ini == null && fin == null) return false;
        if (ini == null) return !ahora.isAfter(fin);
        LocalTime vFin = (fin != null) ? fin : LocalTime.MAX;
        return !ahora.isBefore(ini) && !ahora.isAfter(vFin);
    }

    private boolean asegurarTurnoDeLosAbiertos() {
        boolean cambio = false;
        for (PuntoAtencion punto : puntoRepo.vigentes()) {
            if (!punto.isOperativo()) continue;
            if (turnoService.turnoActivoDe(punto).isPresent()) continue;

            String tipo = turnoService.turnoQueLeTocaA(punto);
            if (tipo == null) continue;
            if (turnoService.activarPorPuntoAbierto(tipo)) {
                System.out.println(">> " + punto.getNombre() + " estaba abierto sin turno: se activó "
                        + tipo.toLowerCase() + " para que su cajero pueda atender.");
                cambio = true;
            }
        }
        return cambio;
    }

    private boolean nadieMasAtiende(String tipo, Long idPuntoQueCierra, LocalTime ahora) {
        for (PuntoAtencion otro : puntoRepo.vigentes()) {
            if (otro.getIdPunto().equals(idPuntoQueCierra)) continue;
            if (!otro.isOperativo()) continue;

            boolean cubiertoPorAgenda = false;
            for (ProgramacionHorario c : agendaService.listaDe(LocalDate.now(), otro.getIdPunto())) {
                if (Boolean.FALSE.equals(c.getActivo()) || c.getHoraInicio() == null) continue;
                LocalTime vFin = (c.getHoraFin() != null) ? c.getHoraFin() : LocalTime.MAX;
                if (ahora.isBefore(c.getHoraInicio()) || ahora.isAfter(vFin)) continue;
                cubiertoPorAgenda = true;
                if (tipo.equals(c.getTipoTurno())) return false;
            }
            if (cubiertoPorAgenda) continue;

            if (otro.getTurnoManual() == null || tipo.equals(otro.getTurnoManual())) return false;
        }
        return true;
    }
}
