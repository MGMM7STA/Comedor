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

    private com.upeu.comedorupeu.repository.MarcacionRepository marcacionRepo;

    @org.springframework.beans.factory.annotation.Autowired
    public void setMarcacionRepo(com.upeu.comedorupeu.repository.MarcacionRepository marcacionRepo) {
        this.marcacionRepo = marcacionRepo;
    }

    public boolean selloEsDeOtroDia(java.time.LocalDateTime sello) {
        return sello != null && !sello.toLocalDate().equals(LocalDate.now());
    }

    public Optional<Turno> turnoActivoDe(com.upeu.comedorupeu.models.PuntoAtencion punto) {
        if (punto == null || !punto.isOperativo()) return turnoActivo();

        String elegido = selloEsDeOtroDia(punto.getUltimaAccionManual()) ? null : punto.getTurnoManual();
        if (elegido != null && !elegido.isBlank()) {
            Optional<Turno> propio = turnosDeHoy().stream()
                    .filter(t -> elegido.equals(t.getTipo()))
                    .filter(t -> "ACTIVO".equals(t.getEstado()) || estaAtendiendo(t))
                    .findFirst();
            if (propio.isPresent()) return propio;
        }

        String porAgenda = turnoDeLaAgendaDe(punto);
        if (porAgenda != null) {
            Optional<Turno> propio = turnosDeHoy().stream()
                    .filter(t -> porAgenda.equals(t.getTipo()))
                    .filter(t -> "ACTIVO".equals(t.getEstado()) || estaAtendiendo(t))
                    .findFirst();
            if (propio.isPresent()) return propio;
        }

        return turnoActivo();
    }

    private String turnoDeLaAgendaDe(com.upeu.comedorupeu.models.PuntoAtencion punto) {
        int hoyDia = LocalDate.now().getDayOfWeek().getValue();
        java.time.LocalTime ahora = java.time.LocalTime.now();
        for (var celda : programacionRepo.findByObjetivoAndPuntoIdPuntoAndDiaSemana(
                "TURNO_PUNTO", punto.getIdPunto(), hoyDia)) {
            if (Boolean.FALSE.equals(celda.getActivo())) continue;
            java.time.LocalTime ini = celda.getHoraInicio();
            if (ini == null) continue;
            java.time.LocalTime fin = (celda.getHoraFin() != null) ? celda.getHoraFin() : java.time.LocalTime.MAX;
            if (!ahora.isBefore(ini) && !ahora.isAfter(fin)) return celda.getTipoTurno();
        }
        return null;
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
                        || selloEsDeOtroDia(turno.getUltimaAccionManual())
                        || turno.getUltimaAccionManual().isBefore(limite));
            if (agendaGobierna) return dentro;
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
        if (fecha.isAfter(hoy)) return false;

        Optional<Turno> turno = turnoRepo.findByFechaAndTipo(fecha, tipo);
        if (turno.isEmpty() || "DESACTIVADO".equals(turno.get().getEstado())) return false;
        if (!tuvoActividad(turno.get())) return false;

        if (fecha.isBefore(hoy)) return true;

        if (estaAtendiendo(turno.get())) return false;
        if ("CERRADO".equals(turno.get().getEstado())) return true;

        int idx = TIPOS.indexOf(tipo);
        for (int i = idx + 1; i < TIPOS.size(); i++) {
            Optional<Turno> posterior = turnoRepo.findByFechaAndTipo(hoy, TIPOS.get(i));
            if (posterior.isEmpty()) continue;
            boolean atendiendo = estaAtendiendo(posterior.get());
            boolean cerradoConGente = "CERRADO".equals(posterior.get().getEstado())
                    && tuvoActividad(posterior.get());
            if (atendiendo || cerradoConGente) return true;
        }

        return false;
    }

    public boolean tuvoActividad(Turno turno) {
        if (turno == null || turno.getIdTurno() == null) return false;
        if (marcacionRepo == null) return true;
        return marcacionRepo.countByTurnoIdTurno(turno.getIdTurno()) > 0;
    }

    @Transactional
    public void cambiarEstado(Long idTurno, String accion, Usuario usuario) {
        Turno turno = turnoRepo.findById(idTurno).orElseThrow();
        switch (accion) {
            case "activar" -> turno.setEstado("ACTIVO");
            case "cerrar" -> turno.setEstado("CERRADO");
        }
        turno.setUsuario(usuario);

        turno.setUltimaAccionManual(java.time.LocalDateTime.now());
        turnoRepo.save(turno);
    }
}
