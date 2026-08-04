package com.upeu.comedorupeu.controller;

import com.upeu.comedorupeu.dto.ValidacionResultado;
import com.upeu.comedorupeu.models.Incidencia;
import com.upeu.comedorupeu.models.Marcacion;
import com.upeu.comedorupeu.models.PuntoAtencion;
import com.upeu.comedorupeu.models.Usuario;
import com.upeu.comedorupeu.repository.ApunteRepository;
import com.upeu.comedorupeu.repository.IncidenciaRepository;
import com.upeu.comedorupeu.repository.MarcacionRepository;
import com.upeu.comedorupeu.repository.PuntoAtencionRepository;
import com.upeu.comedorupeu.repository.ResidenteRepository;
import com.upeu.comedorupeu.repository.SolicitudExtemporaneaRepository;
import com.upeu.comedorupeu.repository.UsuarioRepository;
import com.upeu.comedorupeu.services.TurnoService;
import com.upeu.comedorupeu.services.ValidacionService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/cajero")
public class CajeroController {

    private static final List<String> DECISIONES = List.of("PERMITIDO", "DENEGADO", "JUSTIFICADO");

    private final ValidacionService validacionService;
    private final TurnoService turnoService;
    private final MarcacionRepository marcacionRepo;
    private final PuntoAtencionRepository puntoRepo;
    private final UsuarioRepository usuarioRepo;
    private final IncidenciaRepository incidenciaRepo;
    private final SolicitudExtemporaneaRepository solicitudRepo;
    private final ResidenteRepository residenteRepo;
    private final ApunteRepository apunteRepo;
    private final com.upeu.comedorupeu.repository.TurnoRepository turnoRepo;

    public CajeroController(ValidacionService validacionService, TurnoService turnoService,
                            MarcacionRepository marcacionRepo, PuntoAtencionRepository puntoRepo,
                            UsuarioRepository usuarioRepo, IncidenciaRepository incidenciaRepo,
                            SolicitudExtemporaneaRepository solicitudRepo, ResidenteRepository residenteRepo,
                            ApunteRepository apunteRepo, com.upeu.comedorupeu.repository.TurnoRepository turnoRepo) {
        this.validacionService = validacionService;
        this.turnoService = turnoService;
        this.marcacionRepo = marcacionRepo;
        this.puntoRepo = puntoRepo;
        this.usuarioRepo = usuarioRepo;
        this.incidenciaRepo = incidenciaRepo;
        this.solicitudRepo = solicitudRepo;
        this.residenteRepo = residenteRepo;
        this.apunteRepo = apunteRepo;
        this.turnoRepo = turnoRepo;
    }

    private Usuario usuarioActual(Authentication auth) {
        return usuarioRepo.findByCorreo(auth.getName());
    }

    private PuntoAtencion puntoDelCajero(Usuario cajero) {
        return puntoRepo.findFirstByCajeroIdUsuario(cajero.getIdUsuario()).orElse(null);
    }

    private boolean puntoCerrado(Usuario cajero) {
        PuntoAtencion asignado = puntoDelCajero(cajero);
        return asignado != null && !asignado.isOperativo();
    }

    @GetMapping("/validar")
    public String validar(@RequestParam(required = false) String codigo, Model model, Authentication auth) {
        Usuario cajero = usuarioActual(auth);
        PuntoAtencion punto = puntoDelCajero(cajero);
        var turnoActivo = turnoService.turnoActivoDe(punto).orElse(null);
        boolean sinPunto = (punto == null);
        boolean cerrado = puntoCerrado(cajero);
        boolean bloqueado = sinPunto || cerrado;
        model.addAttribute("turnoActivo", turnoActivo);
        model.addAttribute("punto", punto);
        model.addAttribute("sinPunto", sinPunto);
        model.addAttribute("puntoCerrado", cerrado);

        model.addAttribute("apuntes", apunteRepo.findTop10ByTipoOrderByFechaHoraDesc("AVISO").stream()
                .filter(com.upeu.comedorupeu.models.Apunte::estaVigente)
                .toList());
        model.addAttribute("codigo", codigo);

        if (turnoActivo != null) {
            long servidas = marcacionRepo.countByTurnoIdTurnoAndEstado(turnoActivo.getIdTurno(), "PERMITIDO")
                    + marcacionRepo.countByTurnoIdTurnoAndEstado(turnoActivo.getIdTurno(), "JUSTIFICADO");
            model.addAttribute("racionesServidas", servidas);
            model.addAttribute("racionesPorServir", Math.max(0, residenteRepo.countByEstado("ACTIVO") - servidas));
        }

        if (codigo != null && !codigo.isBlank()) {
            var resultado = validacionService.validar(codigo, punto);
            model.addAttribute("resultado", resultado);

            model.addAttribute("soloConsulta", bloqueado);

            var solGrupo = resultado.getReservaExtra() != null
                    ? resultado.getReservaExtra() : resultado.getSolicitud();
            if (solGrupo != null && solGrupo.getGrupoLote() != null
                    && "PENDIENTE".equals(solGrupo.getEstado())) {
                model.addAttribute("grupoEscaneado", solGrupo.getGrupoLote());
                model.addAttribute("grupoCantidad", solicitudRepo
                        .findByGrupoLoteAndFechaAndEstado(solGrupo.getGrupoLote(),
                                java.time.LocalDate.now(), "PENDIENTE").size());
            }
        }
        return "cajero/validar";
    }

