package com.upeu.comedorupeu.controller;

import com.upeu.comedorupeu.models.*;
import com.upeu.comedorupeu.repository.*;
import com.upeu.comedorupeu.services.CarrerasService;
import com.upeu.comedorupeu.services.ExcelService;
import com.upeu.comedorupeu.services.ImagenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/preceptor")
public class PreceptorController {

    private final ResidenteRepository residenteRepo;
    private final ApoderadoRepository apoderadoRepo;
    private final AusenciaRepository ausenciaRepo;
    private final EventoEspecialRepository eventoRepo;
    private final UsuarioRepository usuarioRepo;
    private final ImagenService imagenService;
    private final ExcelService excelService;
    private final SolicitudExtemporaneaRepository solicitudRepo;
    private final EventoEntregaRepository entregaRepo;
    private final CarrerasService carrerasService;
    private final IncidenciaRepository incidenciaRepo;
    private final ApunteRepository apunteRepo;
    private final com.upeu.comedorupeu.services.TurnoService turnoService;

    private final com.upeu.comedorupeu.services.JustificacionService justificacionService;

    public PreceptorController(ResidenteRepository residenteRepo, ApoderadoRepository apoderadoRepo,
                               AusenciaRepository ausenciaRepo, EventoEspecialRepository eventoRepo,
                               UsuarioRepository usuarioRepo, ImagenService imagenService,
                               ExcelService excelService, SolicitudExtemporaneaRepository solicitudRepo,
                               EventoEntregaRepository entregaRepo, CarrerasService carrerasService,
                               IncidenciaRepository incidenciaRepo, ApunteRepository apunteRepo,
                               com.upeu.comedorupeu.services.TurnoService turnoService,
                               com.upeu.comedorupeu.services.JustificacionService justificacionService) {
        this.residenteRepo = residenteRepo;
        this.apoderadoRepo = apoderadoRepo;
        this.ausenciaRepo = ausenciaRepo;
        this.eventoRepo = eventoRepo;
        this.usuarioRepo = usuarioRepo;
        this.imagenService = imagenService;
        this.excelService = excelService;
        this.solicitudRepo = solicitudRepo;
        this.entregaRepo = entregaRepo;
        this.carrerasService = carrerasService;
        this.incidenciaRepo = incidenciaRepo;
        this.apunteRepo = apunteRepo;
        this.turnoService = turnoService;
        this.justificacionService = justificacionService;
    }

    private RacionEspecialRepository racionEspecialRepo;

    @org.springframework.beans.factory.annotation.Autowired
    public void setRacionEspecialRepo(RacionEspecialRepository racionEspecialRepo) {
        this.racionEspecialRepo = racionEspecialRepo;
    }

    private Usuario usuarioActual(Authentication auth) {
        return usuarioRepo.findByCorreo(auth.getName());
    }

    private String pabellonDe(Authentication auth) {
        Usuario u = usuarioActual(auth);
        return "PRECEPTOR".equals(u.getRol()) ? u.getPabellon() : null;
    }

    private List<Residente> residentesActivosDe(Authentication auth) {
        String pab = pabellonDe(auth);
        return pab == null ? residenteRepo.findByEstadoOrderByApellidoAsc("ACTIVO")
                : residenteRepo.findByEstadoAndPabellonOrderByApellidoAsc("ACTIVO", pab);
    }

    @GetMapping("/residentes")
    public String residentes(@RequestParam(required = false) String q, Model model, Authentication auth) {
        String pab = pabellonDe(auth);
        List<Residente> lista;
        if (q != null && !q.isBlank()) {
            lista = residenteRepo.buscar(q.trim(), pab);
        } else if (pab != null) {
            lista = residenteRepo.findByPabellonOrderByApellidoAsc(pab);
        } else {
            lista = residenteRepo.findAll();
        }
        model.addAttribute("residentes", lista);
        model.addAttribute("q", q);
        model.addAttribute("pabellonScope", pab);
        model.addAttribute("total", pab == null ? residenteRepo.count() : residenteRepo.countByPabellon(pab));
        model.addAttribute("activos", pab == null ? residenteRepo.countByEstado("ACTIVO") : residenteRepo.countByEstadoAndPabellon("ACTIVO", pab));
        model.addAttribute("inactivos", pab == null ? residenteRepo.countByEstado("INACTIVO") : residenteRepo.countByEstadoAndPabellon("INACTIVO", pab));

        model.addAttribute("avisosPreceptor", apunteRepo.findTop10ByTipoOrderByFechaHoraDesc("PRECEPTOR").stream()
                .filter(com.upeu.comedorupeu.models.Apunte::estaVigente)
                .toList());
        model.addAttribute("comidasBloqueadas", turnoService.comidasBloqueadasHoy());
        model.addAttribute("hoy", LocalDate.now());
        return "preceptor/residentes";
    }

