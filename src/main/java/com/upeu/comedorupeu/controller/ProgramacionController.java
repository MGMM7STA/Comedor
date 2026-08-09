package com.upeu.comedorupeu.controller;

import com.upeu.comedorupeu.models.ProgramacionHorario;
import com.upeu.comedorupeu.models.PuntoAtencion;
import com.upeu.comedorupeu.models.Usuario;
import com.upeu.comedorupeu.repository.ProgramacionHorarioRepository;
import com.upeu.comedorupeu.repository.PuntoAtencionRepository;
import com.upeu.comedorupeu.repository.UsuarioRepository;
import com.upeu.comedorupeu.services.TurnoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/programar")
public class ProgramacionController {

    private static final String CELDA = "TURNO_PUNTO";

    private static final List<String> DIAS = List.of(
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo");

    private final ProgramacionHorarioRepository programacionRepo;
    private final PuntoAtencionRepository puntoRepo;
    private final UsuarioRepository usuarioRepo;

    private final TurnoService turnoService;

    public ProgramacionController(ProgramacionHorarioRepository programacionRepo,
                                  PuntoAtencionRepository puntoRepo,
                                  UsuarioRepository usuarioRepo,
                                  TurnoService turnoService) {
        this.programacionRepo = programacionRepo;
        this.puntoRepo = puntoRepo;
        this.usuarioRepo = usuarioRepo;
        this.turnoService = turnoService;
    }

    private int diaDeHoy() {
        return java.time.LocalDate.now().getDayOfWeek().getValue();
    }

    @GetMapping
    public String programar(@RequestParam(required = false) Integer dia,
                            @RequestParam(defaultValue = "0") int semana,

                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate ir,
                            jakarta.servlet.http.HttpSession session,
                            Model model) {

        if (ir != null) {
            java.time.LocalDate hoy = java.time.LocalDate.now();
            if (ir.isBefore(hoy)) ir = hoy;
            java.time.LocalDate lunesActual = hoy.minusDays(diaDeHoy() - 1L);
            java.time.LocalDate lunesDestino = ir.minusDays(ir.getDayOfWeek().getValue() - 1L);
            semana = (int) java.time.temporal.ChronoUnit.WEEKS.between(lunesActual, lunesDestino);
            dia = ir.getDayOfWeek().getValue();
        }

        if (semana < 0) semana = 0;
        int diaSel = (dia == null || dia < 1 || dia > 7) ? diaDeHoy() : dia;

        model.addAttribute("diaCopiado", session.getAttribute("diaCopiado"));

        if (semana == 0 && diaSel < diaDeHoy()) diaSel = diaDeHoy();

        List<PuntoAtencion> puntos = puntoRepo.vigentes();

        java.time.LocalDate fechaSel = java.time.LocalDate.now()
                .minusDays(diaDeHoy() - 1L).plusWeeks(semana).plusDays(diaSel - 1L);
        model.addAttribute("fechaSel", fechaSel);

        Map<String, ProgramacionHorario> celdas = agendaService.celdasDe(fechaSel);

        Map<String, ProgramacionHorario> horariosTurno = new HashMap<>();
        for (ProgramacionHorario p : programacionRepo.findByObjetivoAndDiaSemana("TURNO", diaSel)) {
            if (p.getTipoTurno() != null) horariosTurno.put(p.getTipoTurno(), p);
        }

        Map<String, Boolean> heredada = new HashMap<>();
        celdas.forEach((k, c) -> heredada.put(k, agendaService.esHeredada(c)));
        model.addAttribute("heredada", heredada);

        Map<Long, String> turnoEnCurso = new HashMap<>();
        if (semana == 0 && diaSel == diaDeHoy()) {
            LocalTime ahora = LocalTime.now();
            for (PuntoAtencion p : puntos) {
                if (!p.isOperativo()) continue;
                if (p.getTurnoManual() != null) {
                    turnoEnCurso.put(p.getIdPunto(), p.getTurnoManual());
                    continue;
                }
                agendaService.listaDe(fechaSel, p.getIdPunto()).stream()
                        .filter(c -> c.getHoraInicio() != null || c.getHoraFin() != null)
                        .filter(c -> !ahora.isBefore(iniDe(c)) && !ahora.isAfter(finDe(c)))
                        .findFirst()
                        .ifPresent(c -> turnoEnCurso.put(p.getIdPunto(), c.getTipoTurno()));
            }
        }
        model.addAttribute("turnoEnCurso", turnoEnCurso);

        Map<Long, Boolean> conflictos = new HashMap<>();

        Map<String, Boolean> conflictosTurno = new HashMap<>();
        if (semana == 0 && diaSel == diaDeHoy()) {
            for (PuntoAtencion p : puntos) {
                conflictos.put(p.getIdPunto(), hayConflicto(p));
            }
            for (var turno : turnoService.turnosDeHoy()) {
                boolean tieneAgenda = turnoService.ventanaDe(turno.getTipo()).isPresent();
                boolean difiere = tieneAgenda
                        && turnoService.estaAtendiendo(turno) != turnoService.agendaAbiertaAhora(turno.getTipo());
                conflictosTurno.put(turno.getTipo(), difiere);
            }
        }
        model.addAttribute("conflictosTurno", conflictosTurno);

        java.time.LocalDate lunes = java.time.LocalDate.now()
                .minusDays(diaDeHoy() - 1L).plusWeeks(semana);
        java.time.LocalDate domingo = lunes.plusDays(6);
        java.time.format.DateTimeFormatter corto = java.time.format.DateTimeFormatter
                .ofPattern("dd MMM", java.util.Locale.forLanguageTag("es-PE"));
        model.addAttribute("rotuloSemana", "Lun " + corto.format(lunes)
                + " – Dom " + corto.format(domingo) + ", " + domingo.getYear());
        model.addAttribute("semana", semana);

        java.util.List<java.time.LocalDate> fechasSemana = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) fechasSemana.add(lunes.plusDays(i));
        model.addAttribute("fechasSemana", fechasSemana);

        model.addAttribute("diaPasado", semana == 0 && diaSel < diaDeHoy());

        model.addAttribute("puntos", puntos);
        model.addAttribute("tiposTurno", TurnoService.TIPOS);
        model.addAttribute("dias", DIAS);
        model.addAttribute("diaSel", diaSel);
        model.addAttribute("hoyDia", diaDeHoy());
        model.addAttribute("celdas", celdas);
        model.addAttribute("horariosTurno", horariosTurno);
        model.addAttribute("conflictos", conflictos);
        model.addAttribute("cajeros", usuarioRepo.findByRol("CAJERO"));
        return "admin/programar";
    }

