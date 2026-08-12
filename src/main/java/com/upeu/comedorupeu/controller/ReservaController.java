package com.upeu.comedorupeu.controller;

import com.upeu.comedorupeu.dto.SemanaNav;
import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.models.SolicitudExtemporanea;
import com.upeu.comedorupeu.models.Usuario;
import com.upeu.comedorupeu.repository.ResidenteRepository;
import com.upeu.comedorupeu.repository.SolicitudExtemporaneaRepository;
import com.upeu.comedorupeu.repository.UsuarioRepository;
import com.upeu.comedorupeu.services.AlcanceService;
import com.upeu.comedorupeu.services.TurnoService;
import com.upeu.comedorupeu.services.alcance.AlcanceDatos;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
public class ReservaController {

    private final SolicitudExtemporaneaRepository solicitudRepo;
    private final ResidenteRepository residenteRepo;
    private final UsuarioRepository usuarioRepo;
    private final TurnoService turnoService;
    private final AlcanceService alcanceService;

    private final com.upeu.comedorupeu.services.ExcelService excelService;

    private com.upeu.comedorupeu.services.SemestreService semestreService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSemestreService(com.upeu.comedorupeu.services.SemestreService semestreService) {
        this.semestreService = semestreService;
    }

    private com.upeu.comedorupeu.services.JustificacionService justificacionService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setJustificacionService(com.upeu.comedorupeu.services.JustificacionService justificacionService) {
        this.justificacionService = justificacionService;
    }

    private com.upeu.comedorupeu.services.ReglasComidaService reglasComidaService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setReglasComidaService(com.upeu.comedorupeu.services.ReglasComidaService reglasComidaService) {
        this.reglasComidaService = reglasComidaService;
    }

    public ReservaController(SolicitudExtemporaneaRepository solicitudRepo, ResidenteRepository residenteRepo,
                             UsuarioRepository usuarioRepo, TurnoService turnoService, AlcanceService alcanceService,
                             com.upeu.comedorupeu.services.ExcelService excelService) {
        this.solicitudRepo = solicitudRepo;
        this.residenteRepo = residenteRepo;
        this.usuarioRepo = usuarioRepo;
        this.turnoService = turnoService;
        this.alcanceService = alcanceService;
        this.excelService = excelService;
    }

    @GetMapping("/preceptor/reservas")
    public String reservasPreceptor(@RequestParam(required = false) Long editar,
                                    Model model, Authentication auth, RedirectAttributes flash) {
        AlcanceDatos alcance = alcanceService.de(auth);
        model.addAttribute("residentes", alcance.residentesActivos().stream()
                .filter(r -> !r.estaBorrado())
                .toList());
        model.addAttribute("hoy", LocalDate.now());

        if (editar != null) {
            SolicitudExtemporanea s = solicitudRepo.findById(editar).orElse(null);
            if (s == null || !alcance.alcanza(s.getResidente())) {
                flash.addFlashAttribute("error", "Esa reserva no existe o no pertenece a tu residencia.");
                return "redirect:/admin/reservas";
            }
            if (!"PENDIENTE".equals(s.getEstado()) || s.estaVencida()) {
                flash.addFlashAttribute("error", "Esa reserva ya no se puede editar: "
                        + "o fue entregada, o fue cancelada, o su turno ya pasó.");
                return "redirect:/admin/reservas";
            }
            model.addAttribute("editando", s);
        }

        model.addAttribute("comidasBloqueadas", turnoService.comidasBloqueadasHoy());
        model.addAttribute("residenciaFija", alcance.residenciaGenero());
        model.addAttribute("misReservas", ultimasReservas(alcance));
        return "preceptor/reservas";
    }