    @GetMapping("/residentes/nuevo")
    public String nuevoResidente(Model model, Authentication auth) {
        Residente r = new Residente();
        r.setFechaIngreso(LocalDate.now());
        model.addAttribute("residente", r);

        model.addAttribute("pabellonAuto", usuarioActual(auth).getPabellon());
        model.addAttribute("facultades", carrerasService.facultades());
        return "preceptor/residente_form";
    }

    @GetMapping("/residentes/{id}/editar")
    public String editarResidente(@PathVariable Long id, Model model, Authentication auth) {

        Residente residente = residenteRepo.findById(id).orElse(null);
        if (residente == null) return "redirect:/preceptor/residentes";
        model.addAttribute("residente", residente);
        model.addAttribute("pabellonAuto", usuarioActual(auth).getPabellon());
        model.addAttribute("facultades", carrerasService.facultades());
        return "preceptor/residente_form";
    }

    @PostMapping("/residentes/{id}/eliminar")
    public String eliminarResidente(@PathVariable Long id, RedirectAttributes flash) {
        Residente r = residenteRepo.findById(id).orElse(null);
        if (r == null) return "redirect:/preceptor/residentes";
        try {
            residenteRepo.delete(r);
            residenteRepo.flush();
            flash.addFlashAttribute("ok", "Residente " + r.getNombreCompleto() + " eliminado del sistema.");
        } catch (Exception e) {
            flash.addFlashAttribute("error", "No se puede eliminar a " + r.getNombreCompleto()
                    + " porque tiene historial (marcaciones, ausencias o reservas). Márcalo como INACTIVO en su lugar.");
        }
        return "redirect:/preceptor/residentes";
    }

    @GetMapping("/residentes/{id}")
    public String verResidente(@PathVariable Long id,

                               @RequestParam(required = false) LocalDate fDesde,
                               @RequestParam(required = false) LocalDate fHasta,
                               Model model) {

        Residente r = residenteRepo.findById(id).orElse(null);
        if (r == null) return "redirect:/preceptor/residentes";
        model.addAttribute("residente", r);

        var ausencias = ausenciaRepo.findByResidenteIdResidenteOrderByFechaInicioDesc(id).stream()
                .filter(a -> fDesde == null || !a.getFechaFin().isBefore(fDesde))
                .filter(a -> fHasta == null || !a.getFechaInicio().isAfter(fHasta))
                .toList();
        var solicitudes = solicitudRepo.findByResidenteIdResidenteOrderByFechaHoraDesc(id).stream()
                .filter(s -> fDesde == null || !s.getFecha().isBefore(fDesde))
                .filter(s -> fHasta == null || !s.getFecha().isAfter(fHasta))
                .toList();

        var eventosParticipo = entregaRepo.findByResidenteIdResidente(id).stream()
                .filter(en -> en.getEvento() != null && en.getEvento().getFechaEvento() != null)
                .filter(en -> fDesde == null || !en.getEvento().getFechaEvento().isBefore(fDesde))
                .filter(en -> fHasta == null || !en.getEvento().getFechaEvento().isAfter(fHasta))
                .sorted((a, b) -> b.getEvento().getFechaEvento().compareTo(a.getEvento().getFechaEvento()))
                .toList();

        var racionesEspeciales = racionEspecialRepo.findByResidenteIdResidenteOrderByFechaInicioDesc(id).stream()
                .filter(re -> fDesde == null || !re.getFechaFin().isBefore(fDesde))
                .filter(re -> fHasta == null || !re.getFechaInicio().isAfter(fHasta))
                .toList();

        model.addAttribute("ausencias", ausencias);
        model.addAttribute("solicitudes", solicitudes);
        model.addAttribute("racionesEspeciales", racionesEspeciales);
        model.addAttribute("eventosParticipo", eventosParticipo);
        model.addAttribute("fDesde", fDesde);
        model.addAttribute("fHasta", fHasta);
        model.addAttribute("hoy", LocalDate.now());

        model.addAttribute("comidasBloqueadas", turnoService.comidasBloqueadasHoy());
        return "preceptor/residente_ver";
    }