    @PostMapping("/turno")
    public String guardarHorarioTurno(@RequestParam String tipoTurno,
                                      @RequestParam Integer diaSemana,
                                      @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime horaInicio,
                                      @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime horaFin,
                                      RedirectAttributes flash) {
        ProgramacionHorario celda = programacionRepo
                .findFirstByObjetivoAndTipoTurnoAndDiaSemana("TURNO", tipoTurno, diaSemana)
                .orElseGet(ProgramacionHorario::new);
        celda.setObjetivo("TURNO");
        celda.setTipoTurno(tipoTurno);
        celda.setDiaSemana(diaSemana);

        if (horaInicio == null && horaFin == null) {
            if (celda.getIdProgramacion() != null) programacionRepo.delete(celda);
            flash.addFlashAttribute("ok", "Se quitó el horario del "
                    + tipoTurno.toLowerCase() + " de los " + nombreDia(diaSemana) + ".");
            return "redirect:/admin/programar?dia=" + diaSemana;
        }
        if (horaInicio != null && horaFin != null && horaFin.isBefore(horaInicio)) {
            flash.addFlashAttribute("error", "La hora de cierre del turno debe ser posterior a la de apertura.");
            return "redirect:/admin/programar?dia=" + diaSemana;
        }
        celda.setHoraInicio(horaInicio);
        celda.setHoraFin(horaFin);
        celda.setActivo(true);
        programacionRepo.save(celda);

        if (diaSemana == diaDeHoy()) {
            turnoService.guardarConfig(tipoTurno, true, horaInicio, horaFin);
            turnoService.reaplicarAgenda(tipoTurno);
        }
        flash.addFlashAttribute("ok", "Plantilla del " + tipoTurno.toLowerCase() + " de los "
                + nombreDia(diaSemana) + ": " + textoHora(horaInicio) + " - " + textoHora(horaFin)
                + ". Las entradas ya programadas conservan su horario; las nuevas que agregues heredarán esta plantilla.");
        return "redirect:/admin/programar?dia=" + diaSemana;
    }

