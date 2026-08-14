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

    private com.upeu.comedorupeu.repository.MarcacionRepository marcacionRepo;

    @org.springframework.beans.factory.annotation.Autowired
    public void setMarcacionRepo(com.upeu.comedorupeu.repository.MarcacionRepository repo) {
        this.marcacionRepo = repo;
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
        List<Residente> lista = pab == null ? residenteRepo.findByEstadoOrderByApellidoAsc("ACTIVO")
                : residenteRepo.findByEstadoAndPabellonOrderByApellidoAsc("ACTIVO", pab);
        return lista.stream().filter(r -> !r.estaBorrado()).toList();
    }

    private List<Residente> residentesYaIngresados(Authentication auth) {
        return residentesActivosDe(auth).stream()
                .filter(com.upeu.comedorupeu.services.alcance.AlcanceDatos::yaEnVigencia)
                .toList();
    }

    private com.upeu.comedorupeu.services.VigenciaService vigenciaService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setVigenciaService(com.upeu.comedorupeu.services.VigenciaService vigenciaService) {
        this.vigenciaService = vigenciaService;
    }

    private com.upeu.comedorupeu.services.HistorialService historialService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setHistorialService(com.upeu.comedorupeu.services.HistorialService historialService) {
        this.historialService = historialService;
    }

    private String resumenHistorial(Residente r) {
        return historialService.resumen(r);
    }

    private void borrarHistorialDe(Residente r) {
        historialService.borrar(r);
    }

    @GetMapping("/residentes/exportar")
    public org.springframework.http.ResponseEntity<byte[]> exportarResidentes(
            @RequestParam(required = false) String q, Authentication auth) throws java.io.IOException {
        String pab = pabellonDe(auth);
        List<Residente> lista;
        if (q != null && !q.isBlank()) {
            lista = residenteRepo.buscar(q.trim(), pab);
        } else if (pab != null) {
            lista = residenteRepo.findByPabellonOrderByApellidoAsc(pab);
        } else {
            lista = residenteRepo.findAll();
        }

        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<String[]> datos = new java.util.ArrayList<>();
        int n = 1;
        for (Residente r : lista) {
            var apo = r.getApoderado();
            datos.add(new String[]{
                    String.valueOf(n++),
                    r.getNombre() == null ? "" : r.getNombre(),
                    r.getApellido() == null ? "" : r.getApellido(),
                    r.getDni() == null ? "—" : r.getDni(),
                    r.getCodigoAcceso() == null ? "" : r.getCodigoAcceso(),
                    r.getCarrera() == null ? "—" : r.getCarrera(),
                    r.getPabellon() == null ? "—" : r.getPabellon(),
                    r.getCuarto() == null ? "—" : r.getCuarto(),
                    r.getCelular() == null ? "—" : r.getCelular(),
                    apo == null || apo.getNombre() == null ? "—" : apo.getNombre(),
                    apo == null || apo.getRelacion() == null ? "—" : apo.getRelacion(),
                    apo == null || apo.getDni() == null ? "—" : apo.getDni(),
                    apo == null || apo.getTelefono() == null ? "—" : apo.getTelefono(),
                    r.getFechaIngreso() == null ? "—" : f.format(r.getFechaIngreso()),
                    r.getFechaFinEstancia() == null ? "—" : f.format(r.getFechaFinEstancia()),
                    r.getEstado() == null ? "—" : r.getEstado(),
                    Boolean.TRUE.equals(r.getDeuda()) ? "Con deuda" : "Pago al día",
                    r.getPreceptor() == null ? "—" : r.getPreceptor().getNombreCompleto()
            });
        }

        String filtros = "Residencia de género: " + (pab == null ? "todas" : pab)
                + (q == null || q.isBlank() ? "" : "   Búsqueda: " + q)
                + "   Total: " + datos.size();
        byte[] xlsx = excelService.exportarTabla("COMEDOR UPEU — Residentes", filtros,
                new String[]{"N°", "Nombres", "Apellidos", "DNI", "Código", "Carrera",
                        "Residencia de Género", "Cuarto", "Celular",
                        "Resp. Financiero", "Relación", "DNI Resp.", "Teléfono Resp.",
                        "Inicio de estancia", "Fin de estancia", "Estado", "Estado de pago", "Preceptor"},
                datos);
        String nombre = "residentes_" + LocalDate.now() + ".xlsx";
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
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
        lista = lista.stream().filter(x -> !x.estaBorrado()).toList();
        model.addAttribute("residentes", lista);
        model.addAttribute("q", q);
        model.addAttribute("pabellonScope", pab);
        List<Residente> enCasa = (pab == null ? residenteRepo.findAll() : residenteRepo.findByPabellonOrderByApellidoAsc(pab))
                .stream().filter(x -> !x.estaBorrado()).toList();
        model.addAttribute("total", enCasa.size());
        model.addAttribute("activos", enCasa.stream().filter(x -> "ACTIVO".equals(x.getEstado())).count());
        model.addAttribute("inactivos", enCasa.stream().filter(x -> "INACTIVO".equals(x.getEstado())).count());

        model.addAttribute("avisosPreceptor", apunteRepo.findTop10ByTipoOrderByFechaHoraDesc("PRECEPTOR").stream()
                .filter(com.upeu.comedorupeu.models.Apunte::estaVigente)
                .toList());
        model.addAttribute("comidasBloqueadas", turnoService.comidasBloqueadasHoy());
        model.addAttribute("hoy", LocalDate.now());

        java.util.List<java.util.Map<String, Object>> dietas = new java.util.ArrayList<>();
        for (Residente r : lista) {
            for (RacionEspecial re : racionEspecialRepo
                    .findByResidenteIdResidenteOrderByFechaInicioDesc(r.getIdResidente())) {
                java.util.List<String> comidas = new java.util.ArrayList<>();
                for (RacionEspecialDetalle d : re.getDetalles()) {
                    comidas.add(d.getFecha() + "|" + d.getTipoComida());
                }
                dietas.add(java.util.Map.of(
                        "id", re.getIdRacionEspecial(),
                        "residente", r.getIdResidente(),
                        "desde", re.getFechaInicio().toString(),
                        "hasta", re.getFechaFin().toString(),
                        "indicacion", re.getIndicacion() == null ? "" : re.getIndicacion(),
                        "evidencia", re.getEvidenciaUrl() == null ? "" : re.getEvidenciaUrl(),
                        "comidas", comidas,
                        "terminada", re.getFechaFin().isBefore(LocalDate.now())));
            }
        }
        model.addAttribute("dietas", dietas);
        return "preceptor/residentes";
    }

    @GetMapping("/residentes/nuevo")
    public String nuevoResidente(Model model, Authentication auth) {
        Residente r = new Residente();
        r.setFechaIngreso(LocalDate.now());
        model.addAttribute("residente", r);
        model.addAttribute("hoy", LocalDate.now());

        model.addAttribute("pabellonAuto", usuarioActual(auth).getPabellon());
        model.addAttribute("facultades", carrerasService.facultades());
        return "preceptor/residente_form";
    }

    private com.upeu.comedorupeu.services.ReglasComidaService reglasComidaService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setReglasComidaService(com.upeu.comedorupeu.services.ReglasComidaService reglasComidaService) {
        this.reglasComidaService = reglasComidaService;
    }

    private com.upeu.comedorupeu.repository.TurnoRepository turnoRepo;

    @org.springframework.beans.factory.annotation.Autowired
    public void setTurnoRepo(com.upeu.comedorupeu.repository.TurnoRepository turnoRepo) {
        this.turnoRepo = turnoRepo;
    }

    private boolean fueraDeSuResidencia(Residente r, Authentication auth) {
        if (r == null) return true;
        Usuario u = usuarioActual(auth);
        if (u == null) return true;
        if (!"PRECEPTOR".equals(u.getRol()) || u.getPabellon() == null) return false;
        return r.getPabellon() != null && !u.getPabellon().equals(r.getPabellon());
    }

    @GetMapping("/residentes/{id}/editar")
    public String editarResidente(@PathVariable Long id, Model model, Authentication auth) {

        Residente residente = residenteRepo.findById(id).orElse(null);
        if (residente == null) return "redirect:/preceptor/residentes";
        if (fueraDeSuResidencia(residente, auth)) return "redirect:/preceptor/residentes";
        model.addAttribute("residente", residente);
        model.addAttribute("pabellonAuto", usuarioActual(auth).getPabellon());
        model.addAttribute("facultades", carrerasService.facultades());
        model.addAttribute("historialResidente", resumenHistorial(residente));
        return "preceptor/residente_form";
    }

    @PostMapping("/residentes/{id}/eliminar")
    public String eliminarResidente(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        Residente r = residenteRepo.findById(id).orElse(null);
        if (r == null) return "redirect:/preceptor/residentes";
        if (fueraDeSuResidencia(r, auth)) {
            flash.addFlashAttribute("error", "Ese residente no pertenece a tu residencia de género.");
            return "redirect:/preceptor/residentes";
        }
        vigenciaService.marcarBorrado(r);
        r.setEstado("INACTIVO");
        r.setEliminado(true);
        residenteRepo.save(r);
        flash.addFlashAttribute("ok", "Residente " + r.getNombreCompleto() + " borrado: desde ahora ya no figura "
                + "en ninguna pantalla. Todo lo que hizo hasta este momento se conserva en el historial.");
        return "redirect:/preceptor/residentes";
    }

    @GetMapping("/residentes/{id}")
    public String verResidente(@PathVariable Long id, Authentication auth) {
        return "redirect:/preceptor/residentes";
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
        if (fueraDeSuResidencia(r, auth)) {
            flash.addFlashAttribute("error", "Ese residente no pertenece a tu residencia de género.");
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
        return "redirect:/preceptor/residentes";
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
            return "redirect:/preceptor/residentes";
        }
        ausenciaRepo.delete(a);
        flash.addFlashAttribute("ok", "Ausencia del " + a.getFechaInicio() + " al " + a.getFechaFin()
                + " eliminada. El residente ya no figura justificado en esas fechas.");
        return "redirect:/preceptor/residentes";
    }

    @PostMapping("/ausencias/{id}/cancelar")
    public String cancelarAusencia(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        Ausencia a = ausenciaRepo.findById(id).orElse(null);
        if (a == null) return "redirect:/preceptor/ausencias";
        if (fueraDeSuResidencia(a.getResidente(), auth)) {
            flash.addFlashAttribute("error", "Ese residente no pertenece a tu residencia de género.");
            return "redirect:/preceptor/ausencias";
        }
        Usuario quien = usuarioActual(auth);
        String resultado = justificacionService.cierreAnticipado(a);
        if (resultado != null) {
            resultado += " Cancelada por " + quien.getNombreCompleto() + " (" + quien.getRol() + ").";
        }
        if (resultado == null) {
            flash.addFlashAttribute("error", "Esta ausencia no está en curso: no aplica el cierre anticipado.");
        } else {
            flash.addFlashAttribute("ok", resultado);
        }
        return "redirect:/preceptor/ausencias";
    }

    @PostMapping("/solicitudes/{id}/cancelar")
    public String cancelarSolicitud(@PathVariable Long id,
                                    @RequestParam(required = false) String motivoAccion,
                                    Authentication auth, RedirectAttributes flash) {
        SolicitudExtemporanea s = solicitudRepo.findById(id).orElse(null);
        if (s == null) return "redirect:/preceptor/residentes";
        Long idResidente = s.getResidente().getIdResidente();
        if (fueraDeSuResidencia(s.getResidente(), auth)) {
            flash.addFlashAttribute("error", "Ese residente no pertenece a tu residencia de género.");
        } else if (!"PENDIENTE".equals(s.getEstado())) {
            flash.addFlashAttribute("error", "Esa reserva ya fue atendida por el cajero; no se puede cancelar.");
        } else if (turnoService.turnoYaOcurrio(s.getTipoComida(), s.getFecha())) {

            flash.addFlashAttribute("error", "El turno de esa reserva ya pasó: queda como historial y no se puede cancelar.");
        } else {
            Usuario quien = usuarioActual(auth);
            s.setEstado("CANCELADA");
            s.setCanceladaPor(quien.getNombreCompleto() + " (" + quien.getRol() + ")");
            s.setMotivoCancelacion(motivoAccion == null || motivoAccion.isBlank() ? null : motivoAccion.trim());
            s.setFechaCancelacion(java.time.LocalDateTime.now());
            solicitudRepo.save(s);
            flash.addFlashAttribute("ok", "Reserva de " + s.getTipoComida().toLowerCase()
                    + " del " + s.getFecha() + " cancelada por " + s.getCanceladaPor() + ".");
        }
        return "redirect:/preceptor/residentes";
    }

    @PostMapping("/eventos/{id}/excluir")
    public String excluirDeEntrega(@PathVariable Long id, @RequestParam String codigo,
                                   Authentication auth, RedirectAttributes flash) {
        EventoEspecial e = entregaEditable(id, auth, flash);
        if (e == null) return "redirect:/preceptor/eventos?detalle=" + id;

        java.util.Set<String> ex = new java.util.LinkedHashSet<>(e.getExcluidosLista());
        ex.add(codigo.trim());
        e.setExcluidos(String.join(",", ex));
        eventoRepo.save(e);
        flash.addFlashAttribute("ok", codigo + " ya no recibirá ración de \"" + e.getNombre() + "\".");
        return "redirect:/preceptor/eventos?detalle=" + id;
    }

    @PostMapping("/eventos/{id}/incluir")
    public String incluirEnEntrega(@PathVariable Long id, @RequestParam String codigo,
                                   Authentication auth, RedirectAttributes flash) {
        EventoEspecial e = entregaEditable(id, auth, flash);
        if (e == null) return "redirect:/preceptor/eventos?detalle=" + id;

        List<String> ex = new java.util.ArrayList<>(e.getExcluidosLista());
        ex.remove(codigo.trim());
        e.setExcluidos(String.join(",", ex));
        eventoRepo.save(e);
        flash.addFlashAttribute("ok", codigo + " vuelve a la lista de \"" + e.getNombre() + "\".");
        return "redirect:/preceptor/eventos?detalle=" + id;
    }

    private EventoEspecial entregaEditable(Long id, Authentication auth, RedirectAttributes flash) {
        EventoEspecial e = eventoRepo.findById(id).orElse(null);
        if (e == null) {
            flash.addFlashAttribute("error", "La entrega ya no existe.");
            return null;
        }
        if (!esDeMiResidencia(e, auth)) {
            flash.addFlashAttribute("error", "Esa entrega la envió otra preceptoría: no puedes cambiar su lista.");
            return null;
        }
        if (!"PENDIENTE".equals(e.getEstado())) {
            flash.addFlashAttribute("error", "\"" + e.getNombre() + "\" ya no está pendiente: la lista quedó "
                    + "cerrada. Si alguien ya no va a recoger su ración, no lo marques en el pase de lista.");
            return null;
        }
        return e;
    }

    @PostMapping("/eventos/{id}/eliminar")
    public String eliminarEvento(@PathVariable Long id,
                                 @RequestParam(required = false) String motivoAccion,
                                 Authentication auth, RedirectAttributes flash) {
        EventoEspecial e = eventoRepo.findById(id).orElse(null);
        if (e == null) return "redirect:/preceptor/eventos";

        if (!esDeMiResidencia(e, auth)) {
            flash.addFlashAttribute("error", "Esa entrega la envió otra preceptoría: solo ellos pueden cancelarla.");
            return "redirect:/preceptor/eventos";
        }
        if ("CANCELADO".equals(e.getEstado())) {
            flash.addFlashAttribute("error", "\"" + e.getNombre() + "\" ya estaba cancelada.");
            return "redirect:/preceptor/eventos";
        }
        if (!"PENDIENTE".equals(e.getEstado())) {
            flash.addFlashAttribute("error", "\"" + e.getNombre() + "\" ya fue revisada por el administrador y quedó "
                    + e.getEstado().toLowerCase() + ": solo se puede cancelar mientras siga pendiente. "
                    + "Si ya no se va a entregar, pídele al administrador que la rechace.");
            return "redirect:/preceptor/eventos";
        }

        List<EventoEspecial> grupo = (e.getGrupoEvento() == null || e.getGrupoEvento().isBlank())
                ? List.of(e) : eventoRepo.findByGrupoEvento(e.getGrupoEvento());
        if (grupo.isEmpty()) grupo = List.of(e);

        Usuario quien = usuarioActual(auth);
        String firma = quien.getNombreCompleto() + " (" + quien.getRol() + ")";
        String motivo = (motivoAccion == null || motivoAccion.isBlank()) ? null : motivoAccion.trim();

        for (EventoEspecial comida : grupo) {
            entregaRepo.deleteAll(entregaRepo.findByEventoIdEvento(comida.getIdEvento()));
            comida.setEstado("CANCELADO");
            comida.setCanceladoPor(firma);
            comida.setMotivoCancelacion(motivo);
            comida.setFechaCancelacion(java.time.LocalDateTime.now());
            eventoRepo.save(comida);
        }
        String cuantas = grupo.size() == 1 ? "" : " (" + grupo.size() + " comidas)";
        flash.addFlashAttribute("ok", "Entrega \"" + e.getNombre() + "\" cancelado" + cuantas
                + " por " + firma + ". El administrador lo verá como cancelado.");
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
                                   @RequestParam(required = false) LocalDate fechaIngreso,
                                   @RequestParam(required = false) LocalDate fechaFinEstancia,
                                   @RequestParam(required = false) MultipartFile foto,
                                   @RequestParam(required = false) String apoNombre,
                                   @RequestParam(required = false) String apoRelacion,
                                   @RequestParam(required = false) String apoDni,
                                   @RequestParam(required = false) String apoTelefono,
                                   @RequestParam(required = false) Boolean borrarHistorial,
                                   Authentication auth,
                                   RedirectAttributes flash) {
        Residente r = (idResidente != null) ? residenteRepo.findById(idResidente).orElse(new Residente()) : new Residente();

        if (idResidente != null && fueraDeSuResidencia(r, auth)) {
            flash.addFlashAttribute("error", "Ese residente no pertenece a tu residencia de género.");
            return "redirect:/preceptor/residentes";
        }

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
        final String estadoPrevio = r.getEstado();
        r.setEstado(estado);
        r.setDeuda(deuda);

        LocalDate hoy = LocalDate.now();
        if (fechaIngreso != null) {
            boolean cambia = r.getFechaIngreso() == null || !fechaIngreso.equals(r.getFechaIngreso());
            if (cambia && fechaIngreso.isBefore(hoy)) {
                flash.addFlashAttribute("error", "El inicio de estancia no puede ser anterior a hoy ("
                        + hoy + "). Elige hoy o una fecha posterior.");
                return idResidente == null ? "redirect:/preceptor/residentes/nuevo"
                        : "redirect:/preceptor/residentes/" + idResidente + "/editar";
            }

            boolean cambiaIngreso = idResidente != null
                    && r.getFechaIngreso() != null
                    && !fechaIngreso.equals(r.getFechaIngreso());
            if (cambiaIngreso) {
                String historial = resumenHistorial(r);
                if (historial != null && !Boolean.TRUE.equals(borrarHistorial)) {
                    flash.addFlashAttribute("error", "No se puede mover el inicio de estancia de "
                            + r.getNombreCompleto() + ": ya tiene movimientos registrados (" + historial + "). "
                            + "Marca la casilla de confirmación del formulario si aceptas que TODO ese historial "
                            + "se borre para poder cambiar la fecha.");
                    return "redirect:/preceptor/residentes/" + idResidente + "/editar";
                }
                if (historial != null) {
                    borrarHistorialDe(r);
                    flash.addFlashAttribute("ok", "Se borró el historial de " + r.getNombreCompleto()
                            + " (" + historial + ") para poder mover su inicio de estancia.");
                }
            }
            r.setFechaIngreso(fechaIngreso);
        } else if (r.getFechaIngreso() == null) {
            r.setFechaIngreso(hoy);
        }
        if (fechaFinEstancia != null) {
            if (fechaFinEstancia.isBefore(r.getFechaIngreso())) {
                flash.addFlashAttribute("error", "El fin de estancia no puede ser anterior a su inicio.");
                return idResidente == null ? "redirect:/preceptor/residentes/nuevo"
                        : "redirect:/preceptor/residentes/" + idResidente + "/editar";
            }
            r.setFechaFinEstancia(fechaFinEstancia);
        } else if (r.getFechaFinEstancia() == null) {
            LocalDate ini = r.getFechaIngreso();
            r.setFechaFinEstancia(ini.getMonthValue() >= 7
                    ? LocalDate.of(ini.getYear(), 12, 31)
                    : LocalDate.of(ini.getYear(), 6, 30));
        }
        if (r.getTokenAcceso() == null) r.setTokenAcceso(java.util.UUID.randomUUID().toString());
        if (r.getPreceptor() == null) r.setPreceptor(quienRegistra);

        try {
            String url = imagenService.guardar(foto, r);
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

        if (!"INACTIVO".equals(estadoPrevio) && "INACTIVO".equals(estado)) {
            vigenciaService.marcarInactivo(r);
        } else if ("INACTIVO".equals(estadoPrevio) && "ACTIVO".equals(estado)) {
            vigenciaService.marcarActivo(r);
        }

        flash.addFlashAttribute("ok", "Residente " + r.getNombreCompleto() + " guardado correctamente."
                + ("INACTIVO".equals(estado) && !"INACTIVO".equals(estadoPrevio)
                   ? " Queda inactivo desde este momento: lo anterior se conserva y los turnos que vengan figurarán como I."
                   : ""));
        return "redirect:/preceptor/residentes";
    }

    @GetMapping("/ausencias")
    public String ausencias(@RequestParam(required = false) Long editar,
                            Model model, Authentication auth, RedirectAttributes flash) {
        model.addAttribute("residentes", residentesYaIngresados(auth));
        model.addAttribute("hoy", LocalDate.now());

        if (editar != null) {
            Ausencia a = ausenciaRepo.findById(editar).orElse(null);
            if (a == null || fueraDeSuResidencia(a.getResidente(), auth)) {
                flash.addFlashAttribute("error", "Esa justificación no existe o no pertenece a tu residencia.");
                return "redirect:/admin/justificaciones";
            }
            String estado = justificacionService.estadoDe(a);
            if (!"FUTURA".equals(estado) && !"EN_CURSO".equals(estado)) {
                flash.addFlashAttribute("error", "Esa justificación ya terminó: no se puede editar "
                        + "porque su historial ya está escrito por completo.");
                return "redirect:/admin/justificaciones";
            }
            model.addAttribute("editando", a);
            model.addAttribute("yaEmpezo", "EN_CURSO".equals(estado));

            java.util.List<String> marcadas = new java.util.ArrayList<>();
            java.util.List<String> intocables = new java.util.ArrayList<>();
            for (AusenciaDetalle d : a.getDetalles()) {
                marcadas.add(d.getFecha() + "|" + d.getTipoComida());
                if (turnoService.turnoYaOcurrio(d.getTipoComida(), d.getFecha())) {
                    intocables.add(d.getFecha() + "|" + d.getTipoComida());
                }
            }
            model.addAttribute("comidasMarcadas", marcadas);
            model.addAttribute("comidasIntocables", intocables);
        }

        model.addAttribute("comidasBloqueadas", turnoService.comidasBloqueadasHoy());

        return "preceptor/ausencias";
    }

    private String actualizarAusencia(Long idEditar, LocalDate desde, LocalDate hasta, String motivo,
                                      MultipartFile evidencia, HttpServletRequest request,
                                      Authentication auth, RedirectAttributes flash) {
        Ausencia a = ausenciaRepo.findById(idEditar).orElse(null);
        if (a == null || fueraDeSuResidencia(a.getResidente(), auth)) {
            flash.addFlashAttribute("error", "Esa justificación no existe o no pertenece a tu residencia.");
            return "redirect:/admin/justificaciones";
        }
        String estado = justificacionService.estadoDe(a);
        if (!"FUTURA".equals(estado) && !"EN_CURSO".equals(estado)) {
            flash.addFlashAttribute("error", "Esa justificación ya terminó: no se puede editar.");
            return "redirect:/admin/justificaciones";
        }
        boolean yaEmpezo = "EN_CURSO".equals(estado);
        if (!yaEmpezo && desde.isBefore(LocalDate.now())) {
            flash.addFlashAttribute("error", "No se puede justificar un día que ya pasó.");
            return "redirect:/preceptor/ausencias?editar=" + idEditar;
        }

        Residente r = a.getResidente();
        Long idRes = r.getIdResidente();
        java.util.List<AusenciaDetalle> nuevos = new java.util.ArrayList<>();
        int yaComidas = 0;
        int conservadas = 0;

        for (AusenciaDetalle viejo : a.getDetalles()) {
            if (!turnoService.turnoYaOcurrio(viejo.getTipoComida(), viejo.getFecha())) continue;
            AusenciaDetalle d = new AusenciaDetalle();
            d.setAusencia(a);
            d.setFecha(viejo.getFecha());
            d.setTipoComida(viejo.getTipoComida());
            nuevos.add(d);
            conservadas++;
        }

        LocalDate inicioBucle = yaEmpezo && desde.isBefore(LocalDate.now()) ? LocalDate.now() : desde;
        for (LocalDate f = inicioBucle; !f.isAfter(hasta); f = f.plusDays(1)) {
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
                if (!incluir) continue;
                if (turnoService.turnoYaOcurrio(tipo, f)) continue;
                if (reglasComidaService.yaIngreso(r, f, tipo)) { yaComidas++; continue; }

                AusenciaDetalle d = new AusenciaDetalle();
                d.setAusencia(a);
                d.setFecha(f);
                d.setTipoComida(tipo);
                nuevos.add(d);
            }
        }

        if (nuevos.isEmpty()) {
            flash.addFlashAttribute("error", "No marcaste ninguna comida: la justificación se quedó como estaba.");
            return "redirect:/preceptor/ausencias?editar=" + idEditar;
        }

        if (evidencia != null && !evidencia.isEmpty()) {
            try {
                a.setEvidenciaUrl(imagenService.guardarEvidencia(evidencia, "ausencia"));
            } catch (Exception e) {
                flash.addFlashAttribute("error", "No se pudo guardar la nueva evidencia: " + e.getMessage());
                return "redirect:/preceptor/ausencias?editar=" + idEditar;
            }
        }

        LocalDate inicioFinal = yaEmpezo ? a.getFechaInicio() : desde;
        LocalDate finFinal = nuevos.stream().map(AusenciaDetalle::getFecha)
                .max(LocalDate::compareTo).orElse(hasta);

        a.getDetalles().clear();
        a.getDetalles().addAll(nuevos);
        a.setFechaInicio(inicioFinal);
        a.setFechaFin(finFinal);
        if (motivo != null && !motivo.isBlank()) a.setMotivo(motivo.trim());
        ausenciaRepo.save(a);

        String msg = "Justificación de " + r.getNombreCompleto() + " actualizada: del " + inicioFinal
                + " al " + finFinal + ", " + nuevos.size() + " comida(s).";
        if (conservadas > 0) {
            msg += " " + conservadas + " comida(s) ya transcurridas se conservaron intactas.";
        }
        if (yaComidas > 0) {
            msg += " " + yaComidas + " comida(s) quedaron fuera porque el residente ya ingresó a ese turno.";
        }
        flash.addFlashAttribute("ok", msg);
        return "redirect:/admin/justificaciones";
    }

    @PostMapping("/ausencias/guardar")
    public String guardarAusencia(@RequestParam("ids") List<Long> ids,
                                  @RequestParam LocalDate desde,
                                  @RequestParam LocalDate hasta,
                                  @RequestParam String motivo,
                                  @RequestParam(required = false) MultipartFile evidencia,
                                  @RequestParam(required = false) Long idEditar,
                                  HttpServletRequest request,
                                  Authentication auth,
                                  RedirectAttributes flash) {
        if (hasta.isBefore(desde)) {
            flash.addFlashAttribute("error", "El rango de fechas no es válido.");
            return "redirect:/preceptor/ausencias";
        }
        if (idEditar != null) {
            return actualizarAusencia(idEditar, desde, hasta, motivo, evidencia, request, auth, flash);
        }
        if (desde.isBefore(LocalDate.now())) {
            flash.addFlashAttribute("error", "No se puede justificar un día que ya pasó. "
                    + "La justificación se registra antes de la ausencia, no después.");
            return "redirect:/preceptor/ausencias";
        }
        if (desde.equals(LocalDate.now()) && turnoService.comidasBloqueadasHoy().size() == 3) {
            flash.addFlashAttribute("error", "Los tres turnos de hoy ya cerraron: "
                    + "no queda ninguna comida por justificar. Elige a partir de mañana.");
            return "redirect:/preceptor/ausencias";
        }
        Usuario preceptor = usuarioActual(auth);
        int registrados = 0;
        final int[] reservasAnuladas = {0};

        String evidenciaUrl = null;
        if (evidencia != null && !evidencia.isEmpty()) {
            try {
                evidenciaUrl = imagenService.guardarEvidencia(evidencia, "ausencia");
            } catch (Exception e) {
                flash.addFlashAttribute("error", "No se pudo guardar la evidencia: " + e.getMessage());
                return "redirect:/preceptor/ausencias";
            }
        }

        java.util.List<String> aunNoIngresan = new java.util.ArrayList<>();
        java.util.List<String> sinNadaQueJustificar = new java.util.ArrayList<>();
        final int[] comidasYaJustificadas = {0};
        final int[] comidasYaComidas = {0};
        java.util.List<String> evidenciasFallidas = new java.util.ArrayList<>();

        for (Long idRes : ids) {
            Residente r = residenteRepo.findById(idRes).orElse(null);
            if (r == null || fueraDeSuResidencia(r, auth)) continue;

            if (r.estaBorrado()
                    || !com.upeu.comedorupeu.services.alcance.AlcanceDatos.yaEnVigencia(r)) {
                aunNoIngresan.add(r.getNombreCompleto()
                        + (r.getFechaIngreso() != null ? " (ingresa el " + r.getFechaIngreso() + ")" : ""));
                continue;
            }

            Ausencia ausencia = new Ausencia();
            ausencia.setResidente(r);
            ausencia.setUsuario(preceptor);
            ausencia.setFechaInicio(desde);
            ausencia.setFechaFin(hasta);

            String motivoPropio = request.getParameter("motivoInd_" + idRes);
            ausencia.setMotivo((motivoPropio != null && !motivoPropio.isBlank())
                    ? motivoPropio.trim() : motivo);

            String urlPropia = evidenciaUrl;
            if (request instanceof org.springframework.web.multipart.MultipartHttpServletRequest multi) {
                MultipartFile suya = multi.getFile("evidenciaInd_" + idRes);
                if (suya != null && !suya.isEmpty()) {
                    try {
                        urlPropia = imagenService.guardarEvidencia(suya, "ausencia");
                    } catch (Exception e) {
                        evidenciasFallidas.add(r.getNombreCompleto());
                    }
                }
            }
            ausencia.setEvidenciaUrl(urlPropia);

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
                    if (!incluir) continue;

                    if (reglasComidaService.yaIngreso(r, f, tipo)) {
                        comidasYaComidas[0]++;
                        continue;
                    }
                    if (reglasComidaService.yaJustificado(r, f, tipo)) {
                        comidasYaJustificadas[0]++;
                    }

                    AusenciaDetalle d = new AusenciaDetalle();
                    d.setAusencia(ausencia);
                    d.setFecha(f);
                    d.setTipoComida(tipo);
                    ausencia.getDetalles().add(d);
                }
            }
            if (ausencia.getDetalles().isEmpty()) {
                sinNadaQueJustificar.add(r.getNombreCompleto());
                continue;
            }

            ausenciaRepo.save(ausencia);
            registrados++;

            for (AusenciaDetalle d : ausencia.getDetalles()) {
                solicitudRepo.findFirstByResidenteIdResidenteAndFechaAndTipoComidaAndEstado(
                                r.getIdResidente(), d.getFecha(), d.getTipoComida(), "PENDIENTE")
                        .ifPresent(s -> {
                            s.setEstado("CANCELADA");
                            s.setCanceladaPor(preceptor.getNombreCompleto()
                                    + " (justificación registrada)");
                            solicitudRepo.save(s);
                            reservasAnuladas[0]++;
                        });
            }
        }
        String aviso = aunNoIngresan.isEmpty() ? ""
                : " Se omitió a " + String.join(", ", aunNoIngresan)
                  + ": todavía no empieza su estancia, así que no hay nada que justificar.";
        if (comidasYaJustificadas[0] > 0) {
            aviso += " Aviso: " + comidasYaJustificadas[0] + " comida(s) ya estaban justificadas por otra"
                    + " ausencia anterior. Se registraron igual en esta, así que quedan cubiertas por las dos.";
        }
        if (comidasYaComidas[0] > 0) {
            aviso += " " + comidasYaComidas[0] + " comida(s) no se justificaron porque el residente"
                    + " YA INGRESÓ a ese turno: la ración ya se entregó.";
        }
        if (!sinNadaQueJustificar.isEmpty()) {
            aviso += " No se creó justificación para " + String.join(", ", sinNadaQueJustificar)
                    + ": todas las comidas elegidas ya se habían consumido.";
        }
        if (!evidenciasFallidas.isEmpty()) {
            aviso += " No se pudo guardar la foto propia de " + String.join(", ", evidenciasFallidas)
                    + ": quedaron con la evidencia general.";
        }

        if (registrados == 0) {
            flash.addFlashAttribute("error", "No se registró ninguna ausencia."
                    + (aviso.isEmpty()
                       ? " Revisa que los residentes seleccionados pertenezcan a tu residencia de género."
                       : aviso));
        } else {
            String msg = "Ausencia registrada para " + registrados + " residente(s) del "
                    + desde + " al " + hasta + ".";
            if (reservasAnuladas[0] > 0) {
                msg += " Se cancelaron " + reservasAnuladas[0] + " reserva(s) que coincidían con "
                        + "las comidas justificadas: la justificación manda sobre la reserva.";
            }
            flash.addFlashAttribute("ok", msg + aviso);
        }
        return "redirect:/preceptor/ausencias";
    }

    private String actualizarDieta(Long idEditar, Residente r, LocalDate desde, LocalDate hasta,
                                   String indicacion, MultipartFile evidencia,
                                   HttpServletRequest request, RedirectAttributes flash) {
        RacionEspecial re = racionEspecialRepo.findById(idEditar).orElse(null);
        if (re == null || !re.getResidente().getIdResidente().equals(r.getIdResidente())) {
            flash.addFlashAttribute("error", "Esa dieta no existe o no es de ese residente.");
            return "redirect:/preceptor/residentes";
        }
        if (re.getFechaFin().isBefore(LocalDate.now())) {
            flash.addFlashAttribute("error", "Esa dieta ya terminó: no se puede editar. "
                    + "Si necesitas otra, crea una nueva.");
            return "redirect:/preceptor/residentes";
        }

        java.util.List<RacionEspecialDetalle> nuevos = new java.util.ArrayList<>();
        int conservadas = 0;
        for (RacionEspecialDetalle viejo : re.getDetalles()) {
            if (!turnoService.turnoYaOcurrio(viejo.getTipoComida(), viejo.getFecha())) continue;
            RacionEspecialDetalle d = new RacionEspecialDetalle();
            d.setRacionEspecial(re);
            d.setFecha(viejo.getFecha());
            d.setTipoComida(viejo.getTipoComida());
            nuevos.add(d);
            conservadas++;
        }

        LocalDate arranque = desde.isBefore(LocalDate.now()) ? LocalDate.now() : desde;
        for (LocalDate f = arranque; !f.isAfter(hasta); f = f.plusDays(1)) {
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
                if (!incluir) continue;
                if (turnoService.turnoYaOcurrio(tipo, f)) continue;

                RacionEspecialDetalle d = new RacionEspecialDetalle();
                d.setRacionEspecial(re);
                d.setFecha(f);
                d.setTipoComida(tipo);
                nuevos.add(d);
            }
        }

        if (nuevos.isEmpty()) {
            flash.addFlashAttribute("error", "No marcaste ninguna comida: la dieta se quedó como estaba.");
            return "redirect:/preceptor/residentes";
        }

        if (evidencia != null && !evidencia.isEmpty()) {
            try {
                re.setEvidenciaUrl(imagenService.guardarEvidencia(evidencia, "dieta"));
            } catch (Exception e) {
                flash.addFlashAttribute("error", "No se pudo guardar la nueva indicación médica: " + e.getMessage());
                return "redirect:/preceptor/residentes";
            }
        }

        LocalDate finReal = nuevos.stream().map(RacionEspecialDetalle::getFecha)
                .max(LocalDate::compareTo).orElse(hasta);
        LocalDate iniReal = nuevos.stream().map(RacionEspecialDetalle::getFecha)
                .min(LocalDate::compareTo).orElse(desde);

        re.getDetalles().clear();
        re.getDetalles().addAll(nuevos);
        re.setFechaInicio(iniReal);
        re.setFechaFin(finReal);
        re.setIndicacion((indicacion == null || indicacion.isBlank()) ? null : indicacion.trim());
        racionEspecialRepo.save(re);

        String msg = "Dieta de " + r.getNombreCompleto() + " actualizada: del " + iniReal
                + " al " + finReal + ", " + nuevos.size() + " comida(s).";
        if (conservadas > 0) {
            msg += " " + conservadas + " comida(s) ya transcurridas se conservaron intactas.";
        }
        flash.addFlashAttribute("ok", msg);
        return "redirect:/preceptor/residentes";
    }

    @PostMapping("/residentes/{id}/racion-especial")
    public String guardarRacionEspecial(@PathVariable Long id,
                                        @RequestParam LocalDate desde,
                                        @RequestParam LocalDate hasta,
                                        @RequestParam(required = false) String indicacion,
                                        @RequestParam(required = false) MultipartFile evidencia,
                                        @RequestParam(required = false) Long idEditar,
                                        HttpServletRequest request,
                                        Authentication auth,
                                        RedirectAttributes flash) {
        Residente r = residenteRepo.findById(id).orElse(null);
        if (r == null) return "redirect:/preceptor/residentes";
        if (fueraDeSuResidencia(r, auth)) {
            flash.addFlashAttribute("error", "Ese residente no pertenece a tu residencia de género.");
            return "redirect:/preceptor/residentes";
        }
        if (hasta.isBefore(desde)) {
            flash.addFlashAttribute("error", "El rango de fechas no es válido.");
            return "redirect:/preceptor/residentes";
        }
        if (idEditar != null) {
            return actualizarDieta(idEditar, r, desde, hasta, indicacion, evidencia, request, flash);
        }
        if (desde.isBefore(LocalDate.now())) {
            flash.addFlashAttribute("error", "La dieta no puede empezar en un día que ya pasó.");
            return "redirect:/preceptor/residentes";
        }
        if (r.getFechaIngreso() != null && desde.isBefore(r.getFechaIngreso())) {
            flash.addFlashAttribute("error", r.getNombreCompleto() + " empieza su estancia el "
                    + r.getFechaIngreso() + ": la dieta no puede arrancar antes de ese día.");
            return "redirect:/preceptor/residentes";
        }

        String evidenciaUrl = null;
        if (evidencia != null && !evidencia.isEmpty()) {
            try {
                evidenciaUrl = imagenService.guardarEvidencia(evidencia, "dieta");
            } catch (Exception e) {
                flash.addFlashAttribute("error", "No se pudo guardar la indicación médica: " + e.getMessage());
                return "redirect:/preceptor/residentes";
            }
        }

        RacionEspecial re = new RacionEspecial();
        re.setResidente(r);
        re.setUsuario(usuarioActual(auth));
        re.setFechaInicio(desde);
        re.setFechaFin(hasta);
        re.setEvidenciaUrl(evidenciaUrl);
        re.setIndicacion((indicacion == null || indicacion.isBlank()) ? null : indicacion.trim());

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
            return "redirect:/preceptor/residentes";
        }
        racionEspecialRepo.save(re);
        flash.addFlashAttribute("ok", "Ración especial asignada a " + r.getNombreCompleto()
                + " (" + re.getDetalles().size() + " comida(s) del " + desde + " al " + hasta + ").");
        return "redirect:/preceptor/residentes";
    }

    @PostMapping("/raciones-especiales/{id}/eliminar")
    public String eliminarRacionEspecial(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        RacionEspecial re = racionEspecialRepo.findById(id).orElse(null);
        if (re == null) {
            flash.addFlashAttribute("error", "Esa dieta ya no existe.");
            return "redirect:/preceptor/residentes";
        }
        if (fueraDeSuResidencia(re.getResidente(), auth)) {
            flash.addFlashAttribute("error", "Ese residente no pertenece a tu residencia de género.");
            return "redirect:/preceptor/residentes";
        }
        String quien = re.getResidente().getNombreCompleto();
        String periodo = re.getFechaInicio() + " al " + re.getFechaFin();
        racionEspecialRepo.delete(re);
        flash.addFlashAttribute("ok", "Dieta de " + quien + " (" + periodo + ") eliminada.");
        return "redirect:/preceptor/residentes";
    }

    @GetMapping("/eventos")
    public String eventos(@RequestParam(required = false) Long lista,
                          @RequestParam(required = false) Long detalle,
                          Model model, Authentication auth) {

        if (lista != null) return "redirect:/preceptor/pase-lista?evento=" + lista;

        List<Residente> activos = residentesActivosDe(auth);
        List<EventoEspecial> deMiResidencia = entregasDeMiResidencia(auth);
        List<EventoEspecial> aprobados = deMiResidencia.stream()
                .filter(e -> "APROBADO".equals(e.getEstado())).toList();
        model.addAttribute("residentes", activos);
        model.addAttribute("activos", activos.size());
        model.addAttribute("misEventos", deMiResidencia);
        model.addAttribute("eventosAprobados", aprobados);
        model.addAttribute("listaHecha", paseDeListaHecho(aprobados, activos));
        model.addAttribute("hoy", LocalDate.now());

        if (detalle != null) {
            eventoRepo.findById(detalle)
                    .filter(evento -> esDeMiResidencia(evento, auth))
                    .ifPresent(evento -> cargarDatosEvento(model, evento, activos, "eventoDetalle"));
        }
        return "preceptor/eventos";
    }

    @GetMapping("/pase-lista")
    public String paseLista(@RequestParam(required = false) Long evento, Model model, Authentication auth) {
        List<Residente> activos = residentesActivosDe(auth);
        List<EventoEspecial> aprobados = entregasDeMiResidencia(auth).stream()
                .filter(e -> "APROBADO".equals(e.getEstado())).toList();
        model.addAttribute("eventosAprobados", aprobados);
        model.addAttribute("listaHecha", paseDeListaHecho(aprobados, activos));
        if (evento != null) {
            eventoRepo.findById(evento)
                    .filter(e -> "APROBADO".equals(e.getEstado()))
                    .filter(e -> esDeMiResidencia(e, auth))
                    .ifPresent(e -> cargarDatosEvento(model, e, activos, "eventoLista"));
        }
        return "preceptor/pase_lista";
    }

    private List<EventoEspecial> entregasDeMiResidencia(Authentication auth) {
        return eventoRepo.findAllByOrderByFechaEnvioDesc().stream()
                .filter(e -> esDeMiResidencia(e, auth))
                .toList();
    }

    private boolean esDeMiResidencia(EventoEspecial e, Authentication auth) {
        String mia = pabellonDe(auth);
        if (mia == null) return true;
        String suya = (e.getUsuario() == null) ? null : e.getUsuario().getPabellon();
        return suya == null || mia.equals(suya);
    }

    private java.util.Map<Long, Boolean> paseDeListaHecho(List<EventoEspecial> eventos, List<Residente> activos) {
        java.util.Set<Long> mios = activos.stream().map(Residente::getIdResidente)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, Boolean> hecho = new java.util.HashMap<>();
        for (EventoEspecial e : eventos) {
            boolean hay = entregaRepo.findByEventoIdEvento(e.getIdEvento()).stream()
                    .anyMatch(en -> mios.contains(en.getResidente().getIdResidente()));
            hecho.put(e.getIdEvento(), hay);
        }
        return hecho;
    }

    private void cargarDatosEvento(Model model, EventoEspecial evento, List<Residente> activos, String nombreAttr) {
        var excluidos = evento.getExcluidosLista();
        var enLaResidencia = activos.stream()
                .filter(r -> !excluidos.contains(r.getCodigoAcceso()))
                .filter(r -> com.upeu.comedorupeu.services.alcance.AlcanceDatos
                        .vigenteEn(r, evento.getFechaEvento()))
                .toList();
        List<Residente> justificadosLista = new java.util.ArrayList<>();
        List<Residente> participantes = new java.util.ArrayList<>();
        for (Residente r : enLaResidencia) {
            boolean fuera = evento.getComida() != null && evento.getFechaEvento() != null
                    && reglasComidaService.yaJustificado(r, evento.getFechaEvento(), evento.getComida());
            if (fuera) justificadosLista.add(r);
            else participantes.add(r);
        }
        model.addAttribute("justificadosLista", justificadosLista);
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
            flash.addFlashAttribute("error", "La entrega ya no existe.");
            return "redirect:/preceptor/eventos";
        }
        if (!esDeMiResidencia(evento, auth)) {
            flash.addFlashAttribute("error", "Esa entrega la envió otra preceptoría: su pase de lista lo pasan ellos.");
            return "redirect:/preceptor/eventos";
        }
        if (!"APROBADO".equals(evento.getEstado())) {
            flash.addFlashAttribute("error", "\"" + evento.getNombre() + "\" no está aprobada: todavía no hay pase de lista.");
            return "redirect:/preceptor/eventos";
        }

        Usuario quien = usuarioActual(auth);
        Turno turno = turnoDeLaEntrega(evento);
        int recibidos = 0, justificados = 0, yaEnComedor = 0, anuladas = 0;

        for (Residente r : residentesActivosDe(auth)) {
            if (evento.getExcluidosLista().contains(r.getCodigoAcceso())) continue;
            if (turno != null && reglasComidaService.yaJustificado(r, evento.getFechaEvento(), evento.getComida())) {
                justificados++;
                continue;
            }
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

            if (turno == null) continue;
            String marca = "LISTA#" + evento.getIdEvento();
            Marcacion mia = null;
            boolean laDioElCajero = false;
            for (Marcacion m : marcacionRepo
                    .findByResidenteIdResidenteAndTurnoIdTurno(r.getIdResidente(), turno.getIdTurno())) {
                if (m.getObservacion() != null && m.getObservacion().startsWith(marca)) mia = m;
                else if (!Boolean.TRUE.equals(m.getAnulada()) && "PERMITIDO".equals(m.getEstado())) laDioElCajero = true;
            }

            if (recibido) {
                if (laDioElCajero) { yaEnComedor++; continue; }
                if (mia == null) {
                    mia = new Marcacion();
                    mia.setResidente(r);
                    mia.setTurno(turno);
                    mia.setEstado("PERMITIDO");
                    mia.setFechaHora(evento.getFechaEvento().atTime(java.time.LocalTime.now()));
                    mia.setObservacion(marca + " · " + evento.getNombre() + " (entregado por preceptoría)");
                }
                mia.setUsuario(quien);
                mia.setAnulada(false);
                mia.setAclaracion(null);
                marcacionRepo.save(mia);
            } else if (mia != null && !Boolean.TRUE.equals(mia.getAnulada())) {
                mia.setAnulada(true);
                mia.setAclaracion("El preceptor lo desmarcó del pase de lista: no recibió la ración.");
                marcacionRepo.save(mia);
                anuladas++;
            }
        }

        String msg = "Pase de lista guardado: " + recibidos + " residente(s) recibieron su ración de \""
                + evento.getNombre() + "\".";
        if (turno != null) {
            msg += " Les queda registrado el " + evento.getComida().toLowerCase()
                    + " del " + evento.getFechaEvento() + " en su asistencia.";
        }
        if (anuladas > 0) {
            msg += " " + anuladas + " marca(s) se anularon porque los desmarcaste.";
        }
        if (yaEnComedor > 0) {
            msg += " " + yaEnComedor + " ya habían comido en el comedor ese turno: no se les duplicó la ración.";
        }
        if (justificados > 0) {
            msg += " " + justificados + " no aparecen en esta lista porque están en ausencia justificada.";
        }
        flash.addFlashAttribute("ok", msg);
        return "redirect:/preceptor/pase-lista?evento=" + id;
    }

    private Turno turnoDeLaEntrega(EventoEspecial evento) {
        if (evento.getComida() == null || evento.getComida().isBlank()) return null;
        if (evento.getFechaEvento() == null) return null;
        return turnoRepo.findByFechaAndTipo(evento.getFechaEvento(), evento.getComida())
                .orElseGet(() -> {
                    Turno nuevo = new Turno();
                    nuevo.setFecha(evento.getFechaEvento());
                    nuevo.setTipo(evento.getComida());
                    nuevo.setEstado("CERRADO");
                    return turnoRepo.save(nuevo);
                });
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
            flash.addFlashAttribute("error", "La entrega ya no existe.");
            return "redirect:/preceptor/eventos";
        }

        if (evento.getFechaEvento() != null && evento.getFechaEvento().isBefore(LocalDate.now())) {
            flash.addFlashAttribute("error", "La entrega \"" + evento.getNombre() + "\" ya se realizó; ya no se puede excluir a nadie.");
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
                + ") no asistirá a la entrega \"" + evento.getNombre() + "\" del "
                + evento.getFechaEvento() + ". Motivo: " + motivo.trim());
        incidenciaRepo.save(i);
        flash.addFlashAttribute("ok", "Solicitud enviada al administrador: excluir a " + r.getNombreCompleto()
                + " de la entrega \"" + evento.getNombre() + "\".");
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
            flash.addFlashAttribute("error", "La entrega ya no existe.");
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
                               @RequestParam(defaultValue = "false") boolean sustDesayuno,
                               @RequestParam(defaultValue = "false") boolean sustAlmuerzo,
                               @RequestParam(defaultValue = "false") boolean sustCena,
                               Authentication auth,
                               RedirectAttributes flash) {
        List<String> comidas = new java.util.ArrayList<>();
        if (sustDesayuno) comidas.add("DESAYUNO");
        if (sustAlmuerzo) comidas.add("ALMUERZO");
        if (sustCena) comidas.add("CENA");

        if (comidas.isEmpty()) {
            flash.addFlashAttribute("error", "Marca al menos una comida: la entrega tiene que decir "
                    + "a qué desayuno, almuerzo o cena corresponde la ración.");
            return "redirect:/preceptor/eventos";
        }

        String grupo = java.util.UUID.randomUUID().toString();
        Usuario quien = usuarioActual(auth);

        for (String comida : comidas) {
            EventoEspecial e = new EventoEspecial();
            e.setNombre(nombre);
            e.setFechaEvento(fechaEvento);
            e.setComida(comida);
            e.setGrupoEvento(grupo);
            e.setSustituyeDesayuno("DESAYUNO".equals(comida));
            e.setSustituyeAlmuerzo("ALMUERZO".equals(comida));
            e.setSustituyeCena("CENA".equals(comida));
            e.setTurnos(comida);
            e.setExcluidos(excluidos);
            e.setEstado("PENDIENTE");
            e.setUsuario(quien);
            eventoRepo.save(e);
        }

        String detalle = comidas.size() == 1
                ? "para el " + comidas.get(0).toLowerCase()
                : "con un pase de lista por cada comida (" + comidas.size() + ")";
        String msg = "\"" + nombre + "\" enviado al administrador " + detalle + ".";

        java.util.List<String> avisos = new java.util.ArrayList<>();
        for (String comida : comidas) {
            java.util.List<String> fuera = new java.util.ArrayList<>();
            for (Residente r : residentesActivosDe(auth)) {
                if (reglasComidaService.yaJustificado(r, fechaEvento, comida)) {
                    fuera.add(r.getNombreCompleto());
                }
            }
            if (!fuera.isEmpty()) {
                avisos.add(comida.toLowerCase() + ": " + String.join(", ", fuera));
            }
        }
        if (!avisos.isEmpty()) {
            msg += " Aviso: hay residentes en ausencia justificada, así que no saldrán en el pase de lista"
                    + " de esa comida — " + String.join("; ", avisos) + ".";
        }
        flash.addFlashAttribute("ok", msg);
        return "redirect:/preceptor/eventos";
    }
}