    @PostMapping("/reservas")
    public String reservaRapida(@RequestParam String codigo,
                                @RequestParam LocalDate fecha,
                                @RequestParam String tipoComida,
                                @RequestParam(required = false) java.time.LocalTime horaRecojo,
                                @RequestParam String motivo,
                                Authentication auth,
                                RedirectAttributes flash) {
        String cod = codigo.contains("—") ? codigo.split("—")[0].trim() : codigo.trim();
        Residente r = residenteRepo.findByCodigoAcceso(cod).orElse(null);
        if (r == null) {
            flash.addFlashAttribute("error", "No se encontró el residente con código \"" + cod + "\".");
            return "redirect:/preceptor/residentes";
        }
        SolicitudExtemporanea s = new SolicitudExtemporanea();
        s.setResidente(r);
        s.setUsuario(usuarioActual(auth));
        s.setFecha(fecha);
        s.setTipoComida(tipoComida);
        s.setHoraRecojo(horaRecojo);
        s.setMotivo(motivo);
        solicitudRepo.save(s);
        flash.addFlashAttribute("ok", "Ración reservada para " + r.getNombreCompleto() + " ("
                + tipoComida.toLowerCase() + " del " + fecha
                + (horaRecojo != null ? ", recojo aprox. " + horaRecojo : "") + ").");
        return "redirect:/preceptor/residentes";
    }

    @PostMapping("/residentes/{id}/solicitud")
    public String registrarSolicitud(@PathVariable Long id,
                                     @RequestParam LocalDate fecha,
                                     @RequestParam String tipoComida,
                                     @RequestParam(required = false) java.time.LocalTime horaRecojo,
                                     @RequestParam String motivo,
                                     Authentication auth,
                                     RedirectAttributes flash) {
        Residente r = residenteRepo.findById(id).orElse(null);
        if (r == null) {
            flash.addFlashAttribute("error", "El residente ya no existe.");
            return "redirect:/preceptor/residentes";
        }
        SolicitudExtemporanea s = new SolicitudExtemporanea();
        s.setResidente(r);
        s.setUsuario(usuarioActual(auth));
        s.setFecha(fecha);
        s.setTipoComida(tipoComida);
        s.setHoraRecojo(horaRecojo);
        s.setMotivo(motivo);
        solicitudRepo.save(s);
        flash.addFlashAttribute("ok", "Ración reservada para " + r.getNombreCompleto() + " (" +
                tipoComida.toLowerCase() + " del " + fecha + "). El cajero podrá atenderlo aunque el turno haya cerrado.");
        return "redirect:/preceptor/residentes/" + id;
    }

    @PostMapping("/ausencias/{id}/eliminar")
    public String eliminarAusencia(@PathVariable Long id, RedirectAttributes flash) {
        Ausencia a = ausenciaRepo.findById(id).orElse(null);
        if (a == null) return "redirect:/preceptor/residentes";
        Long idResidente = a.getResidente().getIdResidente();
        String estado = justificacionService.estadoDe(a);
        if (!"FUTURA".equals(estado)) {
            flash.addFlashAttribute("error", "EN_CURSO".equals(estado)
                    ? "Esta ausencia ya está en curso: usa \"Cancelar\" en el módulo de Ausencias para el cierre anticipado."
                    : "Esta ausencia ya transcurrió: su historial se conserva y no puede eliminarse.");
            return "redirect:/preceptor/residentes/" + idResidente;
        }
        ausenciaRepo.delete(a);
        flash.addFlashAttribute("ok", "Ausencia del " + a.getFechaInicio() + " al " + a.getFechaFin()
                + " eliminada. El residente ya no figura justificado en esas fechas.");
        return "redirect:/preceptor/residentes/" + idResidente;
    }

    @PostMapping("/ausencias/{id}/cancelar")
    public String cancelarAusencia(@PathVariable Long id, RedirectAttributes flash) {
        Ausencia a = ausenciaRepo.findById(id).orElse(null);
        if (a == null) return "redirect:/preceptor/ausencias";
        String resultado = justificacionService.cierreAnticipado(a);
        if (resultado == null) {
            flash.addFlashAttribute("error", "Esta ausencia no está en curso: no aplica el cierre anticipado.");
        } else {
            flash.addFlashAttribute("ok", resultado);
        }
        return "redirect:/preceptor/ausencias";
    }