    @PostMapping("/excluir-entrada")
    public String excluirEntrada(@RequestParam Long idPunto,
                                 @RequestParam String tipoTurno,
                                 @RequestParam Integer diaSemana,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate fecha,
                                 RedirectAttributes flash) {
        PuntoAtencion punto = puntoRepo.findById(idPunto).orElse(null);
        if (punto == null) return "redirect:/admin/programar";

        if (fecha != null) {
            var plantilla = programacionRepo
                    .findFirstByObjetivoAndPuntoIdPuntoAndTipoTurnoAndDiaSemanaAndFechaIsNull(
                            CELDA, idPunto, tipoTurno, diaSemana);

            var propia = programacionRepo
                    .findFirstByObjetivoAndPuntoIdPuntoAndTipoTurnoAndFecha(CELDA, idPunto, tipoTurno, fecha);

            if (plantilla.isPresent()) {

                ProgramacionHorario anula = propia.orElseGet(ProgramacionHorario::new);
                anula.setObjetivo(CELDA);
                anula.setPunto(punto);
                anula.setTipoTurno(tipoTurno);
                anula.setDiaSemana(diaSemana);
                anula.setFecha(fecha);
                anula.setHoraInicio(null);
                anula.setHoraFin(null);
                anula.setCajero(null);
                anula.setActivo(false);
                programacionRepo.save(anula);
            } else {
                propia.ifPresent(programacionRepo::delete);
            }
        } else {
            programacionRepo.findFirstByObjetivoAndPuntoIdPuntoAndTipoTurnoAndDiaSemanaAndFechaIsNull(
                            CELDA, idPunto, tipoTurno, diaSemana)
                    .ifPresent(programacionRepo::delete);
        }

        flash.addFlashAttribute("ok", punto.getNombre() + " salió del " + tipoTurno.toLowerCase()
                + (fecha != null ? " del " + fecha : " de los " + nombreDia(diaSemana))
                + ". Puedes volver a añadirla con el botón +.");
        return volverAlDia(diaSemana, fecha);
    }

    private static String textoHora(LocalTime h) {
        return h == null ? "--:--" : h.toString();
    }

    private String volverAlDia(Integer diaSemana, java.time.LocalDate fecha) {
        if (fecha != null) return "redirect:/admin/programar?ir=" + fecha;
        return "redirect:/admin/programar?dia=" + diaSemana;
    }

    private java.time.LocalDate proximaFechaDe(Integer diaSemana) {
        java.time.LocalDate hoy = java.time.LocalDate.now();
        java.time.LocalDate lunes = hoy.minusDays(diaDeHoy() - 1L);
        java.time.LocalDate destino = lunes.plusDays(diaSemana - 1L);
        return destino.isBefore(hoy) ? destino.plusWeeks(1) : destino;
    }

    private com.upeu.comedorupeu.services.AgendaService agendaService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setAgendaService(com.upeu.comedorupeu.services.AgendaService agendaService) {
        this.agendaService = agendaService;
    }

