package com.upeu.comedorupeu.services;

import com.upeu.comedorupeu.models.ConfigTurno;
import com.upeu.comedorupeu.models.Turno;
import com.upeu.comedorupeu.models.Usuario;
import com.upeu.comedorupeu.repository.ConfigTurnoRepository;
import com.upeu.comedorupeu.repository.TurnoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TurnoService {

    public static final List<String> TIPOS = List.of("DESAYUNO", "ALMUERZO", "CENA");

    private final TurnoRepository turnoRepo;
    private final ConfigTurnoRepository configRepo;

    private final com.upeu.comedorupeu.repository.ProgramacionHorarioRepository programacionRepo;

    public TurnoService(TurnoRepository turnoRepo, ConfigTurnoRepository configRepo,
                        com.upeu.comedorupeu.repository.ProgramacionHorarioRepository programacionRepo) {
        this.turnoRepo = turnoRepo;
        this.configRepo = configRepo;
        this.programacionRepo = programacionRepo;
    }

    public Optional<java.time.LocalTime[]> ventanaDe(String tipo) {
        int hoyDia = LocalDate.now().getDayOfWeek().getValue();
        var celda = programacionRepo
                .findFirstByObjetivoAndTipoTurnoAndDiaSemana("TURNO", tipo, hoyDia)
                .orElse(null);
        java.time.LocalTime ini = null, fin = null;
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
        if (ini == null && fin == null) return Optional.empty();
        return Optional.of(new java.time.LocalTime[]{ini, fin});
    }

    private static String textoHora(java.time.LocalTime h) {
        return h == null ? "--:--" : String.format("%02d:%02d", h.getHour(), h.getMinute());
    }

    public String textoVentana(String tipo) {
        return ventanaDe(tipo)
                .map(v -> textoHora(v[0]) + " - " + textoHora(v[1]))
                .orElse(null);
    }

    @Transactional
    public List<Turno> turnosDeHoy() {
        LocalDate hoy = LocalDate.now();
        List<Turno> turnos = new ArrayList<>();
        for (String tipo : TIPOS) {
            Turno t = turnoRepo.findByFechaAndTipo(hoy, tipo).orElseGet(() -> {
                Turno nuevo = new Turno();
                nuevo.setTipo(tipo);
                nuevo.setFecha(hoy);
                nuevo.setEstado("DESACTIVADO");
                return turnoRepo.save(nuevo);
            });
            turnos.add(t);
        }
        return turnos;
    }

    public Optional<Turno> turnoActivo() {
        return turnosDeHoy().stream().filter(this::estaAtendiendo).findFirst();
    }

    public boolean estaAtendiendo(Turno turno) {
        var ventana = ventanaDe(turno.getTipo());
        if (ventana.isPresent()) {
            java.time.LocalTime ahora = java.time.LocalTime.now();
            java.time.LocalTime ini = ventana.get()[0];
            java.time.LocalTime fin = ventana.get()[1];

            boolean dentro;
            java.time.LocalDateTime limite = null;
            if (ini == null) {
                dentro = false;
                if (ahora.isAfter(fin)) limite = LocalDate.now().atTime(fin);
            } else {
                java.time.LocalTime vFin = (fin != null) ? fin : java.time.LocalTime.of(23, 59);
                dentro = !ahora.isBefore(ini) && !ahora.isAfter(vFin);
                if (dentro) limite = LocalDate.now().atTime(ini);
                else if (ahora.isAfter(vFin)) limite = LocalDate.now().atTime(vFin);
            }

            boolean agendaGobierna = limite != null
                    && (turno.getUltimaAccionManual() == null
                        || turno.getUltimaAccionManual().isBefore(limite));
            if (agendaGobierna) {
                if (!dentro) return false;

                boolean otroManualPosterior = turnosDeHoy().stream()
                        .anyMatch(t -> !t.getTipo().equals(turno.getTipo())
                                && "ACTIVO".equals(t.getEstado())
                                && t.getUltimaAccionManual() != null
                                && !t.getUltimaAccionManual().isBefore(LocalDate.now().atTime(ini)));
                return !otroManualPosterior;
            }
        }

        return "ACTIVO".equals(turno.getEstado());
    }

    public boolean agendaAbiertaAhora(String tipo) {
        return ventanaDe(tipo).map(v -> {
            if (v[0] == null) return false;
            java.time.LocalTime ahora = java.time.LocalTime.now();
            java.time.LocalTime fin = (v[1] != null) ? v[1] : java.time.LocalTime.of(23, 59);
            return !ahora.isBefore(v[0]) && !ahora.isAfter(fin);
        }).orElse(false);
    }

    @Transactional
    public String reaplicarAgenda(String tipo) {
        var ventana = ventanaDe(tipo);
        if (ventana.isEmpty()) return null;
        Turno turno = turnosDeHoy().stream()
                .filter(t -> t.getTipo().equals(tipo))
                .findFirst().orElse(null);
        if (turno == null) return null;

        boolean dentro = agendaAbiertaAhora(tipo);

        turno.setUltimaAccionManual(null);
        if (dentro) {

            for (Turno otro : turnoRepo.findByFecha(LocalDate.now())) {
                if (!otro.getIdTurno().equals(turno.getIdTurno()) && "ACTIVO".equals(otro.getEstado())) {
                    otro.setEstado("CERRADO");
                    turnoRepo.save(otro);
                }
            }
            turno.setEstado("ACTIVO");
        } else if ("ACTIVO".equals(turno.getEstado())) {

            java.time.LocalTime ini = ventana.get()[0], fin = ventana.get()[1];
            java.time.LocalTime ahora = java.time.LocalTime.now();
            boolean soloAperturaFutura = ini != null && fin == null && ahora.isBefore(ini);
            boolean soloCierreFuturo = ini == null && fin != null && !ahora.isAfter(fin);
            if (!soloAperturaFutura && !soloCierreFuturo) turno.setEstado("CERRADO");
        }
        turnoRepo.save(turno);
        return "Programación reaplicada al " + tipo.toLowerCase() + ": queda "
                + ("ACTIVO".equals(turno.getEstado()) ? "ABIERTO" : "CERRADO")
                + " (su horario es " + textoVentana(tipo) + ") y vuelve a regirse por la agenda.";
    }

    @Transactional
    public void aplicarHorarioFijoHoy(String tipo, java.time.LocalTime horaInicio, java.time.LocalTime horaFin) {
        int hoyDia = LocalDate.now().getDayOfWeek().getValue();
        programacionRepo.findFirstByObjetivoAndTipoTurnoAndDiaSemana("TURNO", tipo, hoyDia)
                .ifPresent(celda -> {
                    celda.setHoraInicio(horaInicio);
                    celda.setHoraFin(horaFin);
                    programacionRepo.save(celda);
                });
        reaplicarAgenda(tipo);
    }

    @Transactional
    public ConfigTurno configDe(String tipo) {
        return configRepo.findByTipo(tipo).orElseGet(() -> {
            ConfigTurno c = new ConfigTurno();
            c.setTipo(tipo);
            c.setUsarHorario(false);
            return configRepo.save(c);
        });
    }

    @Transactional
    public void guardarConfig(String tipo, boolean usarHorario,
                              java.time.LocalTime horaInicio, java.time.LocalTime horaFin) {
        ConfigTurno c = configDe(tipo);
        c.setUsarHorario(usarHorario);
        c.setHoraInicio(horaInicio);
        c.setHoraFin(horaFin);
        configRepo.save(c);
    }

    public List<String> comidasBloqueadasHoy() {
        List<String> bloqueadas = new ArrayList<>();
        for (String tipo : TIPOS) {
            if (turnoYaOcurrio(tipo, LocalDate.now())) bloqueadas.add(tipo);
        }
        return bloqueadas;
    }

    public boolean turnoYaOcurrio(String tipo, LocalDate fecha) {
        LocalDate hoy = LocalDate.now();
        if (fecha.isBefore(hoy)) return true;
        if (fecha.isAfter(hoy)) return false;

        Optional<Turno> turnoHoy = turnoRepo.findByFechaAndTipo(hoy, tipo);
        if (turnoHoy.isPresent() && "CERRADO".equals(turnoHoy.get().getEstado())
                && !estaAtendiendo(turnoHoy.get())) return true;

        int idx = TIPOS.indexOf(tipo);
        for (int i = idx + 1; i < TIPOS.size(); i++) {
            Optional<Turno> posterior = turnoRepo.findByFechaAndTipo(hoy, TIPOS.get(i));
            if (posterior.isPresent() && (estaAtendiendo(posterior.get())
                    || "CERRADO".equals(posterior.get().getEstado()))) return true;
        }

        return false;
    }

    @Transactional
    public void cambiarEstado(Long idTurno, String accion, Usuario usuario) {
        Turno turno = turnoRepo.findById(idTurno).orElseThrow();
        switch (accion) {
            case "activar" -> {
                for (Turno t : turnoRepo.findByFecha(turno.getFecha())) {
                    if (!t.getIdTurno().equals(idTurno) && "ACTIVO".equals(t.getEstado())) {
                        t.setEstado("CERRADO");
                        turnoRepo.save(t);
                    }
                }
                turno.setEstado("ACTIVO");
            }
            case "cerrar" -> turno.setEstado("CERRADO");
        }
        turno.setUsuario(usuario);

        turno.setUltimaAccionManual(java.time.LocalDateTime.now());
        turnoRepo.save(turno);
    }
}