    private String actualizarReserva(Long idEditar, LocalDate fecha, String tipoComida, String motivo,
                                     java.time.LocalTime horaRecojo, String conTaper,
                                     jakarta.servlet.http.HttpServletRequest request,
                                     Authentication auth, RedirectAttributes flash) {
        AlcanceDatos alcance = alcanceService.de(auth);
        SolicitudExtemporanea s = solicitudRepo.findById(idEditar).orElse(null);
        if (s == null || !alcance.alcanza(s.getResidente())) {
            flash.addFlashAttribute("error", "Esa reserva no existe o no pertenece a tu residencia.");
            return "redirect:/admin/reservas";
        }
        if (!"PENDIENTE".equals(s.getEstado()) || s.estaVencida()) {
            flash.addFlashAttribute("error", "Esa reserva ya no se puede editar: "
                    + "o fue entregada, o fue cancelada, o su turno ya pasó.");
            return "redirect:/admin/reservas";
        }

        Residente r = s.getResidente();
        boolean cambiaDeSitio = !fecha.equals(s.getFecha()) || !tipoComida.equals(s.getTipoComida());
        if (cambiaDeSitio) {
            if (fecha.isBefore(LocalDate.now())) {
                flash.addFlashAttribute("error", "No puedes mover la reserva a un día que ya pasó.");
                return "redirect:/preceptor/reservas?editar=" + idEditar;
            }
            if (fecha.equals(LocalDate.now()) && turnoService.comidasBloqueadasHoy().contains(tipoComida)) {
                flash.addFlashAttribute("error", "El turno de " + tipoComida.toLowerCase()
                        + " de hoy ya no admite reservas.");
                return "redirect:/preceptor/reservas?editar=" + idEditar;
            }
            if (reglasComidaService.yaIngreso(r, fecha, tipoComida)) {
                flash.addFlashAttribute("error", r.getNombreCompleto() + " ya ingresó a ese turno: "
                        + "la ración ya se entregó y no se puede mover la reserva ahí.");
                return "redirect:/preceptor/reservas?editar=" + idEditar;
            }
            if (reglasComidaService.yaTieneReserva(r, fecha, tipoComida)) {
                flash.addFlashAttribute("error", r.getNombreCompleto()
                        + " ya tiene otra reserva en ese día y turno.");
                return "redirect:/preceptor/reservas?editar=" + idEditar;
            }
            if (reglasComidaService.yaJustificado(r, fecha, tipoComida)) {
                flash.addFlashAttribute("error", r.getNombreCompleto()
                        + " está en ausencia justificada en ese turno: no tiene sentido reservarle comida.");
                return "redirect:/preceptor/reservas?editar=" + idEditar;
            }
        }

        String codigo = r.getCodigoAcceso();
        boolean traeTaper = false;
        if (conTaper != null && !conTaper.isBlank()) {
            for (String c : conTaper.split(",")) {
                if (c.trim().equals(codigo)) traeTaper = true;
            }
        }
        String motivoPropio = request.getParameter("motivoInd_" + codigo);
        String motivoFinal = (motivoPropio != null && !motivoPropio.isBlank())
                ? motivoPropio.trim()
                : ((motivo == null || motivo.isBlank()) ? "Reserva registrada por preceptoría" : motivo.trim());

        s.setFecha(fecha);
        s.setTipoComida(tipoComida);
        s.setHoraRecojo(horaRecojo);
        s.setTraeTaper(traeTaper);
        s.setMotivo(motivoFinal);
        solicitudRepo.save(s);

        flash.addFlashAttribute("ok", "Reserva de " + r.getNombreCompleto() + " actualizada: "
                + tipoComida.toLowerCase() + " del " + fecha
                + (traeTaper ? ", con táper." : ", sin táper."));
        return "redirect:/admin/reservas";
    }