    private static boolean seSolapan(LocalTime iniA, LocalTime finA, LocalTime iniB, LocalTime finB) {
        return iniA.isBefore(finB) && iniB.isBefore(finA);
    }

    private static LocalTime iniDe(ProgramacionHorario c) {
        return c.getHoraInicio() != null ? c.getHoraInicio() : LocalTime.MIN;
    }

    private static LocalTime finDe(ProgramacionHorario c) {
        return c.getHoraFin() != null ? c.getHoraFin() : LocalTime.MAX;
    }

    private boolean hayConflicto(PuntoAtencion punto) {
        List<ProgramacionHorario> hoy = agendaService
                .listaDe(java.time.LocalDate.now(), punto.getIdPunto())
                .stream().filter(c -> !Boolean.FALSE.equals(c.getActivo())).toList();
        if (hoy.isEmpty()) return false;

        LocalTime ahora = LocalTime.now();
        ProgramacionHorario vigente = celdaVigente(hoy, ahora);
        ProgramacionHorario referencia = (vigente != null) ? vigente : celdaSiguiente(hoy, ahora);
        if (referencia == null) return false;

        boolean horarioDistinto = !"HORARIO".equals(punto.getModo())
                || !java.util.Objects.equals(referencia.getHoraInicio(), punto.getHoraInicio())
                || !java.util.Objects.equals(referencia.getHoraFin(), punto.getHoraFin());

        boolean aperturaDistinta = (vigente != null) != Boolean.TRUE.equals(punto.getActivo());

        boolean cajeroDistinto = vigente != null && vigente.getCajero() != null
                && (punto.getCajero() == null
                || !vigente.getCajero().getIdUsuario().equals(punto.getCajero().getIdUsuario()));
        return horarioDistinto || aperturaDistinta || cajeroDistinto;
    }

    private ProgramacionHorario celdaVigente(List<ProgramacionHorario> celdas, LocalTime ahora) {
        return celdas.stream()
                .filter(c -> c.getHoraInicio() != null || c.getHoraFin() != null)
                .filter(c -> !ahora.isBefore(iniDe(c)) && !ahora.isAfter(finDe(c)))
                .findFirst().orElse(null);
    }

    private ProgramacionHorario celdaSiguiente(List<ProgramacionHorario> celdas, LocalTime ahora) {
        return celdas.stream()
                .filter(c -> c.getHoraInicio() != null && c.getHoraInicio().isAfter(ahora))
                .min(java.util.Comparator.comparing(ProgramacionHorario::getHoraInicio))
                .orElse(null);
    }

    private void aplicarAgendaDeHoy(PuntoAtencion punto) {
        List<ProgramacionHorario> hoy = agendaService
                .listaDe(java.time.LocalDate.now(), punto.getIdPunto())
                .stream().filter(c -> !Boolean.FALSE.equals(c.getActivo())).toList();
        if (hoy.isEmpty()) return;
        punto.setModo("HORARIO");

        LocalTime ahora = LocalTime.now();
        ProgramacionHorario vigente = celdaVigente(hoy, ahora);
        ProgramacionHorario referencia = (vigente != null) ? vigente : celdaSiguiente(hoy, ahora);

        if (referencia != null) {
            punto.setHoraInicio(referencia.getHoraInicio());
            punto.setHoraFin(referencia.getHoraFin());
            if (referencia.getCajero() != null) punto.setCajero(referencia.getCajero());
        }
        punto.setActivo(vigente != null);

        if (vigente != null && vigente.getCajero() != null) {
            liberarOperador(punto, vigente.getCajero().getIdUsuario());
        }
        if (vigente != null) {
            turnoService.abrirPorAgenda(vigente.getTipoTurno());
        }

        punto.setTurnoManual(null);
        punto.setUltimaAccionManual(null);
        puntoRepo.save(punto);
    }

