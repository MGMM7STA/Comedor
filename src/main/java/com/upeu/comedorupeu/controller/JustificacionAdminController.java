package com.upeu.comedorupeu.controller;

import com.upeu.comedorupeu.dto.SemanaNav;
import com.upeu.comedorupeu.models.Ausencia;
import com.upeu.comedorupeu.repository.AusenciaRepository;
import com.upeu.comedorupeu.services.AlcanceService;
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
@RequestMapping("/admin/justificaciones")
public class JustificacionAdminController {

    private final AusenciaRepository ausenciaRepo;
    private final AlcanceService alcanceService;
    private final com.upeu.comedorupeu.services.JustificacionService justificacionService;

    private final com.upeu.comedorupeu.services.ExcelService excelService;

    public JustificacionAdminController(AusenciaRepository ausenciaRepo, AlcanceService alcanceService,
                                        com.upeu.comedorupeu.services.JustificacionService justificacionService,
                                        com.upeu.comedorupeu.services.ExcelService excelService) {
        this.ausenciaRepo = ausenciaRepo;
        this.alcanceService = alcanceService;
        this.justificacionService = justificacionService;
        this.excelService = excelService;
    }

    static String excepcionesDelDia(Ausencia a, LocalDate dia) {
        java.util.LinkedHashSet<String> justificados = new java.util.LinkedHashSet<>();
        for (var d : a.getDetalles()) {
            if (dia.equals(d.getFecha())) justificados.add(d.getTipoComida());
        }
        List<String> todos = List.of("DESAYUNO", "ALMUERZO", "CENA");
        if (justificados.containsAll(todos)) return "Completo (D + A + C)";
        if (justificados.isEmpty()) return "Sin turnos justificados";
        List<String> faltantes = todos.stream().filter(t -> !justificados.contains(t)).toList();
        java.util.function.Function<List<String>, String> nombres = lista -> String.join(" y ",
                lista.stream().map(t -> t.charAt(0) + t.substring(1).toLowerCase()).toList());
        return "Solo " + nombres.apply(justificados.stream().toList())
                + " — excluye " + nombres.apply(faltantes);
    }

    @GetMapping
    public String justificaciones(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(defaultValue = "20") int limite,
                                  @RequestParam(defaultValue = "RECIENTES") String orden,
                                  Model model, Authentication auth) {
        LocalDate hoy = LocalDate.now();

        LocalDate ini = (desde != null) ? desde : hoy;
        LocalDate fin = (hasta != null && !hasta.isBefore(ini)) ? hasta : ini;

        model.addAttribute("nav", SemanaNav.de(ini));

        AlcanceDatos alcance = alcanceService.de(auth);
        List<Ausencia> lista = alcance.justificaciones(ini, fin);

        String busca = (q == null) ? "" : q.trim().toLowerCase();
        if (!busca.isEmpty()) {
            lista = lista.stream()
                    .filter(a -> (a.getResidente().getCodigoAcceso() != null
                            && a.getResidente().getCodigoAcceso().toLowerCase().contains(busca))
                            || a.getResidente().getNombreCompleto().toLowerCase().contains(busca))
                    .toList();
        }

        java.util.Comparator<Ausencia> porFecha =
                java.util.Comparator.comparing(Ausencia::getFechaInicio)
                        .thenComparing(Ausencia::getIdAusencia);
        lista = new java.util.ArrayList<>(lista);
        lista.sort("RECIENTES".equals(orden) ? porFecha.reversed() : porFecha);

        int total = lista.size();
        if (limite > 0 && lista.size() > limite) lista = lista.subList(0, limite);

        java.util.Map<Long, String> turnosPorAusencia = new java.util.HashMap<>();
        for (Ausencia a : lista) {
            turnosPorAusencia.put(a.getIdAusencia(), textoTurnos(a));
        }
        model.addAttribute("turnosPorAusencia", turnosPorAusencia);

        java.util.Map<Long, String> dia1PorAusencia = new java.util.HashMap<>();
        java.util.Map<Long, String> diaFinPorAusencia = new java.util.HashMap<>();
        java.util.Map<Long, String> estadoPorAusencia = new java.util.HashMap<>();
        for (Ausencia a : lista) {
            dia1PorAusencia.put(a.getIdAusencia(), excepcionesDelDia(a, a.getFechaInicio()));
            diaFinPorAusencia.put(a.getIdAusencia(), excepcionesDelDia(a, a.getFechaFin()));
            estadoPorAusencia.put(a.getIdAusencia(), justificacionService.estadoDe(a));
        }
        model.addAttribute("dia1PorAusencia", dia1PorAusencia);
        model.addAttribute("diaFinPorAusencia", diaFinPorAusencia);
        model.addAttribute("estadoPorAusencia", estadoPorAusencia);

        model.addAttribute("justificaciones", lista);
        model.addAttribute("totalJustificaciones", total);
        model.addAttribute("desde", ini);
        model.addAttribute("hasta", fin);
        model.addAttribute("q", q);
        model.addAttribute("limite", limite);
        model.addAttribute("orden", orden);
        model.addAttribute("hoy", hoy);
        return "admin/justificaciones";
    }