    @PostMapping("/preceptor/reservas/guardar")
    public String guardarLote(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                              @RequestParam String tipoComida,
                              @RequestParam(required = false) String motivo,
                              @RequestParam(required = false) java.time.LocalTime horaRecojo,
                              @RequestParam(required = false) String codigos,
                              @RequestParam(required = false) String conTaper,
                              @RequestParam(required = false) String fechasExtra,
                              @RequestParam(required = false) Long idEditar,
                              jakarta.servlet.http.HttpServletRequest request,
                              Authentication auth, RedirectAttributes flash) {

        if (idEditar != null) {
            return actualizarReserva(idEditar, fecha, tipoComida, motivo, horaRecojo,
                    conTaper, request, auth, flash);
        }

        if (codigos == null || codigos.isBlank()) {
            flash.addFlashAttribute("error", "Agrega al menos un residente a la lista antes de guardar.");
            return "redirect:/preceptor/reservas";
        }

        java.util.List<LocalDate> pedidas = new java.util.ArrayList<>();
        pedidas.add(fecha);
        if (fechasExtra != null && !fechasExtra.isBlank()) {
            for (String texto : fechasExtra.split(",")) {
                String iso = texto.trim();
                if (iso.isEmpty()) continue;
                LocalDate d;
                try {
                    d = LocalDate.parse(iso);
                } catch (RuntimeException e) {
                    continue;
                }
                if (!pedidas.contains(d)) pedidas.add(d);
            }
        }
        pedidas.sort(java.util.Comparator.naturalOrder());

        LocalDate hoy = LocalDate.now();
        java.util.List<LocalDate> fechas = new java.util.ArrayList<>();
        java.util.List<String> diasDescartados = new java.util.ArrayList<>();
        for (LocalDate d : pedidas) {
            if (d.isBefore(hoy)) {
                diasDescartados.add(d + " (ya pasó)");
            } else if (d.equals(hoy) && turnoService.comidasBloqueadasHoy().contains(tipoComida)) {
                diasDescartados.add(d + " (ese turno de hoy ya cerró)");
            } else {
                fechas.add(d);
            }
        }
        if (fechas.isEmpty()) {
            flash.addFlashAttribute("error", "Ninguno de los días elegidos admite reservas de "
                    + tipoComida.toLowerCase() + ": " + String.join(", ", diasDescartados) + ".");
            return "redirect:/preceptor/reservas";
        }

        Usuario quien = usuarioRepo.findByCorreo(auth.getName());
        AlcanceDatos alcance = alcanceService.de(auth);

        String grupo = null;
        java.util.Set<String> traenTaper = new java.util.HashSet<>();
        if (conTaper != null && !conTaper.isBlank()) {
            for (String c : conTaper.split(",")) traenTaper.add(c.trim());
        }

        int creadas = 0, repetidas = 0, justificados = 0, sinIngresar = 0, yaComieron = 0;
        for (LocalDate dia : fechas) {
            for (String cod : codigos.split(",")) {
                String codigo = cod.trim();
                if (codigo.isEmpty()) continue;
                Residente r = residenteRepo.findByCodigoAcceso(codigo).orElse(null);

                if (r == null || !alcance.alcanza(r)) continue;

                if (r.estaBorrado()
                        || !com.upeu.comedorupeu.services.alcance.AlcanceDatos.vigenteEn(r, dia)) {
                    sinIngresar++;
                    continue;
                }

                if (reglasComidaService.yaIngreso(r, dia, tipoComida)) {
                    yaComieron++;
                    continue;
                }

                if (reglasComidaService.yaTieneReserva(r, dia, tipoComida)) { repetidas++; continue; }

                if (reglasComidaService.yaJustificado(r, dia, tipoComida)) {
                    justificados++;
                    continue;
                }

                SolicitudExtemporanea s = new SolicitudExtemporanea();
                s.setResidente(r);
                s.setUsuario(quien);
                s.setFecha(dia);
                s.setTipoComida(tipoComida);
                s.setHoraRecojo(horaRecojo);

                String motivoPropio = request.getParameter("motivoInd_" + codigo);
                if (motivoPropio != null && !motivoPropio.isBlank()) {
                    s.setMotivo(motivoPropio.trim());
                } else {
                    s.setMotivo((motivo == null || motivo.isBlank())
                            ? "Reserva registrada por preceptoría" : motivo.trim());
                }
                s.setEstado("PENDIENTE");
                s.setGrupoLote(grupo);
                s.setTraeTaper(traenTaper.contains(codigo));
                solicitudRepo.save(s);
                creadas++;
            }
        }
        String dondeVa = (fechas.size() == 1)
                ? "para el " + fechas.get(0)
                : "repartidas en " + fechas.size() + " días (" + fechas.stream()
                        .map(LocalDate::toString).collect(java.util.stream.Collectors.joining(", ")) + ")";
        String msg = creadas + " ración(es) reservada(s) " + dondeVa + " (" + tipoComida.toLowerCase() + ")";
        if (grupo != null) {
            msg += " — RESERVA MASIVA, grupo " + grupo + ". Al escanear a cualquiera del grupo,"
                    + " el cajero podrá entregar todas sus raciones de un solo clic.";
        } else {
            msg += " como reservas individuales (el cajero las entrega una por una).";
        }
        if (repetidas > 0) msg += " " + repetidas + " ya tenían reserva en ese turno y se omitieron.";
        if (yaComieron > 0) {
            msg += " " + yaComieron + " se omitieron porque YA INGRESARON a ese turno: "
                    + "la ración ya se les entregó y no se puede reservar encima.";
        }
        if (justificados > 0) {
            msg += " " + justificados + " se omitieron porque están EN AUSENCIA JUSTIFICADA "
                    + "en ese turno: no tiene sentido reservarles comida.";
        }
        if (sinIngresar > 0) {
            msg += " " + sinIngresar + " se omitieron porque en esa fecha todavía no empieza su estancia.";
        }
        if (!diasDescartados.isEmpty()) {
            msg += " No se reservó en " + String.join(", ", diasDescartados) + ".";
        }
        flash.addFlashAttribute("ok", msg);
        return "redirect:/preceptor/reservas";
    }