    private void marcarInfraccion(Marcacion previa, String justificacion, Usuario cajero) {
        previa.setAnulada(true);
        previa.setAclaracion(justificacion);
        marcacionRepo.save(previa);

        Incidencia i = new Incidencia();
        i.setUsuario(cajero);
        i.setPunto(puntoDelCajero(cajero));
        i.setTipo("ALTERACION");
        i.setRefCodigo(previa.getResidente().getCodigoAcceso());
        i.setDescripcion("Ración alterada — " + previa.getResidente().getNombreCompleto()
                + " (" + previa.getResidente().getCodigoAcceso() + "), turno " + previa.getTurno().getTipo()
                + " " + previa.getTurno().getFecha() + ". Justificación del cajero: " + justificacion);
        incidenciaRepo.save(i);
    }

    @PostMapping("/entregar-reserva")
    public String entregarReserva(@RequestParam String codigo,
                                  @RequestParam(defaultValue = "RESIDENTE") String entregadoA,
                                  Authentication auth, RedirectAttributes flash) {
        Usuario cajero = usuarioActual(auth);

        if (puntoDelCajero(cajero) == null || puntoCerrado(cajero)) {
            flash.addFlashAttribute("error", "Tu punto no está operativo.");
            return "redirect:/cajero/validar";
        }
        var residente = residenteRepo.findByCodigoAcceso(codigo.trim()).orElse(null);
        if (residente == null) {
            flash.addFlashAttribute("error", "Residente no encontrado.");
            return "redirect:/cajero/validar";
        }
        var solOpt = solicitudRepo.findFirstByResidenteIdResidenteAndFechaAndEstado(
                residente.getIdResidente(), java.time.LocalDate.now(), "PENDIENTE");
        if (solOpt.isEmpty()) {
            flash.addFlashAttribute("error", "El residente no tiene ración reservada pendiente para hoy.");
            return "redirect:/cajero/validar?codigo=" + java.net.URLEncoder.encode(codigo.trim(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var sol = solOpt.get();
        turnoService.turnosDeHoy();
        var turnoReserva = turnoRepo.findByFechaAndTipo(sol.getFecha(), sol.getTipoComida()).orElse(null);
        if (turnoReserva == null) {
            flash.addFlashAttribute("error", "No existe el turno de la reserva.");
            return "redirect:/cajero/validar";
        }

        String receptor = "PRECEPTOR".equals(entregadoA) ? "PRECEPTOR" : "RESIDENTE";
        String receptorTexto = "PRECEPTOR".equals(receptor) ? "el preceptor a cargo" : "el residente";

        Marcacion m = new Marcacion();
        m.setResidente(residente);
        m.setTurno(turnoReserva);
        m.setUsuario(cajero);
        m.setPunto(puntoDelCajero(cajero));
        m.setEstado("PERMITIDO");
        m.setObservacion("Ración reservada (" + sol.getTipoComida() + ") entregada a "
                + receptorTexto + ": " + sol.getMotivo());
        marcacionRepo.save(m);
        sol.setEstado("ATENDIDA");
        sol.setEntregadoA(receptor);

        sol.setFechaHoraEntrega(java.time.LocalDateTime.now());
        solicitudRepo.save(sol);
        flash.addFlashAttribute("ok", "Ración reservada de " + sol.getTipoComida().toLowerCase()
                + " de " + residente.getNombreCompleto() + " entregada a " + receptorTexto + ".");
        flash.addFlashAttribute("deshacer", true);
        return "redirect:/cajero/validar";
    }

    @PostMapping("/entregar-reserva-masiva")
    public String entregarReservaMasiva(@RequestParam String grupo,
                                        Authentication auth, RedirectAttributes flash) {
        Usuario cajero = usuarioActual(auth);
        if (puntoDelCajero(cajero) == null || puntoCerrado(cajero)) {
            flash.addFlashAttribute("error", "Tu punto no está operativo.");
            return "redirect:/cajero/validar";
        }
        var pendientes = solicitudRepo.findByGrupoLoteAndFechaAndEstado(
                grupo, java.time.LocalDate.now(), "PENDIENTE");
        if (pendientes.isEmpty()) {
            flash.addFlashAttribute("error", "El grupo " + grupo + " ya no tiene reservas pendientes hoy.");
            return "redirect:/cajero/validar";
        }
        turnoService.turnosDeHoy();
        int entregadas = 0;
        for (var sol : pendientes) {
            var turnoReserva = turnoRepo.findByFechaAndTipo(sol.getFecha(), sol.getTipoComida()).orElse(null);
            if (turnoReserva == null) continue;
            Marcacion m = new Marcacion();
            m.setResidente(sol.getResidente());
            m.setTurno(turnoReserva);
            m.setUsuario(cajero);
            m.setPunto(puntoDelCajero(cajero));
            m.setEstado("PERMITIDO");
            m.setObservacion("Ración reservada (" + sol.getTipoComida() + ") del grupo " + grupo
                    + " — entrega masiva: " + sol.getMotivo());
            marcacionRepo.save(m);
            sol.setEstado("ATENDIDA");
            sol.setEntregadoA("RESIDENTE");
            sol.setFechaHoraEntrega(java.time.LocalDateTime.now());
            solicitudRepo.save(sol);
            entregadas++;
        }
        flash.addFlashAttribute("ok", "Entrega masiva del grupo " + grupo + ": "
                + entregadas + " ración(es) marcadas como entregadas.");
        return "redirect:/cajero/validar";
    }

    @PostMapping("/entregar-reserva-alterada")
    public String entregarReservaAlterada(@RequestParam String codigo,
                                          @RequestParam(defaultValue = "RESIDENTE") String entregadoA,
                                          @RequestParam String justificacion,
                                          Authentication auth, RedirectAttributes flash) {
        Usuario cajero = usuarioActual(auth);
        if (puntoDelCajero(cajero) == null || puntoCerrado(cajero)) {
            flash.addFlashAttribute("error", "Tu punto no está operativo.");
            return "redirect:/cajero/validar";
        }

        if (justificacion == null || justificacion.trim().length() < 5) {
            flash.addFlashAttribute("error", "Para volver a entregar una reserva ya entregada debes justificar el motivo (mínimo 5 caracteres).");
            return "redirect:/cajero/validar?codigo=" + java.net.URLEncoder.encode(codigo.trim(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var residente = residenteRepo.findByCodigoAcceso(codigo.trim()).orElse(null);
        if (residente == null) {
            flash.addFlashAttribute("error", "Residente no encontrado.");
            return "redirect:/cajero/validar";
        }
        var solOpt = solicitudRepo.findFirstByResidenteIdResidenteAndFechaAndEstado(
                residente.getIdResidente(), java.time.LocalDate.now(), "ATENDIDA");
        if (solOpt.isEmpty()) {
            flash.addFlashAttribute("error", "El residente no tiene una reserva entregada hoy.");
            return "redirect:/cajero/validar";
        }
        var sol = solOpt.get();

        String receptorAnterior = sol.getEntregadoATexto();
        String horaAnterior = sol.getFechaHoraEntrega() == null ? "hora no registrada"
                : java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(sol.getFechaHoraEntrega());

        var turnoReserva = turnoRepo.findByFechaAndTipo(sol.getFecha(), sol.getTipoComida()).orElse(null);
        if (turnoReserva != null) {
            var previas = marcacionRepo.consumosVigentes(residente.getIdResidente(), turnoReserva.getIdTurno());
            if (!previas.isEmpty()) {
                Marcacion previa = previas.get(0);
                previa.setAnulada(true);
                previa.setAclaracion("Reserva alterada: " + justificacion.trim());
                marcacionRepo.save(previa);
            }
        }

        Incidencia i = new Incidencia();
        i.setUsuario(cajero);
        i.setPunto(puntoDelCajero(cajero));
        i.setTipo("RESERVA_ALTERADA");
        i.setRefCodigo(residente.getCodigoAcceso());
        i.setDescripcion("Reserva alterada — " + residente.getNombreCompleto()
                + " (" + residente.getCodigoAcceso() + "), " + sol.getTipoComida().toLowerCase()
                + " del " + sol.getFecha() + ". La entrega anterior (a " + receptorAnterior
                + ", " + horaAnterior + ") quedó NO VÁLIDA y ya no aparece en los reportes. "
                + "Justificación del cajero: " + justificacion.trim());
        incidenciaRepo.save(i);

        String receptor = "PRECEPTOR".equals(entregadoA) ? "PRECEPTOR" : "RESIDENTE";
        String receptorTexto = "PRECEPTOR".equals(receptor) ? "el preceptor a cargo" : "el residente";
        if (turnoReserva != null) {
            Marcacion m = new Marcacion();
            m.setResidente(residente);
            m.setTurno(turnoReserva);
            m.setUsuario(cajero);
            m.setPunto(puntoDelCajero(cajero));
            m.setEstado("PERMITIDO");
            m.setObservacion("Ración reservada (" + sol.getTipoComida() + ") entregada a "
                    + receptorTexto + " tras anular la entrega anterior (reserva alterada).");
            marcacionRepo.save(m);
        }
        sol.setEntregadoA(receptor);
        sol.setFechaHoraEntrega(java.time.LocalDateTime.now());
        solicitudRepo.save(sol);

        flash.addFlashAttribute("ok", "Ración reservada de " + sol.getTipoComida().toLowerCase()
                + " de " + residente.getNombreCompleto() + " entregada de nuevo a " + receptorTexto
                + ". La entrega anterior quedó como no válida y fue reportada al administrador (Reservas alteradas).");
        return "redirect:/cajero/validar";
    }

    @PostMapping("/deshacer")
    public String deshacer(Authentication auth, RedirectAttributes flash) {
        Usuario cajero = usuarioActual(auth);
        var ultima = marcacionRepo.findFirstByUsuarioIdUsuarioOrderByFechaHoraDesc(cajero.getIdUsuario());
        if (ultima.isEmpty() || ultima.get().getFechaHora().isBefore(java.time.LocalDateTime.now().minusMinutes(10))) {
            flash.addFlashAttribute("error", "No hay una acción reciente para deshacer (máx. 10 minutos).");
            return "redirect:/cajero/validar";
        }
        Marcacion m = ultima.get();

        if (m.getObservacion() != null && m.getObservacion().startsWith("Ración reservada")) {
            solicitudRepo.findFirstByResidenteIdResidenteAndFechaAndTipoComidaAndEstado(
                            m.getResidente().getIdResidente(), m.getTurno().getFecha(), m.getTurno().getTipo(), "ATENDIDA")
                    .ifPresent(sol -> {
                        sol.setEstado("PENDIENTE");
                        solicitudRepo.save(sol);
                    });
        }
        marcacionRepo.delete(m);
        flash.addFlashAttribute("ok", "Se deshizo el registro " + m.getEstado() + " de "
                + m.getResidente().getNombreCompleto() + ". Puedes volver a validarlo.");
        return "redirect:/cajero/validar?codigo=" + java.net.URLEncoder.encode(
                m.getResidente().getCodigoAcceso(), java.nio.charset.StandardCharsets.UTF_8);
    }

    @PostMapping("/buscar")
    public String buscar(@RequestParam String codigo) {
        return "redirect:/cajero/validar?codigo=" + URLEncoder.encode(codigo.trim(), StandardCharsets.UTF_8);
    }

    @PostMapping("/confirmar")
    public String confirmar(@RequestParam String codigo, @RequestParam String decision,
                            @RequestParam(required = false) String justificacionDoble,
                            Authentication auth, RedirectAttributes flash) {
        if (!DECISIONES.contains(decision)) {
            flash.addFlashAttribute("error", "Decisión no válida.");
            return "redirect:/cajero/validar";
        }
        Usuario actual = usuarioActual(auth);
        if (puntoDelCajero(actual) == null) {
            flash.addFlashAttribute("error", "No tienes un punto de acceso asignado. Pide al administrador que te asigne uno.");
            return "redirect:/cajero/validar";
        }
        if (puntoCerrado(actual)) {
            flash.addFlashAttribute("error", "Tu punto de acceso está cerrado por el administrador. No puedes registrar ingresos.");
            return "redirect:/cajero/validar";
        }
        ValidacionResultado res = validacionService.validar(codigo, puntoDelCajero(actual));
        if (!res.isPuedeDecidir()) {
            flash.addFlashAttribute("error", "No se puede registrar: " + res.getMotivo());
            return "redirect:/cajero/validar";
        }
        if (res.getResidente() != null && !"ACTIVO".equals(res.getResidente().getEstado())) {
            flash.addFlashAttribute("error", "El residente " + res.getResidente().getNombreCompleto()
                    + " está INACTIVO: no puede registrar ingresos al comedor.");
            return "redirect:/cajero/validar";
        }

        if (res.getMarcacionPrevia() != null && "PERMITIDO".equals(decision)) {
            if (justificacionDoble == null || justificacionDoble.isBlank()) {
                flash.addFlashAttribute("error", "Para permitir un segundo ingreso con el mismo código debes justificar el motivo.");
                return "redirect:/cajero/validar?codigo=" + java.net.URLEncoder.encode(codigo.trim(), java.nio.charset.StandardCharsets.UTF_8);
            }
            marcarInfraccion(res.getMarcacionPrevia(), justificacionDoble.trim(), actual);
        }

        Usuario cajero = actual;

        if ("DENEGADO".equals(decision)) {
            var previa = marcacionRepo
                    .findFirstByResidenteIdResidenteAndTurnoIdTurnoAndEstadoOrderByFechaHoraDesc(
                            res.getResidente().getIdResidente(), res.getTurnoActivo().getIdTurno(), "DENEGADO");
            if (previa.isPresent()
                    && previa.get().getFechaHora().isAfter(java.time.LocalDateTime.now().minusMinutes(5))
                    && (previa.get().getAnulada() == null || !previa.get().getAnulada())) {
                Marcacion d = previa.get();
                d.setIntentos((d.getIntentos() == null ? 1 : d.getIntentos()) + 1);
                marcacionRepo.save(d);
                flash.addFlashAttribute("error", "Ingreso DENEGADO a " + res.getResidente().getNombreCompleto()
                        + " (intento N° " + d.getIntentos() + " en 5 minutos: en el reporte figura una sola vez).");
                return "redirect:/cajero/validar";
            }
        }

        Marcacion m = new Marcacion();
        m.setResidente(res.getResidente());
        m.setTurno(res.getTurnoActivo());
        m.setUsuario(cajero);
        m.setPunto(puntoDelCajero(cajero));
        m.setEstado(decision);
        m.setObservacion(observacion(res, decision));
        marcacionRepo.save(m);

        if (res.getSolicitud() != null && "PERMITIDO".equals(decision)) {
            res.getSolicitud().setEstado("ATENDIDA");
            solicitudRepo.save(res.getSolicitud());
        }

        String nombre = res.getResidente().getNombreCompleto();
        switch (decision) {
            case "PERMITIDO" -> flash.addFlashAttribute("ok", "Ingreso PERMITIDO registrado para " + nombre + ".");
            case "JUSTIFICADO" -> flash.addFlashAttribute("ok", "Se registró el paso JUSTIFICADO de " + nombre + ".");
            default -> flash.addFlashAttribute("error", "Ingreso DENEGADO registrado para " + nombre + ".");
        }
        flash.addFlashAttribute("deshacer", true);
        return "redirect:/cajero/validar";
    }

    private String observacion(ValidacionResultado res, String decision) {
        if ("JUSTIFICADO".equals(decision) && res.getJustificacion() != null) {
            return res.getJustificacion().getMotivo();
        }
        if (res.getSolicitud() != null && "PERMITIDO".equals(decision)) {
            return "Ración reservada: " + res.getSolicitud().getMotivo();
        }
        if ("DENEGADO".equals(decision)) {
            return res.getMotivo() != null ? res.getMotivo() : "Denegado por el cajero";
        }
        return res.getMotivo();
    }

    @PostMapping("/incidencia")
    public String incidencia(@RequestParam String descripcion, Authentication auth, RedirectAttributes flash) {
        if (descripcion != null && !descripcion.isBlank()) {
            Usuario cajero = usuarioActual(auth);
            Incidencia i = new Incidencia();
            i.setUsuario(cajero);
            i.setPunto(puntoDelCajero(cajero));
            i.setDescripcion(descripcion.trim());
            incidenciaRepo.save(i);
            flash.addFlashAttribute("ok", "Incidencia reportada correctamente.");
        }
        return "redirect:/cajero/validar";
    }
}