    private String textoTurnos(Ausencia a) {
        java.util.LinkedHashSet<String> tipos = new java.util.LinkedHashSet<>();
        for (var d : a.getDetalles()) tipos.add(d.getTipoComida());
        if (tipos.containsAll(List.of("DESAYUNO", "ALMUERZO", "CENA"))) {
            return "Todos los turnos (D + A + C)";
        }
        if (tipos.isEmpty()) return "—";
        return "Solo " + String.join(" y ", tipos.stream()
                .map(t -> t.charAt(0) + t.substring(1).toLowerCase()).toList());
    }

    @GetMapping("/exportar")
    public org.springframework.http.ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int limite,
            @RequestParam(defaultValue = "RECIENTES") String orden,
            Authentication auth) throws java.io.IOException {
        LocalDate hoy = LocalDate.now();
        LocalDate ini = (desde != null) ? desde : hoy;
        LocalDate fin = (hasta != null && !hasta.isBefore(ini)) ? hasta : ini;

        AlcanceDatos alcance = alcanceService.de(auth);
        List<Ausencia> lista = alcance.justificaciones(ini, fin);
        String busca = (q == null) ? "" : q.trim().toLowerCase();
        if (!busca.isEmpty()) {
            lista = lista.stream()
                    .filter(a -> (a.getResidente().getCodigoAcceso() != null
                            && a.getResidente().getCodigoAcceso().toLowerCase().contains(busca))
                            || a.getResidente().getNombreCompleto().toLowerCase().contains(busca))
                    .toList();
        }
        java.util.Comparator<Ausencia> porFecha =
                java.util.Comparator.comparing(Ausencia::getFechaInicio)
                        .thenComparing(Ausencia::getIdAusencia);
        lista = new java.util.ArrayList<>(lista);
        lista.sort("RECIENTES".equals(orden) ? porFecha.reversed() : porFecha);
        if (limite > 0 && lista.size() > limite) lista = lista.subList(0, limite);

        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<String[]> datos = new java.util.ArrayList<>();
        int n = 1;
        for (Ausencia a : lista) {
            datos.add(new String[]{
                    String.valueOf(n++),
                    f.format(a.getFechaInicio()),
                    f.format(a.getFechaFin()),
                    a.getResidente().getApellido() + ", " + a.getResidente().getNombre(),
                    a.getResidente().getCodigoAcceso(),
                    a.getResidente().getPabellon() == null ? "—" : a.getResidente().getPabellon(),
                    textoTurnos(a),
                    a.getMotivo() == null ? "" : a.getMotivo(),
                    a.getUsuario() == null ? "—" : a.getUsuario().getNombreCompleto(),
                    justificacionService.estadoDe(a)
            });
        }
        String filtros = "Período: " + f.format(ini) + (fin.equals(ini) ? "" : " al " + f.format(fin))
                + (busca.isEmpty() ? "" : "   Búsqueda: " + q);
        byte[] xlsx = excelService.exportarTabla("COMEDOR UPEU — Justificaciones de Inasistencia", filtros,
                new String[]{"N°", "Desde", "Hasta", "Residente", "Código", "Residencia de Género",
                        "Turnos justificados", "Motivo", "Registró", "Estado"},
                datos);
        String nombre = "justificaciones_" + ini + (fin.equals(ini) ? "" : "_a_" + fin) + ".xlsx";
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes flash) {
        ausenciaRepo.findById(id).ifPresent(a -> {
            String estado = justificacionService.estadoDe(a);
            if (!"FUTURA".equals(estado)) {
                flash.addFlashAttribute("error", "Esta justificación ya "
                        + ("PASADA".equals(estado) ? "transcurrió: su historial se conserva y no puede eliminarse."
                        : "está en curso: usa \"Cancelar\" para hacer un cierre anticipado."));
                return;
            }
            String nombre = a.getResidente().getNombreCompleto();
            ausenciaRepo.delete(a);
            flash.addFlashAttribute("ok", "Justificación de " + nombre + " eliminada.");
        });
        return "redirect:/admin/justificaciones";
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable Long id, RedirectAttributes flash) {
        ausenciaRepo.findById(id).ifPresent(a -> {
            String resultado = justificacionService.cierreAnticipado(a);
            if (resultado == null) {
                flash.addFlashAttribute("error", "Esta justificación no está en curso: no aplica el cierre anticipado.");
            } else {
                flash.addFlashAttribute("ok", resultado);
            }
        });
        return "redirect:/admin/justificaciones";
    }
}