    @GetMapping("/admin/reservas")
    public String reservasAdmin(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                @RequestParam(required = false) String q,
                                @RequestParam(defaultValue = "TODOS") String estado,
                                @RequestParam(defaultValue = "20") int limite,
                                @RequestParam(defaultValue = "RECIENTES") String orden,
                                @RequestParam(name = "semestre", required = false) String semestreParam,
                                Model model, Authentication auth) {
        LocalDate hoy = LocalDate.now();

        String semestre = semestreService.aplicar(model, semestreParam);
        LocalDate base = (desde != null) ? desde : semestreService.fechaPorDefecto(semestre);
        LocalDate ini = semestreService.recortarInicio(semestre, base);
        LocalDate fin = semestreService.recortarFin(semestre,
                (hasta != null && !hasta.isBefore(ini)) ? hasta : ini);

        model.addAttribute("nav", SemanaNav.de(ini));

        AlcanceDatos alcance = alcanceService.de(auth);
        List<SolicitudExtemporanea> lista = alcance.reservas(ini, fin);

        if (!"TODOS".equals(estado)) {
            lista = lista.stream().filter(s -> estado.equals(s.getEstado())).toList();
        }
        String busca = (q == null) ? "" : q.trim().toLowerCase();
        if (!busca.isEmpty()) {
            lista = lista.stream()
                    .filter(s -> (s.getResidente().getCodigoAcceso() != null
                            && s.getResidente().getCodigoAcceso().toLowerCase().contains(busca))
                            || s.getResidente().getNombreCompleto().toLowerCase().contains(busca))
                    .toList();
        }

        java.util.Comparator<SolicitudExtemporanea> porFecha =
                java.util.Comparator.comparing(SolicitudExtemporanea::getFecha)
                        .thenComparing(SolicitudExtemporanea::getIdSolicitud);
        lista = new java.util.ArrayList<>(lista);
        lista.sort("RECIENTES".equals(orden) ? porFecha.reversed() : porFecha);

        int total = lista.size();
        long pendientes = lista.stream().filter(s -> "PENDIENTE".equals(s.getEstado())).count();
        if (limite > 0 && lista.size() > limite) lista = lista.subList(0, limite);

        java.util.Map<Long, Boolean> puedeCancelar = new java.util.HashMap<>();
        for (SolicitudExtemporanea s : lista) {
            boolean pendiente = "PENDIENTE".equals(s.getEstado());
            boolean noPaso = !turnoService.turnoYaOcurrio(s.getTipoComida(), s.getFecha());
            puedeCancelar.put(s.getIdSolicitud(), pendiente && noPaso);
        }
        model.addAttribute("puedeCancelar", puedeCancelar);

        model.addAttribute("reservas", lista);
        model.addAttribute("totalReservas", total);
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("desde", ini);
        model.addAttribute("hasta", fin);
        model.addAttribute("q", q);
        model.addAttribute("estado", estado);
        model.addAttribute("limite", limite);
        model.addAttribute("orden", orden);
        model.addAttribute("hoy", hoy);
        return "admin/reservas";
    }

