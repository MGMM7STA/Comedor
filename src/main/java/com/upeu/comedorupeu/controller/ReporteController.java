package com.upeu.comedorupeu.controller;

import com.upeu.comedorupeu.dto.ReporteGeneral;
import com.upeu.comedorupeu.dto.SemanaNav;
import com.upeu.comedorupeu.dto.ReporteIndividual;
import com.upeu.comedorupeu.models.Ausencia;
import com.upeu.comedorupeu.models.EventoEntrega;
import com.upeu.comedorupeu.models.EventoEspecial;
import com.upeu.comedorupeu.models.Marcacion;
import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.models.SolicitudExtemporanea;
import com.upeu.comedorupeu.models.Usuario;
import com.upeu.comedorupeu.repository.*;
import com.upeu.comedorupeu.services.AlcanceService;
import com.upeu.comedorupeu.services.ExcelService;
import com.upeu.comedorupeu.services.ReporteService;
import com.upeu.comedorupeu.services.alcance.AlcanceDatos;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/reportes")
public class ReporteController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReporteService reporteService;
    private final ResidenteRepository residenteRepo;
    private final PuntoAtencionRepository puntoRepo;
    private final ExcelService excelService;
    private final UsuarioRepository usuarioRepo;
    private final SolicitudExtemporaneaRepository solicitudRepo;
    private final EventoEspecialRepository eventoRepo;
    private final EventoEntregaRepository entregaRepo;
    private final AusenciaRepository ausenciaRepo;

    private AlcanceService alcanceService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setAlcanceService(AlcanceService alcanceService) {
        this.alcanceService = alcanceService;
    }

    public ReporteController(ReporteService reporteService, ResidenteRepository residenteRepo,
                             PuntoAtencionRepository puntoRepo, ExcelService excelService,
                             UsuarioRepository usuarioRepo, SolicitudExtemporaneaRepository solicitudRepo,
                             EventoEspecialRepository eventoRepo, EventoEntregaRepository entregaRepo,
                             AusenciaRepository ausenciaRepo) {
        this.reporteService = reporteService;
        this.residenteRepo = residenteRepo;
        this.puntoRepo = puntoRepo;
        this.excelService = excelService;
        this.usuarioRepo = usuarioRepo;
        this.solicitudRepo = solicitudRepo;
        this.eventoRepo = eventoRepo;
        this.entregaRepo = entregaRepo;
        this.ausenciaRepo = ausenciaRepo;
    }

    private String pabellonEfectivo(Authentication auth, String pabellonParam) {
        Usuario u = usuarioRepo.findByCorreo(auth.getName());
        if (u != null && "PRECEPTOR".equals(u.getRol()) && u.getPabellon() != null) {
            return u.getPabellon();
        }
        return (pabellonParam == null || pabellonParam.isBlank() || "TODOS".equalsIgnoreCase(pabellonParam))
                ? null : pabellonParam;
    }

    private boolean esPreceptorConPabellon(Authentication auth) {
        Usuario u = usuarioRepo.findByCorreo(auth.getName());
        return u != null && "PRECEPTOR".equals(u.getRol()) && u.getPabellon() != null;
    }

    private <T> List<T> pagina(List<T> lista, int pagina, int limite) {
        if (limite <= 0) return lista;
        int desde = Math.max(0, pagina * limite);
        if (desde >= lista.size()) return List.of();
        return lista.subList(desde, Math.min(lista.size(), desde + limite));
    }

    @GetMapping
    public String general(@RequestParam(defaultValue = "TODOS") String turno,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
                          @RequestParam(required = false) Long punto,
                          @RequestParam(defaultValue = "20") int limite,
                          @RequestParam(defaultValue = "0") int pag,
                          @RequestParam(required = false) String pabellon,
                          @RequestParam(defaultValue = "RECIENTES") String orden,

                          @RequestParam(defaultValue = "NORMAL") String seccion,

                          @RequestParam(required = false) String codigo,
                          @RequestParam(required = false) String verHoras,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hpFecha,
                          @RequestParam(defaultValue = "TODOS") String hpTurno,
                          Model model, Authentication auth) {

        LocalDate desde = (fecha != null) ? fecha : LocalDate.now();
        LocalDate hasta = (fechaHasta != null && !fechaHasta.isBefore(desde)) ? fechaHasta : desde;

        model.addAttribute("nav", SemanaNav.de(desde));
        String pab = pabellonEfectivo(auth, pabellon);

        boolean vNormal = "NORMAL".equals(seccion);
        boolean vExtras = "RESERVAS".equals(seccion);
        boolean vJustif = "JUSTIFICACIONES".equals(seccion);
        boolean vEventos = "EVENTOS".equals(seccion);

        ReporteGeneral rep = reporteService.general(desde, hasta, turno, punto, pab,
                "RECIENTES".equals(orden), codigo);

        rep.setMovimientos(rep.getMovimientos().stream()
                .filter(m -> !"DENEGADO".equals(m.getEstado()))
                .toList());

        int total = rep.getMovimientos().size();
        List<Marcacion> visibles = pagina(rep.getMovimientos(), pag, limite);
        rep.setFilas(reporteService.construirFilas(visibles));

        List<SolicitudExtemporanea> reservas = reservasDelRango(desde, hasta, pab);
        List<Ausencia> justificaciones = justificacionesDelRango(desde, hasta, pab);
        List<Map<String, Object>> eventos = eventosDelRango(desde, hasta, pab);

        int maxSeccion = Math.max(Math.max(total, reservas.size()),
                Math.max(justificaciones.size(), eventos.size()));

        model.addAttribute("rep", rep);
        model.addAttribute("turno", turno);
        model.addAttribute("fecha", desde);

        model.addAttribute("fechaHasta", hasta);
        model.addAttribute("punto", punto);
        model.addAttribute("limite", limite);
        model.addAttribute("pag", pag);
        model.addAttribute("hayAnterior", pag > 0);
        model.addAttribute("hayMas", limite > 0 && (pag + 1) * limite < maxSeccion);
        model.addAttribute("totalMostrado", visibles.size());
        model.addAttribute("pabellon", pab == null ? "TODOS" : pab);
        model.addAttribute("pabellonFijo", esPreceptorConPabellon(auth));
        model.addAttribute("seccion", seccion);
        model.addAttribute("verNormal", vNormal);
        model.addAttribute("verExtras", vExtras);
        model.addAttribute("verJustificaciones", vJustif);
        model.addAttribute("verEventos", vEventos);
        model.addAttribute("orden", orden);
        model.addAttribute("codigo", codigo);
        model.addAttribute("puntos", puntoRepo.findAllByOrderByNombreAsc());
        model.addAttribute("reservasDia", pagina(reservas, pag, limite));
        model.addAttribute("totalReservas", reservas.size());
        model.addAttribute("justificacionesDia", pagina(justificaciones, pag, limite));
        model.addAttribute("totalJustificaciones", justificaciones.size());
        model.addAttribute("eventosDia", eventos);

        model.addAttribute("verHoras", verHoras != null);
        LocalDate hpDia = (hpFecha != null) ? hpFecha : desde;
        model.addAttribute("hpFecha", hpDia);
        model.addAttribute("hpTurno", hpTurno);
        if (verHoras != null) {
            var franjas = reporteService.general(hpDia, hpDia, hpTurno, null, pab, false, null).getHorasPico();
            model.addAttribute("hpDatos", franjas);

            model.addAttribute("hpPico", franjas.stream()
                    .max(java.util.Comparator.comparingLong(com.upeu.comedorupeu.dto.FilaHora::getCantidad))
                    .orElse(null));
        }
        return "reportes/general";
    }

    @GetMapping("/completo")
    public String completo(@RequestParam(defaultValue = "TODOS") String turno,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
                           @RequestParam(required = false) Long punto,
                           @RequestParam(defaultValue = "20") int limite,
                           @RequestParam(defaultValue = "0") int pag,
                           @RequestParam(required = false) String pabellon,
                           @RequestParam(defaultValue = "RECIENTES") String orden,

                           @RequestParam(defaultValue = "PERMITIDO") String estadoMov,
                           @RequestParam(required = false) String codigo,
                           @RequestParam(required = false) Long registradoPor,

                           @RequestParam(defaultValue = "NORMAL") String seccion,

                           @RequestParam(defaultValue = "TODOS") String resEstado,
                           @RequestParam(defaultValue = "TODOS") String resEntregadoA,
                           @RequestParam(defaultValue = "TODOS") String resTurno,
                           @RequestParam(required = false) Long resReservadoPor,

                           @RequestParam(required = false) Long jusPreceptor,

                           @RequestParam(defaultValue = "TODOS") String evPase,
                           Model model, Authentication auth) {
        LocalDate desde = (fecha != null) ? fecha : LocalDate.now();
        LocalDate hasta = (fechaHasta != null && !fechaHasta.isBefore(desde)) ? fechaHasta : desde;

        model.addAttribute("nav", SemanaNav.de(desde));
        String pab = pabellonEfectivo(auth, pabellon);

        ReporteGeneral rep = reporteService.general(desde, hasta, turno, punto, pab,
                "RECIENTES".equals(orden), codigo);

        rep.setMovimientos(rep.getMovimientosTodos());

        if (!"TODOS".equals(estadoMov)) {
            rep.setMovimientos(rep.getMovimientos().stream()
                    .filter(m -> estadoMov.equals(m.getEstado()))
                    .toList());
        }

        if (registradoPor != null) {
            rep.setMovimientos(rep.getMovimientos().stream()
                    .filter(m -> m.getUsuario() != null && registradoPor.equals(m.getUsuario().getIdUsuario()))
                    .toList());
        }

        int total = rep.getMovimientos().size();
        List<Marcacion> visibles = pagina(rep.getMovimientos(), pag, limite);
        rep.setFilas(reporteService.construirFilas(visibles));

        model.addAttribute("rep", rep);
        model.addAttribute("turno", turno);
        model.addAttribute("fecha", desde);

        model.addAttribute("fechaHasta", hasta);
        model.addAttribute("punto", punto);
        model.addAttribute("limite", limite);
        model.addAttribute("pag", pag);
        model.addAttribute("hayAnterior", pag > 0);
        model.addAttribute("hayMas", limite > 0 && (pag + 1) * limite < total);
        model.addAttribute("totalMostrado", visibles.size());
        model.addAttribute("totalFiltrado", total);
        model.addAttribute("pabellon", pab == null ? "TODOS" : pab);
        model.addAttribute("pabellonFijo", esPreceptorConPabellon(auth));
        model.addAttribute("orden", orden);
        model.addAttribute("estadoMov", estadoMov);
        model.addAttribute("codigo", codigo);
        model.addAttribute("registradoPor", registradoPor);
        model.addAttribute("puntos", puntoRepo.findAllByOrderByNombreAsc());

        model.addAttribute("personal", usuarioRepo.findByRolIn(List.of("CAJERO", "PRECEPTOR")));

        model.addAttribute("seccion", seccion);

        var reservasComp = "RESERVAS".equals(seccion)
                ? reservasDelRango(desde, hasta, pab) : List.<SolicitudExtemporanea>of();
        if (!"TODOS".equals(resEstado)) {
            reservasComp = reservasComp.stream().filter(s -> resEstado.equals(s.getEstado())).toList();
        }
        if (!"TODOS".equals(resEntregadoA)) {
            reservasComp = reservasComp.stream().filter(s -> resEntregadoA.equals(s.getEntregadoA())).toList();
        }
        if (!"TODOS".equals(resTurno)) {
            reservasComp = reservasComp.stream().filter(s -> resTurno.equals(s.getTipoComida())).toList();
        }

        if (resReservadoPor != null) {
            reservasComp = reservasComp.stream()
                    .filter(s -> s.getUsuario() != null && resReservadoPor.equals(s.getUsuario().getIdUsuario()))
                    .toList();
        }
        model.addAttribute("reservasCompleto", reservasComp);

        var justifComp = "JUSTIFICACIONES".equals(seccion)
                ? justificacionesDelRango(desde, hasta, pab) : List.<Ausencia>of();
        if (jusPreceptor != null) {
            justifComp = justifComp.stream()
                    .filter(a -> a.getUsuario() != null && jusPreceptor.equals(a.getUsuario().getIdUsuario()))
                    .toList();
        }
        model.addAttribute("justificacionesCompleto", justifComp);

        var eventosComp = "EVENTOS".equals(seccion)
                ? eventosDelRango(desde, hasta, pab) : List.<java.util.Map<String, Object>>of();
        if ("CON".equals(evPase)) {
            eventosComp = eventosComp.stream().filter(e -> Boolean.TRUE.equals(e.get("conPase"))).toList();
        }
        if ("SIN".equals(evPase)) {
            eventosComp = eventosComp.stream().filter(e -> !Boolean.TRUE.equals(e.get("conPase"))).toList();
        }
        model.addAttribute("eventosCompleto", eventosComp);

        model.addAttribute("resEstado", resEstado);
        model.addAttribute("resEntregadoA", resEntregadoA);
        model.addAttribute("resTurno", resTurno);
        model.addAttribute("resReservadoPor", resReservadoPor);
        model.addAttribute("jusPreceptor", jusPreceptor);
        model.addAttribute("evPase", evPase);
        return "reportes/completo";
    }

    private List<SolicitudExtemporanea> reservasDelRango(LocalDate desde, LocalDate hasta, String residencia) {
        return alcanceDe(residencia).reservas(desde, hasta);
    }

    private List<Ausencia> justificacionesDelRango(LocalDate desde, LocalDate hasta, String residencia) {
        return alcanceDe(residencia).justificaciones(desde, hasta);
    }

    private AlcanceDatos alcanceDe(String residencia) {
        return alcanceService.porResidencia(residencia);
    }

    private List<Map<String, Object>> eventosDelRango(LocalDate desde, LocalDate hasta, String pabellon) {
        List<Map<String, Object>> eventosDia = new ArrayList<>();
        for (EventoEspecial e : eventoRepo.findByEstadoAndFechaEventoBetweenOrderByFechaEventoAsc("APROBADO", desde, hasta)) {
            List<Residente> participantes = residenteRepo.findByEstadoOrderByApellidoAsc("ACTIVO").stream()
                    .filter(r -> !e.getExcluidosLista().contains(r.getCodigoAcceso()))
                    .filter(r -> pabellon == null || pabellon.equals(r.getPabellon()))
                    .toList();
            Map<Long, Boolean> marcados = new HashMap<>();
            for (EventoEntrega en : entregaRepo.findByEventoIdEvento(e.getIdEvento())) {
                marcados.put(en.getResidente().getIdResidente(), Boolean.TRUE.equals(en.getRecibido()));
            }
            List<Map<String, Object>> filasEntrega = new ArrayList<>();
            long recibidas = 0;
            for (Residente r : participantes) {
                Map<String, Object> fe = new HashMap<>();
                fe.put("nombre", r.getApellido() + ", " + r.getNombre());
                fe.put("codigo", r.getCodigoAcceso());
                Boolean recibido = marcados.get(r.getIdResidente());
                fe.put("recibido", recibido);
                if (Boolean.TRUE.equals(recibido)) recibidas++;
                filasEntrega.add(fe);
            }
            Map<String, Object> fila = new HashMap<>();
            fila.put("evento", e);
            fila.put("participantes", participantes.size());
            fila.put("entregadas", recibidas);
            fila.put("filas", filasEntrega);

            fila.put("conPase", participantes.stream().anyMatch(r -> marcados.containsKey(r.getIdResidente())));
            eventosDia.add(fila);
        }
        return eventosDia;
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(@RequestParam(defaultValue = "TODOS") String turno,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
                                           @RequestParam(required = false) Long punto,
                                           @RequestParam(defaultValue = "10") int limite,
                                           @RequestParam(defaultValue = "0") int pag,
                                           @RequestParam(required = false) String pabellon,
                                           @RequestParam(defaultValue = "RECIENTES") String orden,
                                           @RequestParam(defaultValue = "NORMAL") String seccion,
                                           Authentication auth) throws IOException {
        LocalDate desde = (fecha != null) ? fecha : LocalDate.now();
        LocalDate hasta = (fechaHasta != null && !fechaHasta.isBefore(desde)) ? fechaHasta : desde;
        String pab = pabellonEfectivo(auth, pabellon);

        boolean vNormal = "NORMAL".equals(seccion);
        boolean vExtras = "RESERVAS".equals(seccion);
        boolean vJustif = "JUSTIFICACIONES".equals(seccion);
        boolean vEventos = "EVENTOS".equals(seccion);

        ReporteGeneral rep = reporteService.general(desde, hasta, turno, punto, pab,
                "RECIENTES".equals(orden), null);
        rep.setFilas(reporteService.construirFilas(pagina(rep.getMovimientos(), pag, limite)));
        String puntoNombre = punto == null ? "Todos"
                : puntoRepo.findById(punto).map(p -> p.getNombre()).orElse("Todos");

        byte[] xlsx = excelService.exportarGeneral(
                vNormal ? rep : null,
                desde, hasta, turno, puntoNombre, pab == null ? "Todas" : pab,
                vExtras ? pagina(reservasDelRango(desde, hasta, pab), pag, limite) : null,
                vJustif ? pagina(justificacionesDelRango(desde, hasta, pab), pag, limite) : null,
                vEventos ? eventosDelRango(desde, hasta, pab) : null,
                false);

        String nombre = "reporte_" + turno.toLowerCase() + "_" + desde + (hasta.equals(desde) ? "" : "_a_" + hasta) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(XLSX)
                .body(xlsx);
    }

    @GetMapping("/exportar-masivo")
    public ResponseEntity<byte[]> exportarMasivo(Authentication auth) throws IOException {
        String pab = pabellonEfectivo(auth, null);

        LocalDate hasta = LocalDate.now();
        LocalDate desde = hasta.minusYears(5);

        ReporteGeneral rep = reporteService.general(desde, hasta, "TODOS", null, pab, true, null);

        rep.setFilas(reporteService.construirFilas(rep.getMovimientosTodos()));

        byte[] xlsx = excelService.exportarGeneral(rep, desde, hasta, "TODOS", "Todos",
                pab == null ? "Todas" : pab,
                reservasDelRango(desde, hasta, pab),
                justificacionesDelRango(desde, hasta, pab),
                eventosDelRango(desde, hasta, pab),
                true);

        String nombre = "base_completa_comedor_" + hasta + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(XLSX)
                .body(xlsx);
    }

    @GetMapping("/individual")
    public String individual(@RequestParam(required = false) String q,
                             @RequestParam(required = false) String codigo,

                             @RequestParam(defaultValue = "dias") String tipo,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,

                             @RequestParam(defaultValue = "20") int limite,

                             @RequestParam(defaultValue = "0") int pag,
                             @RequestParam(required = false) String conFiltros,
                             @RequestParam(required = false) String verNormal,
                             @RequestParam(required = false) String verExtras,
                             @RequestParam(required = false) String verJustificaciones,
                             @RequestParam(required = false) String verEventos,
                             Model model, Authentication auth) {
        String pab = pabellonEfectivo(auth, null);
        Residente residente = buscarResidente(q, codigo, pab);

        model.addAttribute("verNormal", conFiltros == null || verNormal != null);
        model.addAttribute("verExtras", conFiltros != null && verExtras != null);
        model.addAttribute("verJustificaciones", conFiltros != null && verJustificaciones != null);
        model.addAttribute("verEventos", conFiltros != null && verEventos != null);

        LocalDate hoy = LocalDate.now();
        boolean modoSemanas = "semanas".equals(tipo);
        if (modoSemanas) {

            LocalDate ref = (desde != null) ? desde : hoy;
            LocalDate domingo = ref.minusDays(ref.getDayOfWeek().getValue() % 7);
            LocalDate domingoActual = hoy.minusDays(hoy.getDayOfWeek().getValue() % 7);
            if (domingo.isAfter(domingoActual)) domingo = domingoActual;
            LocalDate sabado = domingo.plusDays(6);
            desde = domingo;

            hasta = sabado.isAfter(hoy) ? hoy : sabado;
            model.addAttribute("semanaAnterior", domingo.minusDays(7));
            model.addAttribute("semanaSiguiente", domingo.plusDays(7));
            model.addAttribute("haySemanaSiguiente", domingo.isBefore(domingoActual));
            model.addAttribute("semanaDomingo", domingo);
            model.addAttribute("semanaSabado", sabado);
        } else {

            if (desde == null) desde = hoy;
            if (hasta == null) hasta = hoy;
        }

        model.addAttribute("q", residente != null ? residente.getNombreCompleto() : q);
        model.addAttribute("codigo", residente != null ? residente.getCodigoAcceso() : codigo);
        model.addAttribute("tipo", tipo);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("hoy", hoy);
        model.addAttribute("limite", limite);
        model.addAttribute("noEncontrado", residente == null && ((q != null && !q.isBlank()) || (codigo != null && !codigo.isBlank())));
        model.addAttribute("pabellonFijo", esPreceptorConPabellon(auth));

        model.addAttribute("pag", pag);
        if (residente != null) {
            asegurarToken(residente);
            ReporteIndividual rep = reporteService.individual(residente, desde, hasta);

            int totalFilas = rep.getFilas().size();
            model.addAttribute("totalFilas", totalFilas);
            var filasPag = rep.getFilas();
            if (limite > 0) {
                int inicio = Math.min(Math.max(0, pag * limite), totalFilas);
                filasPag = new ArrayList<>(filasPag.subList(inicio, Math.min(totalFilas, inicio + limite)));
            }
            rep.setFilas(filasPag);
            model.addAttribute("rep", rep);
            model.addAttribute("hayAnterior", pag > 0);
            model.addAttribute("hayMas", limite > 0 && (pag + 1) * limite < totalFilas);
            model.addAttribute("desde", rep.getDesde());
            model.addAttribute("hasta", rep.getHasta());

            String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            model.addAttribute("urlPadres", base + "/padres/" + residente.getTokenAcceso()
                    + "?desde=" + rep.getDesde() + "&hasta=" + rep.getHasta());
            cargarExtrasResidente(model, residente, rep.getDesde(), rep.getHasta());

            Map<String, List<SolicitudExtemporanea>> reservasPorDia = new HashMap<>();
            for (SolicitudExtemporanea s : solicitudRepo.findByResidenteIdResidenteAndFechaBetweenOrderByFechaAsc(
                    residente.getIdResidente(), rep.getDesde(), rep.getHasta())) {
                reservasPorDia.computeIfAbsent(s.getFecha().toString(), k -> new ArrayList<>()).add(s);
            }
            model.addAttribute("reservasPorDia", reservasPorDia);
        }
        return "reportes/individual";
    }

    private void cargarExtrasResidente(Model model, Residente residente, LocalDate desde, LocalDate hasta) {
        model.addAttribute("reservasResidente",
                solicitudRepo.findByResidenteIdResidenteAndFechaBetweenOrderByFechaAsc(
                        residente.getIdResidente(), desde, hasta));
        model.addAttribute("justificacionesResidente",
                ausenciaRepo.findByResidenteIdResidenteOrderByFechaInicioDesc(residente.getIdResidente()).stream()
                        .filter(a -> !a.getFechaFin().isBefore(desde) && !a.getFechaInicio().isAfter(hasta))
                        .toList());
        List<EventoEntrega> entregas = entregaRepo.findByResidenteIdResidente(residente.getIdResidente()).stream()
                .filter(en -> {
                    LocalDate f = en.getEvento().getFechaEvento();
                    return f != null && !f.isBefore(desde) && !f.isAfter(hasta);
                })
                .toList();
        model.addAttribute("entregasResidente", entregas);
    }

    @GetMapping("/individual/exportar")
    public ResponseEntity<byte[]> exportarIndividual(@RequestParam String codigo,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                                     Authentication auth) throws IOException {
        String pab = pabellonEfectivo(auth, null);
        Residente residente = residenteRepo.findByCodigoAcceso(codigo.trim()).orElse(null);

        if (residente == null || (pab != null && !pab.equals(residente.getPabellon()))) {
            return ResponseEntity.status(403).build();
        }
        ReporteIndividual rep = reporteService.individual(residente, desde, hasta);
        List<SolicitudExtemporanea> reservas = solicitudRepo
                .findByResidenteIdResidenteAndFechaBetweenOrderByFechaAsc(residente.getIdResidente(), rep.getDesde(), rep.getHasta());
        byte[] xlsx = excelService.exportarIndividual(rep, reservas);
        String nombre = "reporte_" + codigo + "_" + rep.getDesde() + "_" + rep.getHasta() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(XLSX)
                .body(xlsx);
    }

    private Residente buscarResidente(String q, String codigo, String pabellon) {
        Residente residente = null;
        if (codigo != null && !codigo.isBlank()) {
            residente = residenteRepo.findByCodigoAcceso(codigo.trim()).orElse(null);
        }
        if (residente == null && q != null && !q.isBlank()) {
            List<Residente> encontrados = residenteRepo.buscar(q.trim(), pabellon);
            if (!encontrados.isEmpty()) residente = encontrados.get(0);
        }

        if (residente != null && pabellon != null && !pabellon.equals(residente.getPabellon())) {
            return null;
        }
        return residente;
    }

    private void asegurarToken(Residente residente) {
        if (residente.getTokenAcceso() == null || residente.getTokenAcceso().isBlank()) {
            residente.setTokenAcceso(UUID.randomUUID().toString());
            residenteRepo.save(residente);
        }
    }
}