    private void liberarOperador(PuntoAtencion queAbre, Long idCajero) {
        for (PuntoAtencion otro : puntoRepo.vigentes()) {
            if (otro.getIdPunto().equals(queAbre.getIdPunto())) continue;
            if (!otro.isOperativo() || otro.getCajero() == null) continue;
            if (!otro.getCajero().getIdUsuario().equals(idCajero)) continue;

            otro.setActivo(false);
            otro.setTurnoManual(null);
            otro.setUltimaAccionManual(null);
            puntoRepo.save(otro);
        }
    }

    @PostMapping("/celda")
    public String guardarCelda(@RequestParam Long idPunto,
                               @RequestParam String tipoTurno,
                               @RequestParam Integer diaSemana,
                               @RequestParam(required = false) Long idCajero,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime horaInicio,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime horaFin,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate fecha,
                               @RequestParam(required = false) Boolean guardarPlantilla,
                               RedirectAttributes flash) {
        PuntoAtencion punto = puntoRepo.findById(idPunto).orElse(null);
        if (punto == null) return "redirect:/admin/programar";

        boolean comoPlantilla = Boolean.TRUE.equals(guardarPlantilla) || fecha == null;
        ProgramacionHorario celda = (comoPlantilla
                ? programacionRepo.findFirstByObjetivoAndPuntoIdPuntoAndTipoTurnoAndDiaSemanaAndFechaIsNull(
                        CELDA, idPunto, tipoTurno, diaSemana)
                : programacionRepo.findFirstByObjetivoAndPuntoIdPuntoAndTipoTurnoAndFecha(
                        CELDA, idPunto, tipoTurno, fecha))
                .orElseGet(ProgramacionHorario::new);
        celda.setObjetivo(CELDA);
        celda.setPunto(punto);
        celda.setTipoTurno(tipoTurno);
        celda.setDiaSemana(diaSemana);
        celda.setFecha(comoPlantilla ? null : fecha);

        if (horaInicio == null && horaFin == null) {

            celda.setHoraInicio(null);
            celda.setHoraFin(null);
            celda.setCajero(null);
            celda.setActivo(true);
            programacionRepo.save(celda);
            flash.addFlashAttribute("ok", "Se borró el horario de " + punto.getNombre()
                    + " en el " + tipoTurno.toLowerCase() + " de los " + nombreDia(diaSemana)
                    + ": la casilla sigue ahí, vacía, lista para volver a llenarla.");
            return volverAlDia(diaSemana, fecha);
        }
        if (horaInicio != null && horaFin != null && horaFin.isBefore(horaInicio)) {
            flash.addFlashAttribute("error", "La hora de cierre debe ser posterior a la de apertura.");
            return volverAlDia(diaSemana, fecha);
        }
        if (idCajero == null) {
            flash.addFlashAttribute("error", "Falta el personal de " + punto.getNombre() + " en el "
                    + tipoTurno.toLowerCase() + " de los " + nombreDia(diaSemana)
                    + ": un turno programado siempre tiene que decir quién atiende.");
            return volverAlDia(diaSemana, fecha);
        }

        LocalTime miIni = (horaInicio != null) ? horaInicio : LocalTime.MIN;
        LocalTime miFin = (horaFin != null) ? horaFin : LocalTime.MAX;

        java.time.LocalDate diaAValidar = (fecha != null) ? fecha : proximaFechaDe(diaSemana);

        for (ProgramacionHorario otra : agendaService.listaDe(diaAValidar, idPunto)) {
            if (Boolean.FALSE.equals(otra.getActivo())) continue;
            if (otra.getIdProgramacion() != null
                    && otra.getIdProgramacion().equals(celda.getIdProgramacion())) continue;
            if (tipoTurno.equals(otra.getTipoTurno())) continue;
            if (otra.getHoraInicio() == null && otra.getHoraFin() == null) continue;

            if (seSolapan(miIni, miFin, iniDe(otra), finDe(otra))) {
                flash.addFlashAttribute("error", "En " + punto.getNombre() + " ese horario se pisa con el "
                        + otra.getTipoTurno().toLowerCase() + " (" + textoHora(otra.getHoraInicio())
                        + " a " + textoHora(otra.getHoraFin()) + ") de los " + nombreDia(diaSemana)
                        + ". Una misma entrada no puede atender dos turnos a la vez: ajusta las horas.");
                return volverAlDia(diaSemana, fecha);
            }
        }

        Usuario cajeroElegido = (idCajero == null) ? null : usuarioRepo.findById(idCajero).orElse(null);
        if (cajeroElegido != null) {
            for (ProgramacionHorario otra : agendaService.listaDe(diaAValidar)) {
                if (Boolean.FALSE.equals(otra.getActivo())) continue;
                boolean mismaCelda = otra.getIdProgramacion() != null
                        && otra.getIdProgramacion().equals(celda.getIdProgramacion());
                boolean otroPunto = otra.getPunto() != null
                        && !otra.getPunto().getIdPunto().equals(idPunto);
                boolean mismaPersona = otra.getCajero() != null
                        && otra.getCajero().getIdUsuario().equals(cajeroElegido.getIdUsuario());
                if (!mismaCelda && otroPunto && mismaPersona
                        && seSolapan(miIni, miFin, iniDe(otra), finDe(otra))) {
                    flash.addFlashAttribute("error", cajeroElegido.getNombreCompleto()
                            + " ya está asignado en " + otra.getPunto().getNombre()
                            + " de " + textoHora(otra.getHoraInicio()) + " a " + textoHora(otra.getHoraFin())
                            + " los " + nombreDia(diaSemana) + ": no puede estar en dos entradas a la vez. "
                            + "Sí puede atender ambas si los horarios no se pisan.");
                    return volverAlDia(diaSemana, fecha);
                }
            }
        }

        celda.setHoraInicio(horaInicio);
        celda.setHoraFin(horaFin);
        celda.setCajero(cajeroElegido);
        celda.setActivo(true);
        programacionRepo.save(celda);

        if (diaSemana == diaDeHoy()) aplicarAgendaDeHoy(punto);

        flash.addFlashAttribute("ok", punto.getNombre() + " · " + tipoTurno.toLowerCase()
                + " de los " + nombreDia(diaSemana) + ": " + textoHora(horaInicio) + " a " + textoHora(horaFin)
                + (celda.getCajero() != null ? " con " + celda.getCajero().getNombreCompleto() : "") + ".");
        return volverAlDia(diaSemana, fecha);
    }

