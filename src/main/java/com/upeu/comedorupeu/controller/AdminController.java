package com.upeu.comedorupeu.controller;

import com.upeu.comedorupeu.dto.SemanaNav;
import com.upeu.comedorupeu.models.Apunte;
import com.upeu.comedorupeu.models.EventoEspecial;
import com.upeu.comedorupeu.models.Incidencia;
import com.upeu.comedorupeu.models.PuntoAtencion;
import com.upeu.comedorupeu.models.Turno;
import com.upeu.comedorupeu.models.Usuario;
import com.upeu.comedorupeu.repository.*;
import com.upeu.comedorupeu.services.TurnoService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UsuarioRepository usuarioRepo;
    private final ResidenteRepository residenteRepo;
    private final PuntoAtencionRepository puntoRepo;
    private final MarcacionRepository marcacionRepo;
    private final EventoEspecialRepository eventoRepo;
    private final IncidenciaRepository incidenciaRepo;

    private com.upeu.comedorupeu.services.SemestreService semestreService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSemestreService(com.upeu.comedorupeu.services.SemestreService semestreService) {
        this.semestreService = semestreService;
    }

    private final SolicitudExtemporaneaRepository solicitudRepo;
    private final AusenciaRepository ausenciaRepo;
    private final ApunteRepository apunteRepo;
    private final EventoEntregaRepository entregaRepo;
    private final TurnoService turnoService;
    private final PasswordEncoder encoder;

    private com.upeu.comedorupeu.services.ExcelService excelService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setExcelService(com.upeu.comedorupeu.services.ExcelService excelService) {
        this.excelService = excelService;
    }

    private ProgramacionHorarioRepository programacionRepo;

    @org.springframework.beans.factory.annotation.Autowired
    public void setProgramacionRepo(ProgramacionHorarioRepository programacionRepo) {
        this.programacionRepo = programacionRepo;
    }

    private TurnoRepository turnoRepo;

    @org.springframework.beans.factory.annotation.Autowired
    public void setTurnoRepo(TurnoRepository turnoRepo) {
        this.turnoRepo = turnoRepo;
    }

    private com.upeu.comedorupeu.services.AgendaService agendaService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setAgendaService(com.upeu.comedorupeu.services.AgendaService agendaService) {
        this.agendaService = agendaService;
    }

    private com.upeu.comedorupeu.repository.AusenciaDetalleRepository ausenciaDetalleRepo;

    @org.springframework.beans.factory.annotation.Autowired
    public void setAusenciaDetalleRepo(com.upeu.comedorupeu.repository.AusenciaDetalleRepository repo) {
        this.ausenciaDetalleRepo = repo;
    }

    private com.upeu.comedorupeu.services.ReglasComidaService reglasComidaService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setReglasComidaService(com.upeu.comedorupeu.services.ReglasComidaService reglasComidaService) {
        this.reglasComidaService = reglasComidaService;
    }

    public AdminController(UsuarioRepository usuarioRepo, ResidenteRepository residenteRepo,
                           PuntoAtencionRepository puntoRepo, MarcacionRepository marcacionRepo,
                           EventoEspecialRepository eventoRepo, IncidenciaRepository incidenciaRepo,
                           SolicitudExtemporaneaRepository solicitudRepo, AusenciaRepository ausenciaRepo,
                           ApunteRepository apunteRepo, EventoEntregaRepository entregaRepo,
                           TurnoService turnoService, PasswordEncoder encoder) {
        this.usuarioRepo = usuarioRepo;
        this.residenteRepo = residenteRepo;
        this.puntoRepo = puntoRepo;
        this.marcacionRepo = marcacionRepo;
        this.eventoRepo = eventoRepo;
        this.incidenciaRepo = incidenciaRepo;
        this.solicitudRepo = solicitudRepo;
        this.ausenciaRepo = ausenciaRepo;
        this.apunteRepo = apunteRepo;
        this.entregaRepo = entregaRepo;
        this.turnoService = turnoService;
        this.encoder = encoder;
    }

    private Usuario usuarioActual(Authentication auth) {
        return usuarioRepo.findByCorreo(auth.getName());
    }

    private List<Map<String, Object>> filasAgendaDelDia(
            List<com.upeu.comedorupeu.models.ProgramacionHorario> celdasDelPunto,
            List<com.upeu.comedorupeu.models.ProgramacionHorario> celdasDeTodos,
            Long idPunto, LocalTime ahora) {

        List<Map<String, Object>> filas = new java.util.ArrayList<>();
        for (String tipoT : TurnoService.TIPOS) {
            var celda = celdasDelPunto.stream()
                    .filter(c -> tipoT.equals(c.getTipoTurno()))
                    .filter(c -> c.getHoraInicio() != null || c.getHoraFin() != null)
                    .findFirst().orElse(null);

            Map<String, Object> fila = new HashMap<>();
            fila.put("turno", tipoT.charAt(0) + tipoT.substring(1).toLowerCase());
            if (celda == null) {
                fila.put("horas", "—");
                fila.put("cajero", "—");
                fila.put("vigente", false);
                fila.put("aviso", null);
                filas.add(fila);
                continue;
            }

            LocalTime ini = celda.getHoraInicio();
            LocalTime fin = celda.getHoraFin();
            fila.put("horas", (ini == null ? "--:--" : ini.toString()) + " – " + (fin == null ? "--:--" : fin.toString()));
            fila.put("cajero", celda.getCajero() != null ? celda.getCajero().getNombreCompleto() : "Sin asignar");
            fila.put("vigente", ini != null && fin != null && !ahora.isBefore(ini) && !ahora.isAfter(fin));

            String aviso = null;
            if (celda.getCajero() != null) {
                var otra = celdasDeTodos.stream()
                        .filter(c -> c.getPunto() != null && !idPunto.equals(c.getPunto().getIdPunto()))
                        .filter(c -> c.getCajero() != null
                                && c.getCajero().getIdUsuario().equals(celda.getCajero().getIdUsuario()))
                        .findFirst().orElse(null);
                if (otra != null) {
                    aviso = "Este operador también atiende el "
                            + otra.getTipoTurno().toLowerCase() + " en " + otra.getPunto().getNombre()
                            + ". Se reasigna solo cuando llega cada turno.";
                }
            }
            fila.put("aviso", aviso);
            filas.add(fila);
        }
        return filas;
    }

    @GetMapping("/puntos")
    public String puntos(@RequestParam(name = "turnoPanel", defaultValue = "TODOS") String turnoPanel,
                         @RequestParam(required = false)
                         @org.springframework.format.annotation.DateTimeFormat(
                                 iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate desde,
                         Model model) {
        if (programacionScheduler != null) programacionScheduler.ponerAlDia();

        LocalDate diaCifras = (desde != null) ? desde : LocalDate.now();
        model.addAttribute("nav", com.upeu.comedorupeu.dto.SemanaNav.de(diaCifras));
        model.addAttribute("diaCifras", diaCifras);
        model.addAttribute("esHoy", diaCifras.equals(LocalDate.now()));

        List<Turno> turnos = turnoService.turnosDeHoy();
        Optional<Turno> activo = turnoService.turnoActivo();

        final String panel = TurnoService.TIPOS.contains(turnoPanel) ? turnoPanel : "TODOS";

        java.time.DayOfWeek diaHoy = LocalDate.now().getDayOfWeek();
        LocalTime ahora = LocalTime.now();
        Map<String, List<Map<String, Object>>> vistaTurnos = new java.util.LinkedHashMap<>();
        for (String tipoT : TurnoService.TIPOS) vistaTurnos.put(tipoT, new java.util.ArrayList<>());

        Map<Long, Boolean> necesitaTurno = new HashMap<>();

        Map<Long, Boolean> reprogPendiente = new HashMap<>();
        Map<Long, Boolean> abiertoAMano = new HashMap<>();
        Map<Long, Boolean> tieneAgendaHoy = new HashMap<>();
        List<Map<String, Object>> sinTurno = new java.util.ArrayList<>();
        Map<Long, List<Map<String, Object>>> agendaDelDia = new HashMap<>();
        List<com.upeu.comedorupeu.models.ProgramacionHorario> celdasDeTodos = agendaService
                .listaDe(LocalDate.now())
                .stream().filter(c -> !Boolean.FALSE.equals(c.getActivo())).toList();
        for (PuntoAtencion p : puntoRepo.vigentes()) {

            var celdasHoy = agendaService.listaDe(LocalDate.now(), p.getIdPunto())
                    .stream().filter(c -> !Boolean.FALSE.equals(c.getActivo())).toList();
            com.upeu.comedorupeu.models.ProgramacionHorario celdaCubre = null;
            for (var c : celdasHoy) {
                if (c.cubre(diaHoy, ahora)) { celdaCubre = c; break; }
            }
            necesitaTurno.put(p.getIdPunto(), true);
            abiertoAMano.put(p.getIdPunto(), p.isOperativo() && p.getTurnoManual() != null);
            tieneAgendaHoy.put(p.getIdPunto(), !celdasHoy.isEmpty());
            agendaDelDia.put(p.getIdPunto(),
                    filasAgendaDelDia(celdasHoy, celdasDeTodos, p.getIdPunto(), ahora));

            boolean pendiente = false;
            if (p.isOperativo() && p.getTurnoManual() != null) {
                pendiente = !celdasHoy.isEmpty();
            } else if (p.isOperativo() && celdaCubre != null) {

                pendiente = !java.util.Objects.equals(celdaCubre.getHoraInicio(), p.getHoraInicio())
                        || !java.util.Objects.equals(celdaCubre.getHoraFin(), p.getHoraFin());
            } else if (!p.isOperativo()) {

                pendiente = celdaCubre != null;
            } else {

                pendiente = !celdasHoy.isEmpty();
            }
            reprogPendiente.put(p.getIdPunto(), pendiente);

            if (!p.isOperativo()) continue;
            String elegido = turnoService.selloEsDeOtroDia(p.getUltimaAccionManual()) ? null : p.getTurnoManual();
            String suyo = (elegido != null && !elegido.isBlank()) ? elegido
                    : (celdaCubre != null ? celdaCubre.getTipoTurno() : null);

            if (suyo != null && vistaTurnos.containsKey(suyo)) {
                boolean porMano = suyo.equals(elegido);
                vistaTurnos.get(suyo).add(Map.of(
                        "nombre", p.getNombre(),
                        "horario", porMano ? "activado manualmente"
                                : celdaCubre.getHoraInicio() + " a " + celdaCubre.getHoraFin(),
                        "cajero", p.getCajero() != null ? p.getCajero().getNombreCompleto() : "Sin asignar"));
            } else {

                sinTurno.add(Map.of(
                        "nombre", p.getNombre(),
                        "horario", p.getHorarioTexto(),
                        "cajero", p.getCajero() != null ? p.getCajero().getNombreCompleto() : "Sin asignar"));
            }
        }
        model.addAttribute("puntosSinTurno", sinTurno);
        model.addAttribute("vistaTurnos", vistaTurnos);
        model.addAttribute("necesitaTurno", necesitaTurno);
        model.addAttribute("reprogPendiente", reprogPendiente);
        model.addAttribute("abiertoAMano", abiertoAMano);
        model.addAttribute("tieneAgendaHoy", tieneAgendaHoy);
        model.addAttribute("agendaDelDia", agendaDelDia);
        model.addAttribute("cajeros", usuarioRepo.findByRol("CAJERO"));
        model.addAttribute("hayActividad", vistaTurnos.values().stream().anyMatch(l -> !l.isEmpty()));

        long turnosActivos = vistaTurnos.values().stream().filter(l -> !l.isEmpty()).count();
        String colTurnos = turnosActivos <= 1 ? "col-md-8" : (turnosActivos == 2 ? "col-md-6" : "col-md-4");
        model.addAttribute("colTurnos", colTurnos);

        boolean panelTodos = "TODOS".equals(panel);
        List<Turno> turnosDelDia = turnoRepo.findByFecha(diaCifras);
        List<Turno> turnosPanel = panelTodos ? turnosDelDia
                : turnosDelDia.stream().filter(t -> t.getTipo().equals(panel)).toList();

        long residentesActivos = residenteRepo.countByEstado("ACTIVO");
        long habilitados = residentesActivos;
        long racionesPosibles = residentesActivos * (panelTodos ? TurnoService.TIPOS.size() : 1);
        long atendidos = 0, bloqueados = 0;
        for (Turno t : turnosPanel) {
            atendidos += marcacionRepo.countByTurnoIdTurnoAndEstado(t.getIdTurno(), "PERMITIDO");
            bloqueados += marcacionRepo.countByTurnoIdTurnoAndEstado(t.getIdTurno(), "DENEGADO");
        }

        LocalDate hoyFecha = diaCifras;
        List<String> comidasPanel = panelTodos ? TurnoService.TIPOS : List.of(panel);
        long justificados = 0, reservas = 0;
        for (String comida : comidasPanel) {
            justificados += ausenciaDetalleRepo.findByFechaAndTipoComida(hoyFecha, comida).size();
            reservas += solicitudRepo.findByFechaOrderByTipoComidaAsc(hoyFecha).stream()
                    .filter(s -> comida.equals(s.getTipoComida()) && "PENDIENTE".equals(s.getEstado()))
                    .count();
        }
        model.addAttribute("turnoPanel", panel);
        model.addAttribute("residentesActivos", residentesActivos);

        model.addAttribute("turnos", turnos);
        model.addAttribute("turnoActivo", activo.orElse(null));

        model.addAttribute("puntos", puntoRepo.vigentes());
        model.addAttribute("habilitados", habilitados);
        model.addAttribute("racionesPosibles", racionesPosibles);
        model.addAttribute("atendidos", atendidos);
        model.addAttribute("justificados", justificados);
        model.addAttribute("reservas", reservas);
        model.addAttribute("pendientes", Math.max(0, racionesPosibles - atendidos - justificados));
        model.addAttribute("bloqueados", bloqueados);
        model.addAttribute("avisos", apunteRepo.findTop10ByTipoOrderByFechaHoraDesc("AVISO"));
        model.addAttribute("avisosPrec", apunteRepo.findTop10ByTipoOrderByFechaHoraDesc("PRECEPTOR"));
        model.addAttribute("notasPersonales", apunteRepo.findTop10ByTipoOrderByFechaHoraDesc("PERSONAL"));

        var eventosHoy = eventoRepo.findByEstadoAndFechaEvento("APROBADO", hoyFecha).stream()
                .filter(e -> panelTodos || panel.equals(e.getComida()))
                .toList();
        long racionesEvento = 0;
        for (EventoEspecial ev : eventosHoy) {
            racionesEvento += residenteRepo.findByEstadoOrderByApellidoAsc("ACTIVO").stream()
                    .filter(r -> !ev.getExcluidosLista().contains(r.getCodigoAcceso())).count();
        }
        model.addAttribute("eventosHoy", eventosHoy);
        model.addAttribute("eventoHoyRaciones", racionesEvento);
        return "admin/puntos";
    }

    private com.upeu.comedorupeu.config.ProgramacionScheduler programacionScheduler;

    @org.springframework.beans.factory.annotation.Autowired
    public void setProgramacionScheduler(com.upeu.comedorupeu.config.ProgramacionScheduler programacionScheduler) {
        this.programacionScheduler = programacionScheduler;
    }

    @PostMapping("/puntos/{id}/toggle")
    public String togglePunto(@PathVariable Long id,
                              @RequestParam(required = false) String turnoManual,
                              @RequestParam(required = false) Long idCajeroManual,
                              Authentication auth, RedirectAttributes flash) {
        final String[] conflicto = new String[1];
        puntoRepo.findById(id).ifPresent(p -> {
            boolean estabaOperativo = p.isOperativo();
            p.setModo("MANUAL");
            p.setActivo(!estabaOperativo);
            if (!estabaOperativo) {

                if (turnoManual == null || turnoManual.isBlank()) {
                    p.setActivo(false);
                    return;
                }
                if (idCajeroManual != null) {
                    PuntoAtencion ocupado = puntoRepo.vigentes().stream()
                            .filter(otro -> !otro.getIdPunto().equals(p.getIdPunto()))
                            .filter(PuntoAtencion::isOperativo)
                            .filter(otro -> otro.getCajero() != null
                                    && otro.getCajero().getIdUsuario().equals(idCajeroManual))
                            .findFirst().orElse(null);
                    if (ocupado != null) {
                        p.setActivo(false);
                        conflicto[0] = ocupado.getNombre();
                        return;
                    }
                    usuarioRepo.findById(idCajeroManual).ifPresent(p::setCajero);
                }
                p.setTurnoManual(turnoManual);
                turnoService.turnosDeHoy().stream()
                        .filter(t -> t.getTipo().equals(turnoManual))
                        .findFirst()
                        .ifPresent(t -> {
                            if (!"ACTIVO".equals(t.getEstado())) {
                                turnoService.cambiarEstado(t.getIdTurno(), "activar", usuarioActual(auth));
                            }
                        });
            } else {

                String turnoQueServia = p.getTurnoManual();
                p.setTurnoManual(null);
                if (turnoQueServia != null) {
                    boolean quedanSirviendo = puntoRepo.vigentes().stream()
                            .anyMatch(otro -> !otro.getIdPunto().equals(p.getIdPunto())
                                    && otro.isOperativo() && turnoQueServia.equals(otro.getTurnoManual()));
                    if (!quedanSirviendo) {
                        turnoService.turnosDeHoy().stream()
                                .filter(t -> t.getTipo().equals(turnoQueServia) && "ACTIVO".equals(t.getEstado()))
                                .findFirst()
                                .ifPresent(t -> {
                                    long registros = marcacionRepo.countByTurnoIdTurnoAndEstado(t.getIdTurno(), "PERMITIDO")
                                            + marcacionRepo.countByTurnoIdTurnoAndEstado(t.getIdTurno(), "JUSTIFICADO")
                                            + marcacionRepo.countByTurnoIdTurnoAndEstado(t.getIdTurno(), "DENEGADO");
                                    t.setEstado(registros == 0 ? "DESACTIVADO" : "CERRADO");
                                    t.setUltimaAccionManual(LocalDateTime.now());
                                    turnoRepo.save(t);
                                });
                    }
                }
            }

            p.setUltimaAccionManual(LocalDateTime.now());
            puntoRepo.save(p);
        });
        if (conflicto[0] != null) {
            flash.addFlashAttribute("error", "Ese operador ya está atendiendo en " + conflicto[0]
                    + " ahora mismo: no puede estar en dos entradas a la vez. "
                    + "Cierra la otra entrada o elige a otra persona.");
        }
        return "redirect:/admin/puntos";
    }

    @PostMapping("/puntos/{id}/horario")
    public String usarHorario(@PathVariable Long id, RedirectAttributes flash) {
        puntoRepo.findById(id).ifPresent(p -> {
            if (p.getHoraInicio() == null && p.getHoraFin() == null) {
                flash.addFlashAttribute("error", "El punto \"" + p.getNombre() + "\" no tiene horario configurado. Edítalo primero.");
            } else {
                p.setModo("HORARIO");
                puntoRepo.save(p);
                flash.addFlashAttribute("ok", "\"" + p.getNombre() + "\" ahora se abre y cierra solo de "
                        + p.getHorarioTexto() + ".");
            }
        });
        return "redirect:/admin/puntos";
    }

    @PostMapping("/puntos/{id}/eliminar")
    public String eliminarPunto(@PathVariable Long id, RedirectAttributes flash) {
        PuntoAtencion p = puntoRepo.findById(id).orElse(null);
        if (p == null) return "redirect:/admin/puntos";
        try {
            puntoRepo.delete(p);
            puntoRepo.flush();
            flash.addFlashAttribute("ok", "Punto \"" + p.getNombre() + "\" eliminado.");
        } catch (Exception e) {

            p.setEliminado(true);
            p.setActivo(false);
            p.setModo("MANUAL");
            p.setCajero(null);
            puntoRepo.save(p);
            flash.addFlashAttribute("ok", "Punto \"" + p.getNombre()
                    + "\" eliminado (tenía historial, así que quedó archivado: puedes auditarlo en Reportes con el filtro [ Eliminados ]).");
        }
        return "redirect:/admin/puntos";
    }

    @PostMapping("/puntos/{id}/manual")
    public String usarManual(@PathVariable Long id, RedirectAttributes flash) {
        puntoRepo.findById(id).ifPresent(p -> {
            p.setActivo(p.isOperativo());
            p.setModo("MANUAL");

            p.setUltimaAccionManual(LocalDateTime.now());
            puntoRepo.save(p);
            flash.addFlashAttribute("ok", "\"" + p.getNombre() + "\" ahora se controla manualmente con el interruptor.");
        });
        return "redirect:/admin/puntos";
    }

    @PostMapping("/apuntes")
    public String crearApunte(@RequestParam String texto,
                              @RequestParam(defaultValue = "AVISO") String tipo,
                              @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate fecha,
                              @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "HH:mm") java.time.LocalTime horaInicio,
                              @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "HH:mm") java.time.LocalTime horaFin,
                              Authentication auth) {
        if (texto != null && !texto.isBlank()) {
            Apunte a = new Apunte();
            a.setTexto(texto.trim().length() > 250 ? texto.trim().substring(0, 250) : texto.trim());
            a.setTipo(List.of("PERSONAL", "PRECEPTOR").contains(tipo) ? tipo : "AVISO");

            a.setFecha(fecha != null ? fecha : LocalDate.now());
            a.setHoraInicio(horaInicio);
            a.setHoraFin(horaFin);
            a.setUsuario(usuarioActual(auth));
            apunteRepo.save(a);
        }
        return "redirect:/admin/puntos";
    }

    @PostMapping("/apuntes/{id}/eliminar")
    public String eliminarApunte(@PathVariable Long id) {
        apunteRepo.deleteById(id);
        return "redirect:/admin/puntos";
    }

    @GetMapping("/puntos/nuevo")
    public String nuevoPunto(Model model) {
        model.addAttribute("punto", new PuntoAtencion());
        cargarCajerosConAsignacion(model);
        model.addAttribute("turnoActivo", turnoService.turnoActivo().orElse(null));
        return "admin/punto_form";
    }

    @GetMapping("/puntos/{id}/editar")
    public String editarPunto(@PathVariable Long id, Model model) {

        PuntoAtencion punto = puntoRepo.findById(id).orElse(null);
        if (punto == null) return "redirect:/admin/puntos";
        model.addAttribute("punto", punto);
        cargarCajerosConAsignacion(model);
        model.addAttribute("turnoActivo", turnoService.turnoActivo().orElse(null));
        return "admin/punto_form";
    }

    private void cargarCajerosConAsignacion(Model model) {
        model.addAttribute("cajeros", usuarioRepo.findByRol("CAJERO"));
        Map<Long, String> asignaciones = new HashMap<>();
        for (PuntoAtencion p : puntoRepo.findAll()) {
            if (p.getCajero() != null) {
                asignaciones.put(p.getCajero().getIdUsuario(), p.getNombre());
            }
        }
        model.addAttribute("asignaciones", asignaciones);
    }

    @PostMapping("/puntos/guardar")
    public String guardarPunto(@RequestParam(required = false) Long idPunto,
                               @RequestParam String nombre,
                               @RequestParam(required = false) String ubicacion,
                               @RequestParam(required = false) Long idCajero,
                               @RequestParam(defaultValue = "false") boolean activo,
                               @RequestParam(defaultValue = "MANUAL") String modo,
                               @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "HH:mm") LocalTime horaInicio,
                               @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "HH:mm") LocalTime horaFin,
                               @RequestParam(required = false) String notas,
                               RedirectAttributes flash) {

        if (idCajero != null) {
            Optional<PuntoAtencion> yaAsignado = puntoRepo.findFirstByCajeroIdUsuario(idCajero);
            if (yaAsignado.isPresent() && (idPunto == null || !yaAsignado.get().getIdPunto().equals(idPunto))) {
                Usuario cajero = usuarioRepo.findById(idCajero).orElse(null);
                flash.addFlashAttribute("error", "El cajero " + (cajero != null ? cajero.getNombreCompleto() : "")
                        + " ya está asignado al punto \"" + yaAsignado.get().getNombre()
                        + "\". Libéralo de ese punto antes de asignarlo aquí.");
                return idPunto != null ? "redirect:/admin/puntos/" + idPunto + "/editar"
                        : "redirect:/admin/puntos/nuevo";
            }
        }

        if ("HORARIO".equals(modo) && horaInicio == null && horaFin == null) {
            flash.addFlashAttribute("error", "Para el modo horario indica al menos una hora (apertura o cierre).");
            return idPunto != null ? "redirect:/admin/puntos/" + idPunto + "/editar" : "redirect:/admin/puntos/nuevo";
        }
        PuntoAtencion p = (idPunto != null) ? puntoRepo.findById(idPunto).orElse(new PuntoAtencion()) : new PuntoAtencion();
        p.setNombre(nombre);
        p.setUbicacion(ubicacion);
        p.setNotas(notas);
        p.setActivo(activo);
        p.setModo(modo);
        p.setHoraInicio(horaInicio);
        p.setHoraFin(horaFin);
        p.setCajero(idCajero != null ? usuarioRepo.findById(idCajero).orElse(null) : null);
        puntoRepo.save(p);
        flash.addFlashAttribute("ok", "Punto \"" + nombre + "\" guardado correctamente.");
        return "redirect:/admin/puntos";
    }

    @GetMapping("/cuentas")
    public String cuentas(Model model, Authentication auth) {
        model.addAttribute("cuentas", usuarioRepo.findByRolIn(List.of("CAJERO", "PRECEPTOR")));
        model.addAttribute("claveTemporal", generarClave());
        model.addAttribute("editando", null);

        model.addAttribute("miCorreo", auth.getName());
        return "admin/cuentas";
    }

    @GetMapping("/cuentas/{id}/editar")
    public String editarCuenta(@PathVariable Long id, Model model, Authentication auth) {
        Usuario cuenta = usuarioRepo.findById(id).orElse(null);
        model.addAttribute("cuentas", usuarioRepo.findByRolIn(List.of("CAJERO", "PRECEPTOR")));
        model.addAttribute("claveTemporal", "");
        model.addAttribute("editando", cuenta);
        model.addAttribute("soloLectura", cuenta != null && cuenta.getActivo() != null && !cuenta.getActivo());
        model.addAttribute("miCorreo", auth.getName());
        return "admin/cuentas";
    }

    @PostMapping("/cuentas/mi-clave")
    public String cambiarMiClave(@RequestParam String claveActual,
                                 @RequestParam String claveNueva,
                                 @RequestParam String claveNuevaConfirma,
                                 Authentication auth,
                                 RedirectAttributes flash) {
        Usuario yo = usuarioRepo.findByCorreo(auth.getName());
        if (yo == null) return "redirect:/admin/cuentas";
        if (!encoder.matches(claveActual, yo.getClave())) {
            flash.addFlashAttribute("error", "La contraseña actual no es correcta.");
            return "redirect:/admin/cuentas";
        }
        if (claveNueva == null || claveNueva.length() < 6) {
            flash.addFlashAttribute("error", "La nueva contraseña debe tener al menos 6 caracteres.");
            return "redirect:/admin/cuentas";
        }
        if (!claveNueva.equals(claveNuevaConfirma)) {
            flash.addFlashAttribute("error", "La nueva contraseña y su confirmación no coinciden.");
            return "redirect:/admin/cuentas";
        }
        yo.setClave(encoder.encode(claveNueva));
        usuarioRepo.save(yo);
        flash.addFlashAttribute("ok", "Tu contraseña de administrador se cambió correctamente.");
        return "redirect:/admin/cuentas";
    }

    @PostMapping("/cuentas/guardar")
    public String guardarCuenta(@RequestParam(required = false) Long idUsuario,
                                @RequestParam String nombreCompleto,
                                @RequestParam(required = false) String codigoUsuario,
                                @RequestParam(required = false) String dni,
                                @RequestParam String correo,
                                @RequestParam(required = false) String claveTemporal,
                                @RequestParam(required = false) String telefono,
                                @RequestParam String rol,
                                @RequestParam(required = false) String pabellon,
                                RedirectAttributes flash) {

        if (correo == null || !correo.trim().toLowerCase().matches("^[a-z0-9._%+-]+@upeu\\.edu\\.pe$")) {
            flash.addFlashAttribute("error",
                    "El correo debe ser institucional: tiene que terminar en @upeu.edu.pe.");
            return idUsuario != null ? "redirect:/admin/cuentas/" + idUsuario + "/editar"
                    : "redirect:/admin/cuentas";
        }

        if (idUsuario != null) {
            Usuario objetivo = usuarioRepo.findById(idUsuario).orElse(null);
            if (objetivo != null && objetivo.getActivo() != null && !objetivo.getActivo()) {
                flash.addFlashAttribute("error", "La cuenta de " + objetivo.getNombreCompleto()
                        + " está desactivada: solo se puede consultar. Actívala primero si necesitas editarla.");
                return "redirect:/admin/cuentas";
            }
        }
        Usuario existente = usuarioRepo.findByCorreo(correo);
        if (existente != null && (idUsuario == null || !existente.getIdUsuario().equals(idUsuario))) {
            flash.addFlashAttribute("error", "Ya existe una cuenta con el correo " + correo);
            return "redirect:/admin/cuentas";
        }
        boolean nueva = (idUsuario == null);
        if (nueva && (claveTemporal == null || claveTemporal.isBlank())) {
            flash.addFlashAttribute("error", "Indica una contraseña temporal para la cuenta nueva.");
            return "redirect:/admin/cuentas";
        }
        Usuario u = nueva ? new Usuario() : usuarioRepo.findById(idUsuario).orElse(new Usuario());
        String[] partes = nombreCompleto.trim().split("\\s+", 2);
        u.setNombre(partes[0]);
        u.setApellido(partes.length > 1 ? partes[1] : "");
        u.setCodigoUsuario(codigoUsuario == null || codigoUsuario.isBlank() ? null : codigoUsuario.trim());
        u.setDni(dni);
        u.setCorreo(correo);
        u.setTelefono(telefono);
        u.setRol(rol);
        u.setPabellon("PRECEPTOR".equals(rol) ? pabellon : null);
        if (claveTemporal != null && !claveTemporal.isBlank()) {
            u.setClave(encoder.encode(claveTemporal));
        }
        if (u.getActivo() == null) u.setActivo(true);
        usuarioRepo.save(u);
        flash.addFlashAttribute("ok", nueva
                ? "Cuenta " + rol + " creada para " + nombreCompleto + ". Contraseña temporal: " + claveTemporal
                : "Cuenta de " + nombreCompleto + " actualizada"
                + (claveTemporal != null && !claveTemporal.isBlank() ? ". Nueva contraseña: " + claveTemporal : "."));
        return "redirect:/admin/cuentas";
    }

    @PostMapping("/cuentas/{id}/restablecer")
    public String restablecerClave(@PathVariable Long id, RedirectAttributes flash) {
        Usuario u = usuarioRepo.findById(id).orElse(null);
        if (u == null) return "redirect:/admin/cuentas";
        if (u.getActivo() != null && !u.getActivo()) {
            flash.addFlashAttribute("error", "La cuenta está desactivada: actívala antes de restablecer su contraseña.");
            return "redirect:/admin/cuentas/" + id + "/editar";
        }
        String nueva = generarClave();
        u.setClave(encoder.encode(nueva));
        usuarioRepo.save(u);

        flash.addFlashAttribute("claveNueva", nueva);
        flash.addFlashAttribute("ok", "Contraseña de " + u.getNombreCompleto()
                + " restablecida. Compártele la contraseña temporal que aparece abajo.");
        return "redirect:/admin/cuentas/" + id + "/editar";
    }

    @PostMapping("/cuentas/{id}/toggle")
    public String toggleCuenta(@PathVariable Long id) {
        usuarioRepo.findById(id).ifPresent(u -> {
            u.setActivo(u.getActivo() == null || !u.getActivo());
            usuarioRepo.save(u);
        });
        return "redirect:/admin/cuentas";
    }

    @PostMapping("/cuentas/{id}/eliminar")
    public String eliminarCuenta(@PathVariable Long id, RedirectAttributes flash) {
        Usuario u = usuarioRepo.findById(id).orElse(null);
        if (u == null) return "redirect:/admin/cuentas";
        if ("ADMIN".equals(u.getRol())) {
            flash.addFlashAttribute("error", "No se puede eliminar una cuenta de administrador.");
            return "redirect:/admin/cuentas";
        }
        try {
            usuarioRepo.delete(u);
            usuarioRepo.flush();
            flash.addFlashAttribute("ok", "Cuenta de " + u.getNombreCompleto() + " eliminada.");
        } catch (Exception e) {
            flash.addFlashAttribute("error", "No se puede eliminar la cuenta de " + u.getNombreCompleto()
                    + " porque tiene registros asociados (marcaciones, puntos, ausencias...). Desactívala en su lugar.");
        }
        return "redirect:/admin/cuentas";
    }

    @GetMapping({"/eventos", "/eventos/{id}"})
    public String eventos(@PathVariable(required = false) Long id,
                          @RequestParam(required = false) String q,
                          @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate desde,
                          @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate hasta,
                          @RequestParam(name = "semestre", required = false) String semestreParam,
                          Model model) {

        String semestre = semestreService.aplicar(model, semestreParam);
        final LocalDate desdeEf = semestreService.recortarInicio(semestre,
                (desde != null) ? desde : semestreService.fechaPorDefecto(semestre));
        final LocalDate hastaEf = semestreService.recortarFin(semestre,
                (hasta != null && !hasta.isBefore(desdeEf)) ? hasta : desdeEf);
        List<EventoEspecial> eventos = eventoRepo.findAllByOrderByFechaEnvioDesc().stream()
                .filter(e -> "PENDIENTE".equals(e.getEstado())
                        || (e.getFechaEnvio() != null
                            && !e.getFechaEnvio().toLocalDate().isBefore(desdeEf)
                            && !e.getFechaEnvio().toLocalDate().isAfter(hastaEf)))
                .toList();
        model.addAttribute("nav", SemanaNav.de(desdeEf));
        model.addAttribute("desde", desdeEf);
        model.addAttribute("hasta", hastaEf);

        if (q != null && !q.isBlank()) {
            String busca = q.trim().toLowerCase();
            eventos = eventos.stream()
                    .filter(e -> e.getNombre() != null && e.getNombre().toLowerCase().contains(busca))
                    .toList();
        }

        EventoEspecial seleccionado = (id == null) ? null : eventoRepo.findById(id).orElse(null);

        Map<Long, Long> participantesPorEvento = new HashMap<>();
        Map<Long, Integer> excluidosPorEvento = new HashMap<>();
        Map<Long, Integer> justificadosPorEvento = new HashMap<>();
        for (EventoEspecial e : eventos) {
            String pabellon = (e.getUsuario() != null) ? e.getUsuario().getPabellon() : null;
            var suyos = (pabellon == null)
                    ? residenteRepo.findByEstadoOrderByApellidoAsc("ACTIVO")
                    : residenteRepo.findByEstadoAndPabellonOrderByApellidoAsc("ACTIVO", pabellon);

            int nExcluidos = 0, nJustificados = 0;
            long cuentan = 0;
            for (var r : suyos) {
                if (r.estaBorrado()) continue;
                if (e.getExcluidosLista().contains(r.getCodigoAcceso())) { nExcluidos++; continue; }
                if (e.getComida() != null && e.getFechaEvento() != null
                        && reglasComidaService.yaJustificado(r, e.getFechaEvento(), e.getComida())) {
                    nJustificados++;
                    continue;
                }
                cuentan++;
            }
            excluidosPorEvento.put(e.getIdEvento(), nExcluidos);
            justificadosPorEvento.put(e.getIdEvento(), nJustificados);
            participantesPorEvento.put(e.getIdEvento(), cuentan);
        }
        model.addAttribute("participantesPorEvento", participantesPorEvento);
        model.addAttribute("excluidosPorEvento", excluidosPorEvento);
        model.addAttribute("justificadosPorEvento", justificadosPorEvento);
        model.addAttribute("hoy", LocalDate.now());

        model.addAttribute("eventos", eventos);
        model.addAttribute("q", q);

        model.addAttribute("exclusionesPendientes", seleccionado == null
                ? List.of() : incidenciaRepo.exclusionesPendientesDe(seleccionado.getIdEvento()));
        model.addAttribute("seleccionado", seleccionado);
        if (seleccionado != null) {
            var excluidos = seleccionado.getExcluidosLista();
            String pabellon = (seleccionado.getUsuario() != null) ? seleccionado.getUsuario().getPabellon() : null;
            var suyos = (pabellon == null)
                    ? residenteRepo.findByEstadoOrderByApellidoAsc("ACTIVO")
                    : residenteRepo.findByEstadoAndPabellonOrderByApellidoAsc("ACTIVO", pabellon);

            List<com.upeu.comedorupeu.models.Residente> participantes = new ArrayList<>();
            List<com.upeu.comedorupeu.models.Residente> justificadosLista = new ArrayList<>();
            for (com.upeu.comedorupeu.models.Residente r : suyos) {
                if (r.estaBorrado()) continue;
                if (excluidos.contains(r.getCodigoAcceso())) continue;
                if (seleccionado.getComida() != null && seleccionado.getFechaEvento() != null
                        && reglasComidaService.yaJustificado(r, seleccionado.getFechaEvento(), seleccionado.getComida())) {
                    justificadosLista.add(r);
                    continue;
                }
                participantes.add(r);
            }
            model.addAttribute("participantes", participantes);
            model.addAttribute("justificadosLista", justificadosLista);
            model.addAttribute("residenciaEntrega", pabellon);

            List<Map<String, String>> excluidosInfo = new ArrayList<>();
            for (String cod : excluidos) {
                Map<String, String> fila = new HashMap<>();
                fila.put("codigo", cod);
                fila.put("nombre", residenteRepo.findByCodigoAcceso(cod)
                        .map(r -> r.getNombreCompleto()).orElse("(no registrado)"));
                excluidosInfo.add(fila);
            }
            model.addAttribute("excluidosInfo", excluidosInfo);
            model.addAttribute("esPasado", seleccionado.getFechaEvento() != null
                    && seleccionado.getFechaEvento().isBefore(LocalDate.now()));
            model.addAttribute("recibidos", entregaRepo.countByEventoIdEventoAndRecibidoTrue(seleccionado.getIdEvento()));
        }
        return "admin/eventos";
    }

    @GetMapping("/eventos/exportar")
    public org.springframework.http.ResponseEntity<byte[]> exportarEventos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate hasta) throws java.io.IOException {
        List<EventoEspecial> eventos = eventoRepo.findAllByOrderByFechaEnvioDesc();
        if (desde != null) {
            final LocalDate ini = desde;
            final LocalDate fin = (hasta != null && !hasta.isBefore(ini)) ? hasta : ini;
            eventos = eventos.stream()
                    .filter(e -> e.getFechaEnvio() != null
                            && !e.getFechaEnvio().toLocalDate().isBefore(ini)
                            && !e.getFechaEnvio().toLocalDate().isAfter(fin))
                    .toList();
        }
        if (q != null && !q.isBlank()) {
            String busca = q.trim().toLowerCase();
            eventos = eventos.stream()
                    .filter(e -> e.getNombre() != null && e.getNombre().toLowerCase().contains(busca))
                    .toList();
        }
        long totalActivos = residenteRepo.countByEstado("ACTIVO");
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<String[]> datos = new ArrayList<>();
        int n = 1;
        for (EventoEspecial e : eventos) {
            int excluidos = e.getExcluidosLista().size();
            datos.add(new String[]{
                    String.valueOf(n++),
                    e.getFechaEvento() == null ? "—" : f.format(e.getFechaEvento()),
                    e.getNombre(),
                    e.getUsuario() == null ? "—" : e.getUsuario().getNombreCompleto(),
                    String.valueOf(Math.max(0, totalActivos - excluidos)),
                    String.valueOf(excluidos),
                    "PENDIENTE".equals(e.getEstado()) ? "Pendiente"
                            : ("APROBADO".equals(e.getEstado()) ? "Aprobado" : "Rechazado")
            });
        }
        String filtros = (q == null || q.isBlank()) ? "Todos las entregas" : "Búsqueda: " + q;
        byte[] xlsx = excelService.exportarTabla("COMEDOR UPEU — Raciones para llevar", filtros,
                new String[]{"N°", "Fecha", "Evento", "Enviado por", "Participantes", "Excluidos", "Estado"},
                datos);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"eventos_" + LocalDate.now() + ".xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    private List<EventoEspecial> comidasDelEvento(EventoEspecial e) {
        if (e.getGrupoEvento() == null || e.getGrupoEvento().isBlank()) return List.of(e);
        List<EventoEspecial> grupo = eventoRepo.findByGrupoEvento(e.getGrupoEvento());
        return grupo.isEmpty() ? List.of(e) : grupo;
    }

    private String textoComidas(List<EventoEspecial> grupo) {
        if (grupo.size() == 1) return "";
        return " (" + grupo.size() + " comidas: "
                + String.join(", ", grupo.stream().map(EventoEspecial::getComidaTexto).toList()) + ")";
    }

    @PostMapping("/eventos/{id}/aprobar")
    public String aprobarEvento(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        eventoRepo.findById(id).ifPresent(e -> {
            List<EventoEspecial> grupo = comidasDelEvento(e);
            for (EventoEspecial comida : grupo) {
                comida.setEstado("APROBADO");
                comida.setRevisor(usuarioActual(auth));
                eventoRepo.save(comida);
            }
            flash.addFlashAttribute("ok", "Entrega \"" + e.getNombre() + "\" aprobado" + textoComidas(grupo)
                    + ": cada comida tiene su propio pase de lista para el preceptor.");
        });
        return "redirect:/admin/eventos/" + id;
    }

    @PostMapping("/eventos/{id}/rechazar")
    public String rechazarEvento(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        eventoRepo.findById(id).ifPresent(e -> {
            List<EventoEspecial> grupo = comidasDelEvento(e);
            for (EventoEspecial comida : grupo) {
                comida.setEstado("RECHAZADO");
                comida.setRevisor(usuarioActual(auth));
                eventoRepo.save(comida);
            }
            flash.addFlashAttribute("ok", "Entrega \"" + e.getNombre() + "\" rechazado" + textoComidas(grupo) + ".");
        });
        return "redirect:/admin/eventos/" + id;
    }

    @PostMapping("/eventos/{id}/eliminar")
    public String eliminarEvento(@PathVariable Long id, RedirectAttributes flash) {
        flash.addFlashAttribute("error", "Las entregas solo los puede eliminar el preceptor que los envió. "
                + "El administrador puede aprobarlos o rechazarlos, no borrarlos.");
        return "redirect:/admin/eventos/" + id;
    }

    @PostMapping({"/eventos/{id}/excluir", "/eventos/{id}/incluir"})
    public String listaDeEventoNoSeToca(@PathVariable Long id, RedirectAttributes flash) {
        flash.addFlashAttribute("error", "Quién participa lo decide el preceptor que envió la entrega, "
                + "mientras siga pendiente. El administrador la aprueba o la rechaza tal como llegó.");
        return "redirect:/admin/eventos/" + id;
    }

    @GetMapping("/peticiones")
    public String peticiones(@RequestParam(defaultValue = "TODAS") String filtro,
                             @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate desde,
                             @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate hasta,
                             @RequestParam(defaultValue = "RECIENTES") String orden,
                             @RequestParam(name = "semestre", required = false) String semestreParam,
                             Model model) {

        String semestre = semestreService.aplicar(model, semestreParam);
        final LocalDate desdeEf = semestreService.recortarInicio(semestre,
                (desde != null) ? desde : semestreService.fechaPorDefecto(semestre));
        final LocalDate hastaEf = semestreService.recortarFin(semestre,
                (hasta != null && !hasta.isBefore(desdeEf)) ? hasta : desdeEf);

        model.addAttribute("nav", SemanaNav.de(desdeEf));
        var lista = ("TODAS".equals(filtro)
                ? incidenciaRepo.findTop30ByOrderByFechaHoraDesc()
                : incidenciaRepo.findTop30ByTipoOrderByFechaHoraDesc(filtro))
                .stream()
                .filter(i -> !"EXCLUSION".equals(i.getTipo()))

                .filter(i -> !i.getFechaHora().toLocalDate().isBefore(desdeEf))
                .filter(i -> !i.getFechaHora().toLocalDate().isAfter(hastaEf))
                .toList();

        if ("ANTIGUOS".equals(orden)) {
            lista = new ArrayList<>(lista);
            java.util.Collections.reverse(lista);
        }
        model.addAttribute("incidencias", lista);
        model.addAttribute("filtro", filtro);

        model.addAttribute("desde", desdeEf);
        model.addAttribute("hasta", hastaEf);
        model.addAttribute("orden", orden);
        return "admin/peticiones";
    }

    private String textoTipoIncidencia(String tipo) {
        return switch (tipo == null ? "" : tipo) {
            case "ALTERACION" -> "Ración alterada";
            case "RESERVA_ALTERADA" -> "Reserva alterada";
            case "PRECEPTOR" -> "Aviso de preceptor";
            default -> "Reporte de cajero";
        };
    }

    @GetMapping("/peticiones/exportar")
    public org.springframework.http.ResponseEntity<byte[]> exportarPeticiones(
            @RequestParam(defaultValue = "TODAS") String filtro,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "RECIENTES") String orden) throws java.io.IOException {

        final LocalDate desdeEf = (desde != null) ? desde : LocalDate.now();
        final LocalDate hastaEf = (hasta != null && !hasta.isBefore(desdeEf)) ? hasta : desdeEf;
        var lista = ("TODAS".equals(filtro)
                ? incidenciaRepo.findTop30ByOrderByFechaHoraDesc()
                : incidenciaRepo.findTop30ByTipoOrderByFechaHoraDesc(filtro))
                .stream()
                .filter(i -> !"EXCLUSION".equals(i.getTipo()))
                .filter(i -> !i.getFechaHora().toLocalDate().isBefore(desdeEf))
                .filter(i -> !i.getFechaHora().toLocalDate().isAfter(hastaEf))
                .toList();
        if ("ANTIGUOS".equals(orden)) {
            lista = new ArrayList<>(lista);
            java.util.Collections.reverse(lista);
        }

        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.format.DateTimeFormatter h = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        List<String[]> datos = new ArrayList<>();
        int n = 1;
        for (Incidencia i : lista) {
            datos.add(new String[]{
                    String.valueOf(n++),
                    f.format(i.getFechaHora()),
                    h.format(i.getFechaHora()),
                    textoTipoIncidencia(i.getTipo()),
                    i.getUsuario() == null ? "Sistema" : i.getUsuario().getNombreCompleto(),
                    i.getPunto() == null ? "—" : i.getPunto().getNombre(),
                    i.getDescripcion() == null ? "" : i.getDescripcion(),
                    Boolean.TRUE.equals(i.getAtendida()) ? "Atendida" : "Pendiente"
            });
        }
        String filtros = "Período: " + f.format(desdeEf)
                + (hastaEf.equals(desdeEf) ? "" : " al " + f.format(hastaEf))
                + "   Tipo: " + ("TODAS".equals(filtro) ? "Todas" : textoTipoIncidencia(filtro));
        byte[] xlsx = excelService.exportarTabla("COMEDOR UPEU — Incidencias y Avisos", filtros,
                new String[]{"N°", "Fecha", "Hora", "Tipo", "Remitente", "Punto", "Descripción", "Estado"},
                datos);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"incidencias_" + desdeEf + ".xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    @PostMapping("/peticiones/{id}/excluir")
    public String atenderExclusion(@PathVariable Long id, RedirectAttributes flash) {
        incidenciaRepo.findById(id).ifPresent(i -> {
            if (!"EXCLUSION".equals(i.getTipo()) || i.getRefEvento() == null || i.getRefCodigo() == null) return;
            eventoRepo.findById(i.getRefEvento()).ifPresent(e -> {
                Set<String> ex = new LinkedHashSet<>(e.getExcluidosLista());
                ex.add(i.getRefCodigo());
                e.setExcluidos(String.join(",", ex));
                eventoRepo.save(e);
            });
            i.setAtendida(true);
            incidenciaRepo.save(i);
            flash.addFlashAttribute("ok", "Solicitud atendida: " + i.getRefCodigo() + " excluido de la entrega.");
        });

        Long idEvento = incidenciaRepo.findById(id).map(Incidencia::getRefEvento).orElse(null);
        return idEvento != null ? "redirect:/admin/eventos/" + idEvento : "redirect:/admin/eventos";
    }

    private String volverAPeticiones(String desde, String hasta, String filtro) {
        StringBuilder url = new StringBuilder("redirect:/admin/peticiones?filtro=")
                .append(filtro == null || filtro.isBlank() ? "TODAS" : filtro);
        if (desde != null && !desde.isBlank()) url.append("&desde=").append(desde);
        if (hasta != null && !hasta.isBlank()) url.append("&hasta=").append(hasta);
        return url.toString();
    }

    @PostMapping("/peticiones/{id}/revisar")
    public String revisarPeticion(@PathVariable Long id,
                                  @RequestParam(required = false) String desde,
                                  @RequestParam(required = false) String hasta,
                                  @RequestParam(required = false) String filtro) {
        incidenciaRepo.findById(id).ifPresent(i -> {
            i.setAtendida(true);
            incidenciaRepo.save(i);
        });
        return volverAPeticiones(desde, hasta, filtro);
    }

    @PostMapping("/peticiones/{id}/eliminar")
    public String eliminarPeticion(@PathVariable Long id,
                                   @RequestParam(required = false) String desde,
                                   @RequestParam(required = false) String hasta,
                                   @RequestParam(required = false) String filtro,
                                   RedirectAttributes flash) {
        incidenciaRepo.deleteById(id);
        flash.addFlashAttribute("ok", "Incidencia eliminada.");
        return volverAPeticiones(desde, hasta, filtro);
    }

    private String generarClave() {
        String[] palabras = {"UPeU", "Comedor", "Residencia", "Acceso", "Turno", "Racion", "Cocina"};
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(palabras[rnd.nextInt(palabras.length)]);
        sb.append('#');
        for (int i = 0; i < 4; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }
}