    @PostMapping("/solicitudes/{id}/cancelar")
    public String cancelarSolicitud(@PathVariable Long id, RedirectAttributes flash) {
        SolicitudExtemporanea s = solicitudRepo.findById(id).orElse(null);
        if (s == null) return "redirect:/preceptor/residentes";
        Long idResidente = s.getResidente().getIdResidente();
        if (!"PENDIENTE".equals(s.getEstado())) {
            flash.addFlashAttribute("error", "Esa reserva ya fue atendida por el cajero; no se puede cancelar.");
        } else if (turnoService.turnoYaOcurrio(s.getTipoComida(), s.getFecha())) {

            flash.addFlashAttribute("error", "El turno de esa reserva ya pasó: queda como historial y no se puede cancelar.");
        } else {
            solicitudRepo.delete(s);
            flash.addFlashAttribute("ok", "Reserva de " + s.getTipoComida().toLowerCase()
                    + " del " + s.getFecha() + " cancelada.");
        }
        return "redirect:/preceptor/residentes/" + idResidente;
    }

    @PostMapping("/eventos/{id}/eliminar")
    public String eliminarEvento(@PathVariable Long id, RedirectAttributes flash) {
        EventoEspecial e = eventoRepo.findById(id).orElse(null);
        if (e == null) return "redirect:/preceptor/eventos";
        entregaRepo.deleteAll(entregaRepo.findByEventoIdEvento(id));
        eventoRepo.delete(e);
        flash.addFlashAttribute("ok", "Evento \"" + e.getNombre() + "\" eliminado junto con su pase de lista.");
        return "redirect:/preceptor/eventos";
    }

    @PostMapping("/residentes/importar")
    public String importarExcel(@RequestParam MultipartFile archivo, Authentication auth, RedirectAttributes flash) {
        try {
            String resumen = excelService.importar(archivo, usuarioActual(auth));
            flash.addFlashAttribute("ok", resumen);
        } catch (Exception e) {
            flash.addFlashAttribute("error", "No se pudo importar: " + e.getMessage());
        }
        return "redirect:/preceptor/residentes";
    }