    @PostMapping("/copiar-dia")
    public String copiarDia(@RequestParam Integer diaSemana,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate fecha,
                            jakarta.servlet.http.HttpSession session,
                            RedirectAttributes flash) {
        java.time.LocalDate diaOrigen = (fecha != null) ? fecha : proximaFechaDe(diaSemana);
        boolean hayAlgo = !agendaService.listaDe(diaOrigen).isEmpty()
                || !programacionRepo.findByObjetivoAndDiaSemana("TURNO", diaSemana).isEmpty();
        if (!hayAlgo) {
            flash.addFlashAttribute("error", "Los " + nombreDia(diaSemana) + " no tienen nada programado que copiar.");
            return volverAlDia(diaSemana, fecha);
        }
        session.setAttribute("diaCopiado", diaSemana);
        flash.addFlashAttribute("ok", "Programación de los " + nombreDia(diaSemana)
                + " copiada. Navega al día donde quieras pegarla y pulsa \"Pegar Día\".");
        return volverAlDia(diaSemana, fecha);
    }

    @PostMapping("/pegar-dia")
    public String pegarDia(@RequestParam Integer diaSemana,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate fecha,
                           jakarta.servlet.http.HttpSession session,
                           RedirectAttributes flash) {
        Object copiado = session.getAttribute("diaCopiado");
        if (copiado == null) {
            flash.addFlashAttribute("error", "Primero usa \"Copiar Día\" en el día que quieras duplicar.");
            return volverAlDia(diaSemana, fecha);
        }
        int origen = (Integer) copiado;
        if (origen == diaSemana) {
            flash.addFlashAttribute("error", "Estás en el mismo día que copiaste: navega a otro día para pegar.");
            return volverAlDia(diaSemana, fecha);
        }
        int pegadas = 0;

        for (ProgramacionHorario o : programacionRepo.findByObjetivoAndDiaSemana("TURNO", origen)) {
            ProgramacionHorario c = programacionRepo
                    .findFirstByObjetivoAndTipoTurnoAndDiaSemana("TURNO", o.getTipoTurno(), diaSemana)
                    .orElseGet(ProgramacionHorario::new);
            c.setObjetivo("TURNO");
            c.setTipoTurno(o.getTipoTurno());
            c.setDiaSemana(diaSemana);
            c.setHoraInicio(o.getHoraInicio());
            c.setHoraFin(o.getHoraFin());
            c.setActivo(true);
            programacionRepo.save(c);
            pegadas++;
        }

        java.time.LocalDate fechaOrigen = proximaFechaDe(origen);
        java.time.LocalDate fechaDestino = (fecha != null) ? fecha : proximaFechaDe(diaSemana);

        for (ProgramacionHorario o : agendaService.listaDe(fechaOrigen)) {
            if (o.getPunto() == null) continue;
            ProgramacionHorario c = programacionRepo
                    .findFirstByObjetivoAndPuntoIdPuntoAndTipoTurnoAndFecha(
                            CELDA, o.getPunto().getIdPunto(), o.getTipoTurno(), fechaDestino)
                    .orElseGet(ProgramacionHorario::new);
            c.setObjetivo(CELDA);
            c.setPunto(o.getPunto());
            c.setTipoTurno(o.getTipoTurno());
            c.setDiaSemana(diaSemana);
            c.setFecha(fechaDestino);
            c.setHoraInicio(o.getHoraInicio());
            c.setHoraFin(o.getHoraFin());
            c.setCajero(o.getCajero());
            c.setActivo(true);
            programacionRepo.save(c);
            pegadas++;
        }
        flash.addFlashAttribute("ok", "Pegado: la programación de los " + nombreDia(origen)
                + " ahora también aplica a los " + nombreDia(diaSemana) + " (" + pegadas + " casilla(s)).");
        return volverAlDia(diaSemana, fecha);
    }

