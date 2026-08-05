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

        Map<String, ProgramacionHorario> celdas = new HashMap<>();
        for (ProgramacionHorario p : programacionRepo.findByObjetivoAndDiaSemana(CELDA, diaSel)) {
            if (p.getPunto() != null && p.getTipoTurno() != null) {
                celdas.put(p.getTipoTurno() + "-" + p.getPunto().getIdPunto(), p);
            }
        }

        Map<String, ProgramacionHorario> horariosTurno = new HashMap<>();
        for (ProgramacionHorario p : programacionRepo.findByObjetivoAndDiaSemana("TURNO", diaSel)) {
            if (p.getTipoTurno() != null) horariosTurno.put(p.getTipoTurno(), p);
        }

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
                                 RedirectAttributes flash) {
        PuntoAtencion punto = puntoRepo.findById(idPunto).orElse(null);
        if (punto == null) return "redirect:/admin/programar";
        ProgramacionHorario celda = programacionRepo
                .findFirstByObjetivoAndPuntoIdPuntoAndTipoTurnoAndDiaSemana(CELDA, idPunto, tipoTurno, diaSemana)
                .orElseGet(ProgramacionHorario::new);
        celda.setObjetivo(CELDA);
        celda.setPunto(punto);
        celda.setTipoTurno(tipoTurno);
        celda.setDiaSemana(diaSemana);

        boolean quedaQuitada = !Boolean.FALSE.equals(celda.getActivo());
        celda.setActivo(!quedaQuitada);
        programacionRepo.save(celda);
        flash.addFlashAttribute("ok", punto.getNombre() + (quedaQuitada
                ? " quedó QUITADA del " + tipoTurno.toLowerCase() + " de los " + nombreDia(diaSemana)
                  + ": el horario del turno ya no se le aplicará (no se eliminó nada)."
                : " vuelve a estar incluida en el " + tipoTurno.toLowerCase() + " de los " + nombreDia(diaSemana) + "."));
        return "redirect:/admin/programar?dia=" + diaSemana;
    }

    private static String textoHora(LocalTime h) {
        return h == null ? "--:--" : h.toString();
    }

    private static LocalTime iniDe(ProgramacionHorario c) {
        return c.getHoraInicio() != null ? c.getHoraInicio() : LocalTime.MIN;
    }

    private static LocalTime finDe(ProgramacionHorario c) {
        return c.getHoraFin() != null ? c.getHoraFin() : LocalTime.MAX;
    }

    private boolean hayConflicto(PuntoAtencion punto) {
        List<ProgramacionHorario> hoy = programacionRepo
                .findByObjetivoAndPuntoIdPuntoAndDiaSemana(CELDA, punto.getIdPunto(), diaDeHoy())
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

        Usuario esperado = cajeroEsperadoAhora(hoy);
        boolean cajeroDistinto = esperado != null
                && (punto.getCajero() == null
                || !esperado.getIdUsuario().equals(punto.getCajero().getIdUsuario()));
        return horarioDistinto || aperturaDistinta || cajeroDistinto;
    }

    private Usuario cajeroEsperadoAhora(List<ProgramacionHorario> celdasHoy) {
        LocalTime ahora = LocalTime.now();
        return celdasHoy.stream()
                .filter(c -> c.getCajero() != null
                        && !ahora.isBefore(iniDe(c)) && !ahora.isAfter(finDe(c)))
                .map(ProgramacionHorario::getCajero)
                .findFirst()
                .orElseGet(() -> celdasHoy.stream()
                        .filter(c -> c.getCajero() != null)
                        .map(ProgramacionHorario::getCajero)
                        .findFirst().orElse(null));
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
        List<ProgramacionHorario> hoy = programacionRepo
                .findByObjetivoAndPuntoIdPuntoAndDiaSemana(CELDA, punto.getIdPunto(), diaDeHoy())
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

        punto.setUltimaAccionManual(null);
        puntoRepo.save(punto);
    }

    @PostMapping("/celda")
    public String guardarCelda(@RequestParam Long idPunto,
                               @RequestParam String tipoTurno,
                               @RequestParam Integer diaSemana,
                               @RequestParam(required = false) Long idCajero,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime horaInicio,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime horaFin,
                               RedirectAttributes flash) {
        PuntoAtencion punto = puntoRepo.findById(idPunto).orElse(null);
        if (punto == null) return "redirect:/admin/programar";

        ProgramacionHorario celda = programacionRepo
                .findFirstByObjetivoAndPuntoIdPuntoAndTipoTurnoAndDiaSemana(CELDA, idPunto, tipoTurno, diaSemana)
                .orElseGet(ProgramacionHorario::new);
        celda.setObjetivo(CELDA);
        celda.setPunto(punto);
        celda.setTipoTurno(tipoTurno);
        celda.setDiaSemana(diaSemana);

        if (horaInicio == null && horaFin == null) {
            if (celda.getIdProgramacion() != null) programacionRepo.delete(celda);
            flash.addFlashAttribute("ok", "Se quitó la programación de " + punto.getNombre()
                    + " en el " + tipoTurno.toLowerCase() + " de los " + nombreDia(diaSemana) + ".");
            return "redirect:/admin/programar?dia=" + diaSemana;
        }
        if (horaInicio != null && horaFin != null && horaFin.isBefore(horaInicio)) {
            flash.addFlashAttribute("error", "La hora de cierre debe ser posterior a la de apertura.");
            return "redirect:/admin/programar?dia=" + diaSemana;
        }

        LocalTime miIni = (horaInicio != null) ? horaInicio : LocalTime.MIN;
        LocalTime miFin = (horaFin != null) ? horaFin : LocalTime.MAX;

        Usuario cajeroElegido = (idCajero == null) ? null : usuarioRepo.findById(idCajero).orElse(null);
        if (cajeroElegido != null) {
            for (ProgramacionHorario otra : programacionRepo.findByObjetivoAndDiaSemana(CELDA, diaSemana)) {
                boolean mismaCelda = otra.getIdProgramacion() != null
                        && otra.getIdProgramacion().equals(celda.getIdProgramacion());
                boolean otroPunto = otra.getPunto() != null
                        && !otra.getPunto().getIdPunto().equals(idPunto);
                boolean mismaPersona = otra.getCajero() != null
                        && otra.getCajero().getIdUsuario().equals(cajeroElegido.getIdUsuario());
                boolean seCruzan = !miFin.isBefore(iniDe(otra)) && !miIni.isAfter(finDe(otra));
                if (!mismaCelda && otroPunto && mismaPersona && seCruzan) {
                    flash.addFlashAttribute("error", cajeroElegido.getNombreCompleto()
                            + " ya está asignado en " + otra.getPunto().getNombre()
                            + " de " + otra.getHoraInicio() + " a " + otra.getHoraFin()
                            + " los " + nombreDia(diaSemana) + ": no puede estar en dos entradas a la vez.");
                    return "redirect:/admin/programar?dia=" + diaSemana;
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
        return "redirect:/admin/programar?dia=" + diaSemana;
    }

    @PostMapping("/copiar-dia")
    public String copiarDia(@RequestParam Integer diaSemana,
                            jakarta.servlet.http.HttpSession session,
                            RedirectAttributes flash) {
        boolean hayAlgo = !programacionRepo.findByObjetivoAndDiaSemana(CELDA, diaSemana).isEmpty()
                || !programacionRepo.findByObjetivoAndDiaSemana("TURNO", diaSemana).isEmpty();
        if (!hayAlgo) {
            flash.addFlashAttribute("error", "Los " + nombreDia(diaSemana) + " no tienen nada programado que copiar.");
            return "redirect:/admin/programar?dia=" + diaSemana;
        }
        session.setAttribute("diaCopiado", diaSemana);
        flash.addFlashAttribute("ok", "Programación de los " + nombreDia(diaSemana)
                + " copiada. Navega al día donde quieras pegarla y pulsa \"Pegar Día\".");
        return "redirect:/admin/programar?dia=" + diaSemana;
    }

    @PostMapping("/pegar-dia")
    public String pegarDia(@RequestParam Integer diaSemana,
                           jakarta.servlet.http.HttpSession session,
                           RedirectAttributes flash) {
        Object copiado = session.getAttribute("diaCopiado");
        if (copiado == null) {
            flash.addFlashAttribute("error", "Primero usa \"Copiar Día\" en el día que quieras duplicar.");
            return "redirect:/admin/programar?dia=" + diaSemana;
        }
        int origen = (Integer) copiado;
        if (origen == diaSemana) {
            flash.addFlashAttribute("error", "Estás en el mismo día que copiaste: navega a otro día para pegar.");
            return "redirect:/admin/programar?dia=" + diaSemana;
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

        for (ProgramacionHorario o : programacionRepo.findByObjetivoAndDiaSemana(CELDA, origen)) {
            ProgramacionHorario c = programacionRepo
                    .findFirstByObjetivoAndPuntoIdPuntoAndTipoTurnoAndDiaSemana(
                            CELDA, o.getPunto().getIdPunto(), o.getTipoTurno(), diaSemana)
                    .orElseGet(ProgramacionHorario::new);
            c.setObjetivo(CELDA);
            c.setPunto(o.getPunto());
            c.setTipoTurno(o.getTipoTurno());
            c.setDiaSemana(diaSemana);
            c.setHoraInicio(o.getHoraInicio());
            c.setHoraFin(o.getHoraFin());
            c.setCajero(o.getCajero());
            c.setActivo(o.getActivo());
            programacionRepo.save(c);
            pegadas++;
        }
        flash.addFlashAttribute("ok", "Pegado: la programación de los " + nombreDia(origen)
                + " ahora también aplica a los " + nombreDia(diaSemana) + " (" + pegadas + " casilla(s)).");
        return "redirect:/admin/programar?dia=" + diaSemana;
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
        List<ProgramacionHorario> hoy = programacionRepo
                .findByObjetivoAndPuntoIdPuntoAndDiaSemana(CELDA, idPunto, diaDeHoy());
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