    @GetMapping("/admin/reservas/exportar")
    public org.springframework.http.ResponseEntity<byte[]> exportarAdmin(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "TODOS") String estado,
            @RequestParam(defaultValue = "20") int limite,
            @RequestParam(defaultValue = "RECIENTES") String orden,
            Authentication auth) throws java.io.IOException {
        LocalDate hoy = LocalDate.now();
        LocalDate ini = (desde != null) ? desde : hoy;
        LocalDate fin = (hasta != null && !hasta.isBefore(ini)) ? hasta : ini;

        AlcanceDatos alcance = alcanceService.de(auth);
        List<SolicitudExtemporanea> lista = alcance.reservas(ini, fin);
        if (!"TODOS".equals(estado)) {
            lista = lista.stream().filter(s -> estado.equals(s.getEstado())).toList();
        }
        String busca = (q == null) ? "" : q.trim().toLowerCase();
        if (!busca.isEmpty()) {
            lista = lista.stream()
                    .filter(s -> (s.getResidente().getCodigoAcceso() != null
                            && s.getResidente().getCodigoAcceso().toLowerCase().contains(busca))
                            || s.getResidente().getNombreCompleto().toLowerCase().contains(busca))
                    .toList();
        }
        java.util.Comparator<SolicitudExtemporanea> porFecha =
                java.util.Comparator.comparing(SolicitudExtemporanea::getFecha)
                        .thenComparing(SolicitudExtemporanea::getIdSolicitud);
        lista = new java.util.ArrayList<>(lista);
        lista.sort("RECIENTES".equals(orden) ? porFecha.reversed() : porFecha);
        if (limite > 0 && lista.size() > limite) lista = lista.subList(0, limite);

        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<String[]> datos = new java.util.ArrayList<>();
        int n = 1;
        for (SolicitudExtemporanea s : lista) {
            datos.add(new String[]{
                    String.valueOf(n++),
                    f.format(s.getFecha()),
                    s.getTipoComida(),
                    s.getResidente().getApellido() + ", " + s.getResidente().getNombre(),
                    s.getResidente().getCodigoAcceso(),
                    s.getResidente().getPabellon() == null ? "—" : s.getResidente().getPabellon(),
                    s.getHoraRecojo() == null ? "—" : s.getHoraRecojo().toString(),
                    s.getMotivo() == null ? "" : s.getMotivo(),
                    s.getUsuario() == null ? "—" : s.getUsuario().getNombreCompleto(),
                    s.getEntregadoATexto(),
                    "PENDIENTE".equals(s.getEstado()) ? "Pendiente" : "Entregada"
            });
        }
        String filtros = "Período: " + f.format(ini) + (fin.equals(ini) ? "" : " al " + f.format(fin))
                + "   Estado: " + ("TODOS".equals(estado) ? "Todas" : estado)
                + (busca.isEmpty() ? "" : "   Búsqueda: " + q);
        byte[] xlsx = excelService.exportarTabla("COMEDOR UPEU — Reservas de Ración (Extras)", filtros,
                new String[]{"N°", "Fecha", "Turno", "Residente", "Código", "Residencia de Género",
                        "Hora recojo", "Motivo", "Reservó", "Entregado a", "Estado"},
                datos);
        String nombre = "reservas_" + ini + (fin.equals(ini) ? "" : "_a_" + fin) + ".xlsx";
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    @PostMapping("/admin/reservas/{id}/cancelar")
    public String cancelarAdmin(@PathVariable Long id,
                                @RequestParam(required = false) String motivoAccion,
                                Authentication auth, RedirectAttributes flash) {
        boolean esPreceptor = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_PRECEPTOR".equals(a.getAuthority()));
        if (!esPreceptor) {
            flash.addFlashAttribute("error", "Las reservas solo las puede cancelar el preceptor que "
                    + "atiende a ese residente. El administrador puede consultarlas, no cancelarlas.");
            return "redirect:/admin/reservas";
        }

        SolicitudExtemporanea s = solicitudRepo.findById(id).orElse(null);
        if (s == null) return "redirect:/admin/reservas";

        if (!alcanceService.de(auth).alcanza(s.getResidente())) {
            flash.addFlashAttribute("error", "Ese residente no pertenece a tu residencia de género.");
        } else if (!"PENDIENTE".equals(s.getEstado())) {
            flash.addFlashAttribute("error", "Esa reserva ya fue atendida por el cajero; no se puede cancelar.");
        } else if (turnoService.turnoYaOcurrio(s.getTipoComida(), s.getFecha())) {
            flash.addFlashAttribute("error", "El turno de esa reserva ya pasó: queda como historial y no se puede cancelar.");
        } else {
            Usuario quien = usuarioRepo.findByCorreo(auth.getName());
            s.setEstado("CANCELADA");
            s.setCanceladaPor(quien.getNombreCompleto() + " (" + quien.getRol() + ")");
            s.setMotivoCancelacion(motivoAccion == null || motivoAccion.isBlank() ? null : motivoAccion.trim());
            s.setFechaCancelacion(java.time.LocalDateTime.now());
            solicitudRepo.save(s);
            flash.addFlashAttribute("ok", "Reserva de " + s.getTipoComida().toLowerCase()
                    + " del " + s.getFecha() + " cancelada por " + s.getCanceladaPor() + ".");
        }
        return "redirect:/admin/reservas";
    }

    private List<SolicitudExtemporanea> ultimasReservas(AlcanceDatos alcance) {
        LocalDate hoy = LocalDate.now();
        return alcance.reservas(hoy.minusDays(3), hoy.plusDays(14));
    }
}