    @PostMapping("/reaplicar-turno")
    public String reaplicarTurno(@RequestParam String tipoTurno, RedirectAttributes flash) {
        String resultado = turnoService.reaplicarAgenda(tipoTurno);
        if (resultado == null) {
            flash.addFlashAttribute("error", "El " + tipoTurno.toLowerCase()
                    + " no tiene un horario programado para hoy: no hay nada que reaplicar.");
        } else {
            flash.addFlashAttribute("ok", resultado);
        }
        return "redirect:/admin/programar";
    }

    @PostMapping("/reaplicar")
    public String reaplicar(@RequestParam Long idPunto,
                            @RequestParam(required = false) String volver,
                            RedirectAttributes flash) {
        String destino = "puntos".equals(volver) ? "redirect:/admin/puntos" : "redirect:/admin/programar";
        PuntoAtencion punto = puntoRepo.findById(idPunto).orElse(null);
        if (punto == null) return destino;
        List<ProgramacionHorario> hoy = agendaService.listaDe(java.time.LocalDate.now(), idPunto);
        if (hoy.isEmpty()) {
            flash.addFlashAttribute("error", "No hay programación guardada para hoy en " + punto.getNombre() + ".");
            return destino;
        }
        aplicarAgendaDeHoy(punto);
        flash.addFlashAttribute("ok", "Programación reaplicada en " + punto.getNombre()
                + ": vuelve a regirse por la agenda de hoy.");
        return destino;
    }

    private String nombreDia(Integer dia) {
        if (dia == null || dia < 1 || dia > 7) return "";
        return DIAS.get(dia - 1).toLowerCase();
    }
}