    @GetMapping("/residentes/plantilla")
    public ResponseEntity<byte[]> plantillaExcel() throws IOException {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla_residentes.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelService.generarPlantilla());
    }

    @PostMapping("/residentes/guardar")
    public String guardarResidente(@RequestParam(required = false) Long idResidente,
                                   @RequestParam String nombre,
                                   @RequestParam String apellido,
                                   @RequestParam(required = false) String dni,
                                   @RequestParam String codigoAcceso,
                                   @RequestParam(required = false) String carrera,
                                   @RequestParam(required = false) String pabellon,
                                   @RequestParam(required = false) String cuarto,
                                   @RequestParam(required = false) String celular,
                                   @RequestParam(defaultValue = "ACTIVO") String estado,
                                   @RequestParam(defaultValue = "false") boolean deuda,
                                   @RequestParam(required = false) MultipartFile foto,
                                   @RequestParam(required = false) String apoNombre,
                                   @RequestParam(required = false) String apoRelacion,
                                   @RequestParam(required = false) String apoDni,
                                   @RequestParam(required = false) String apoTelefono,
                                   Authentication auth,
                                   RedirectAttributes flash) {
        Residente r = (idResidente != null) ? residenteRepo.findById(idResidente).orElse(new Residente()) : new Residente();

        if (!codigoAcceso.equals(r.getCodigoAcceso()) && residenteRepo.existsByCodigoAcceso(codigoAcceso)) {
            String dueno = residenteRepo.findByCodigoAcceso(codigoAcceso.trim())
                    .map(Residente::getNombreCompleto).orElse("otro residente");
            flash.addFlashAttribute("error", "El código universitario \"" + codigoAcceso.trim()
                    + "\" ya está registrado a nombre de " + dueno + ". No se puede repetir.");
            return idResidente == null ? "redirect:/preceptor/residentes/nuevo"
                    : "redirect:/preceptor/residentes/" + idResidente + "/editar";
        }

        String dniLimpio = (dni == null || dni.isBlank()) ? null : dni.trim();
        if (dniLimpio != null) {
            var mismoDni = residenteRepo.findFirstByDni(dniLimpio);
            if (mismoDni.isPresent() && (idResidente == null
                    || !mismoDni.get().getIdResidente().equals(idResidente))) {
                flash.addFlashAttribute("error", "El DNI \"" + dniLimpio + "\" ya está registrado a nombre de "
                        + mismoDni.get().getNombreCompleto() + ". No se puede repetir.");
                return idResidente == null ? "redirect:/preceptor/residentes/nuevo"
                        : "redirect:/preceptor/residentes/" + idResidente + "/editar";
            }
        }

        Usuario quienRegistra = usuarioActual(auth);
        r.setNombre(nombre);
        r.setApellido(apellido);
        r.setDni(dniLimpio);
        r.setCodigoAcceso(codigoAcceso.trim());
        r.setCarrera(carrera);

        r.setPabellon(quienRegistra.getPabellon() != null ? quienRegistra.getPabellon() : pabellon);
        r.setCuarto(cuarto);
        r.setCelular(celular);
        r.setEstado(estado);
        r.setDeuda(deuda);

        if (r.getFechaIngreso() == null) {
            LocalDate ahora = LocalDate.now();
            if (ahora.getMonthValue() >= 7) {
                r.setFechaIngreso(LocalDate.of(ahora.getYear(), 7, 1));
                r.setFechaFinEstancia(LocalDate.of(ahora.getYear(), 12, 31));
            } else {
                r.setFechaIngreso(LocalDate.of(ahora.getYear(), 1, 1));
                r.setFechaFinEstancia(LocalDate.of(ahora.getYear(), 6, 30));
            }
        }
        if (r.getTokenAcceso() == null) r.setTokenAcceso(java.util.UUID.randomUUID().toString());
        if (r.getPreceptor() == null) r.setPreceptor(quienRegistra);

        try {
            String url = imagenService.guardar(foto);
            if (url != null) r.setFotoUrl(url);
        } catch (IOException | IllegalArgumentException e) {
            flash.addFlashAttribute("error", "No se pudo guardar la foto: " + e.getMessage());
        }

        if (apoNombre != null && !apoNombre.isBlank()) {
            Apoderado a = r.getApoderado() != null ? r.getApoderado() : new Apoderado();
            a.setNombre(apoNombre);
            a.setRelacion(apoRelacion);
            a.setDni(apoDni);
            a.setTelefono(apoTelefono);
            apoderadoRepo.save(a);
            r.setApoderado(a);
        }

        residenteRepo.save(r);
        flash.addFlashAttribute("ok", "Residente " + r.getNombreCompleto() + " guardado correctamente.");
        return "redirect:/preceptor/residentes";
    }

    @GetMapping("/ausencias")
    public String ausencias(Model model, Authentication auth) {
        model.addAttribute("residentes", residentesActivosDe(auth));
        model.addAttribute("hoy", LocalDate.now());

        model.addAttribute("comidasBloqueadas", turnoService.comidasBloqueadasHoy());

        String pab = pabellonDe(auth);
        LocalDate hoy = LocalDate.now();
        List<Ausencia> vigentes = ausenciaRepo.findAllByOrderByFechaInicioDesc().stream()
                .filter(a -> pab == null || pab.equals(a.getResidente().getPabellon()))
                .filter(a -> !a.getFechaFin().isBefore(hoy))
                .toList();
        java.util.Map<Long, String> estados = new java.util.HashMap<>();
        for (Ausencia a : vigentes) estados.put(a.getIdAusencia(), justificacionService.estadoDe(a));
        model.addAttribute("ausenciasVigentes", vigentes);
        model.addAttribute("estadoAusencias", estados);
        return "preceptor/ausencias";
    }

    @PostMapping("/ausencias/guardar")
    public String guardarAusencia(@RequestParam("ids") List<Long> ids,
                                  @RequestParam LocalDate desde,
                                  @RequestParam LocalDate hasta,
                                  @RequestParam String motivo,
                                  HttpServletRequest request,
                                  Authentication auth,
                                  RedirectAttributes flash) {
        if (hasta.isBefore(desde)) {
            flash.addFlashAttribute("error", "El rango de fechas no es válido.");
            return "redirect:/preceptor/ausencias";
        }
        Usuario preceptor = usuarioActual(auth);
        int registrados = 0;

        for (Long idRes : ids) {
            Residente r = residenteRepo.findById(idRes).orElse(null);
            if (r == null) continue;

            Ausencia ausencia = new Ausencia();
            ausencia.setResidente(r);
            ausencia.setUsuario(preceptor);
            ausencia.setFechaInicio(desde);
            ausencia.setFechaFin(hasta);
            ausencia.setMotivo(motivo);

            for (LocalDate f = desde; !f.isAfter(hasta); f = f.plusDays(1)) {
                boolean primero = f.equals(desde);
                boolean ultimo = f.equals(hasta);
                for (String tipo : List.of("DESAYUNO", "ALMUERZO", "CENA")) {
                    boolean incluir;
                    if (!primero && !ultimo) {
                        incluir = true;
                    } else {
                        String letra = tipo.substring(0, 1);
                        String pref = primero ? "p" : "u";
                        incluir = request.getParameter(pref + letra + "_" + idRes) != null;

                        if (primero && ultimo && !incluir) {
                            incluir = request.getParameter("u" + letra + "_" + idRes) != null;
                        }
                    }
                    if (incluir) {
                        AusenciaDetalle d = new AusenciaDetalle();
                        d.setAusencia(ausencia);
                        d.setFecha(f);
                        d.setTipoComida(tipo);
                        ausencia.getDetalles().add(d);
                    }
                }
            }
            ausenciaRepo.save(ausencia);
            registrados++;
        }
        flash.addFlashAttribute("ok", "Ausencia registrada para " + registrados + " residente(s) del "
                + desde + " al " + hasta + ".");
        return "redirect:/preceptor/ausencias";
    }

    @PostMapping("/residentes/{id}/racion-especial")
    public String guardarRacionEspecial(@PathVariable Long id,
                                        @RequestParam LocalDate desde,
                                        @RequestParam LocalDate hasta,
                                        HttpServletRequest request,
                                        Authentication auth,
                                        RedirectAttributes flash) {
        Residente r = residenteRepo.findById(id).orElse(null);
        if (r == null) return "redirect:/preceptor/residentes";
        if (hasta.isBefore(desde)) {
            flash.addFlashAttribute("error", "El rango de fechas no es válido.");
            return "redirect:/preceptor/residentes/" + id;
        }

        RacionEspecial re = new RacionEspecial();
        re.setResidente(r);
        re.setUsuario(usuarioActual(auth));
        re.setFechaInicio(desde);
        re.setFechaFin(hasta);

        for (LocalDate f = desde; !f.isAfter(hasta); f = f.plusDays(1)) {
            boolean primero = f.equals(desde);
            boolean ultimo = f.equals(hasta);
            for (String tipo : List.of("DESAYUNO", "ALMUERZO", "CENA")) {
                boolean incluir;
                if (!primero && !ultimo) {
                    incluir = true;
                } else {
                    String letra = tipo.substring(0, 1);
                    incluir = request.getParameter((primero ? "p" : "u") + letra) != null;

                    if (primero && ultimo && !incluir) {
                        incluir = request.getParameter("u" + letra) != null;
                    }
                }
                if (incluir) {
                    RacionEspecialDetalle d = new RacionEspecialDetalle();
                    d.setRacionEspecial(re);
                    d.setFecha(f);
                    d.setTipoComida(tipo);
                    re.getDetalles().add(d);
                }
            }
        }

        if (re.getDetalles().isEmpty()) {
            flash.addFlashAttribute("error", "Marca al menos una comida para asignar la ración especial.");
            return "redirect:/preceptor/residentes/" + id;
        }
        racionEspecialRepo.save(re);
        flash.addFlashAttribute("ok", "Ración especial asignada a " + r.getNombreCompleto()
                + " (" + re.getDetalles().size() + " comida(s) del " + desde + " al " + hasta + ").");
        return "redirect:/preceptor/residentes/" + id;
    }

    @PostMapping("/raciones-especiales/{id}/eliminar")
    public String eliminarRacionEspecial(@PathVariable Long id, RedirectAttributes flash) {
        RacionEspecial re = racionEspecialRepo.findById(id).orElse(null);
        if (re == null) return "redirect:/preceptor/residentes";
        Long idRes = re.getResidente().getIdResidente();
        racionEspecialRepo.delete(re);
        flash.addFlashAttribute("ok", "Ración especial eliminada.");
        return "redirect:/preceptor/residentes/" + idRes;
    }

    @GetMapping("/eventos")
    public String eventos(@RequestParam(required = false) Long lista,
                          @RequestParam(required = false) Long detalle,
                          Model model, Authentication auth) {

        if (lista != null) return "redirect:/preceptor/pase-lista?evento=" + lista;

        List<Residente> activos = residentesActivosDe(auth);
        model.addAttribute("residentes", activos);
        model.addAttribute("activos", activos.size());
        model.addAttribute("misEventos", eventoRepo.findAllByOrderByFechaEnvioDesc());
        model.addAttribute("eventosAprobados", eventoRepo.findAllByOrderByFechaEnvioDesc().stream()
                .filter(e -> "APROBADO".equals(e.getEstado())).toList());
        model.addAttribute("hoy", LocalDate.now());

        if (detalle != null) {
            eventoRepo.findById(detalle).ifPresent(evento ->
                    cargarDatosEvento(model, evento, activos, "eventoDetalle"));
        }
        return "preceptor/eventos";
    }

    @GetMapping("/pase-lista")
    public String paseLista(@RequestParam(required = false) Long evento, Model model, Authentication auth) {
        List<Residente> activos = residentesActivosDe(auth);
        model.addAttribute("eventosAprobados", eventoRepo.findAllByOrderByFechaEnvioDesc().stream()
                .filter(e -> "APROBADO".equals(e.getEstado())).toList());
        if (evento != null) {
            eventoRepo.findById(evento)
                    .filter(e -> "APROBADO".equals(e.getEstado()))
                    .ifPresent(e -> cargarDatosEvento(model, e, activos, "eventoLista"));
        }
        return "preceptor/pase_lista";
    }

    private void cargarDatosEvento(Model model, EventoEspecial evento, List<Residente> activos, String nombreAttr) {
        var excluidos = evento.getExcluidosLista();
        var participantes = activos.stream()
                .filter(r -> !excluidos.contains(r.getCodigoAcceso()))
                .toList();
        java.util.Map<Long, Boolean> entregas = new java.util.HashMap<>();
        for (EventoEntrega en : entregaRepo.findByEventoIdEvento(evento.getIdEvento())) {
            entregas.put(en.getResidente().getIdResidente(), Boolean.TRUE.equals(en.getRecibido()));
        }
        List<java.util.Map<String, String>> excluidosInfo = new java.util.ArrayList<>();
        for (String cod : excluidos) {
            java.util.Map<String, String> fila = new java.util.HashMap<>();
            fila.put("codigo", cod);
            fila.put("nombre", residenteRepo.findByCodigoAcceso(cod)
                    .map(Residente::getNombreCompleto).orElse("(no registrado)"));
            excluidosInfo.add(fila);
        }

        long recibidosDeMiPabellon = participantes.stream()
                .filter(r -> Boolean.TRUE.equals(entregas.get(r.getIdResidente())))
                .count();
        boolean paseDeMiPabellon = participantes.stream()
                .anyMatch(r -> entregas.containsKey(r.getIdResidente()));
        model.addAttribute(nombreAttr, evento);
        model.addAttribute("participantesLista", participantes);
        model.addAttribute("entregas", entregas);
        model.addAttribute("recibidos", recibidosDeMiPabellon);
        model.addAttribute("excluidosInfo", excluidosInfo);
        model.addAttribute("conPaseLista", paseDeMiPabellon);
        model.addAttribute("esPasado", evento.getFechaEvento() != null && evento.getFechaEvento().isBefore(LocalDate.now()));
    }

    @PostMapping("/eventos/{id}/lista")
    public String guardarLista(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request,
                               Authentication auth, RedirectAttributes flash) {
        EventoEspecial evento = eventoRepo.findById(id).orElse(null);
        if (evento == null) {
            flash.addFlashAttribute("error", "El evento ya no existe.");
            return "redirect:/preceptor/eventos";
        }
        int recibidos = 0;
        for (Residente r : residentesActivosDe(auth)) {
            if (evento.getExcluidosLista().contains(r.getCodigoAcceso())) continue;
            boolean recibido = request.getParameter("rec_" + r.getIdResidente()) != null;
            EventoEntrega en = entregaRepo
                    .findFirstByEventoIdEventoAndResidenteIdResidente(id, r.getIdResidente())
                    .orElseGet(() -> {
                        EventoEntrega nuevo = new EventoEntrega();
                        nuevo.setEvento(evento);
                        nuevo.setResidente(r);
                        return nuevo;
                    });
            en.setRecibido(recibido);
            entregaRepo.save(en);
            if (recibido) recibidos++;
        }
        flash.addFlashAttribute("ok", "Pase de lista guardado: " + recibidos
                + " residente(s) recibieron su ración del evento \"" + evento.getNombre() + "\".");
        return "redirect:/preceptor/pase-lista?evento=" + id;
    }

    @PostMapping("/aviso-admin")
    public String avisoAlAdmin(@RequestParam String mensaje, Authentication auth, RedirectAttributes flash) {
        if (mensaje != null && !mensaje.isBlank()) {
            Incidencia i = new Incidencia();
            i.setTipo("PRECEPTOR");
            i.setDescripcion(mensaje.trim().length() > 500 ? mensaje.trim().substring(0, 500) : mensaje.trim());
            i.setUsuario(usuarioActual(auth));
            incidenciaRepo.save(i);
            flash.addFlashAttribute("ok", "Tu aviso fue enviado al administrador.");
        }
        return "redirect:/preceptor/residentes";
    }

    @PostMapping("/eventos/{id}/solicitar-exclusion")
    public String solicitarExclusion(@PathVariable Long id,
                                     @RequestParam String codigo,
                                     @RequestParam String motivo,
                                     Authentication auth,
                                     RedirectAttributes flash) {
        EventoEspecial evento = eventoRepo.findById(id).orElse(null);
        if (evento == null) {
            flash.addFlashAttribute("error", "El evento ya no existe.");
            return "redirect:/preceptor/eventos";
        }

        if (evento.getFechaEvento() != null && evento.getFechaEvento().isBefore(LocalDate.now())) {
            flash.addFlashAttribute("error", "El evento \"" + evento.getNombre() + "\" ya se realizó; ya no se puede excluir a nadie.");
            return "redirect:/preceptor/pase-lista?evento=" + id;
        }
        String cod = codigo.contains("—") ? codigo.split("—")[0].trim() : codigo.trim();
        Residente r = residenteRepo.findByCodigoAcceso(cod).orElse(null);
        if (r == null) {
            flash.addFlashAttribute("error", "No se encontró el residente con código \"" + cod + "\".");
            return "redirect:/preceptor/pase-lista?evento=" + id;
        }
        Incidencia i = new Incidencia();
        i.setUsuario(usuarioActual(auth));
        i.setTipo("EXCLUSION");
        i.setRefEvento(evento.getIdEvento());
        i.setRefCodigo(r.getCodigoAcceso());
        i.setDescripcion("Solicitud de exclusión — " + r.getNombreCompleto() + " (" + r.getCodigoAcceso()
                + ") no asistirá al evento \"" + evento.getNombre() + "\" del "
                + evento.getFechaEvento() + ". Motivo: " + motivo.trim());
        incidenciaRepo.save(i);
        flash.addFlashAttribute("ok", "Solicitud enviada al administrador: excluir a " + r.getNombreCompleto()
                + " del evento \"" + evento.getNombre() + "\".");
        return "redirect:/preceptor/pase-lista?evento=" + id;
    }

    @GetMapping("/eventos/plantilla-lista")
    public org.springframework.http.ResponseEntity<byte[]> plantillaLista() throws IOException {
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"plantilla_pase_lista.xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelService.generarPlantillaLista());
    }

    @PostMapping("/eventos/{id}/lista/importar")
    public String importarLista(@PathVariable Long id, @RequestParam MultipartFile archivo,
                                RedirectAttributes flash) {
        EventoEspecial evento = eventoRepo.findById(id).orElse(null);
        if (evento == null) {
            flash.addFlashAttribute("error", "El evento ya no existe.");
            return "redirect:/preceptor/eventos";
        }
        try {
            List<String> codigos = excelService.leerCodigos(archivo);
            int marcados = 0;
            for (String cod : codigos) {
                Residente r = residenteRepo.findByCodigoAcceso(cod).orElse(null);
                if (r == null || evento.getExcluidosLista().contains(cod)) continue;
                EventoEntrega en = entregaRepo
                        .findFirstByEventoIdEventoAndResidenteIdResidente(id, r.getIdResidente())
                        .orElseGet(() -> {
                            EventoEntrega nuevo = new EventoEntrega();
                            nuevo.setEvento(evento);
                            nuevo.setResidente(r);
                            return nuevo;
                        });
                en.setRecibido(true);
                entregaRepo.save(en);
                marcados++;
            }
            flash.addFlashAttribute("ok", "Importación de lista: " + marcados
                    + " residente(s) marcados como recibidos en \"" + evento.getNombre() + "\".");
        } catch (Exception e) {
            flash.addFlashAttribute("error", "No se pudo importar la lista: " + e.getMessage());
        }
        return "redirect:/preceptor/pase-lista?evento=" + id;
    }

    @PostMapping("/eventos/enviar")
    public String enviarEvento(@RequestParam String nombre,
                               @RequestParam LocalDate fechaEvento,
                               @RequestParam(required = false) String excluidos,
                               Authentication auth,
                               RedirectAttributes flash) {
        EventoEspecial e = new EventoEspecial();
        e.setNombre(nombre);
        e.setFechaEvento(fechaEvento);

        e.setTurnos("ADICIONAL");
        e.setExcluidos(excluidos);
        e.setEstado("PENDIENTE");
        e.setUsuario(usuarioActual(auth));
        eventoRepo.save(e);
        flash.addFlashAttribute("ok", "Evento \"" + nombre + "\" enviado al administrador para su aprobación.");
        return "redirect:/preceptor/eventos";
    }
}
