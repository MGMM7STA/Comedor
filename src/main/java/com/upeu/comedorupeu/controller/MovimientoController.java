package com.upeu.comedorupeu.controller;

import com.upeu.comedorupeu.dto.FilaDia;
import com.upeu.comedorupeu.dto.SemanaNav;
import com.upeu.comedorupeu.dto.ReporteIndividual;
import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.services.AlcanceService;
import com.upeu.comedorupeu.services.ReporteService;
import com.upeu.comedorupeu.services.alcance.AlcanceDatos;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/movimientos")
public class MovimientoController {

    private final ReporteService reporteService;
    private final AlcanceService alcanceService;

    private final com.upeu.comedorupeu.services.ExcelService excelService;

    public MovimientoController(ReporteService reporteService, AlcanceService alcanceService,
                                com.upeu.comedorupeu.services.ExcelService excelService) {
        this.reporteService = reporteService;
        this.alcanceService = alcanceService;
        this.excelService = excelService;
    }

    @GetMapping
    public String movimientos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                              @RequestParam(required = false) String codigo,
                              @RequestParam(defaultValue = "20") int limite,
                              @RequestParam(defaultValue = "0") int pag,
                              @RequestParam(defaultValue = "RECIENTES") String orden,
                              Model model, Authentication auth) {
        LocalDate hoy = LocalDate.now();

        LocalDate ini = (desde != null) ? desde : hoy;
        LocalDate fin = (hasta != null && !hasta.isBefore(ini)) ? hasta : ini;
        if (fin.isAfter(hoy)) fin = hoy;

        model.addAttribute("nav", SemanaNav.de(ini));

        List<Map<String, Object>> filas = filasConsolidadas(ini, fin, codigo, orden, auth);

        int total = filas.size();
        if (limite > 0) {
            int iniIdx = Math.max(0, pag * limite);
            filas = iniIdx >= filas.size() ? new ArrayList<>()
                    : new ArrayList<>(filas.subList(iniIdx, Math.min(filas.size(), iniIdx + limite)));
        }

        model.addAttribute("filas", filas);
        model.addAttribute("totalFilas", total);
        model.addAttribute("pag", pag);
        model.addAttribute("hayAnterior", pag > 0);
        model.addAttribute("hayMas", limite > 0 && (pag + 1) * limite < total);
        model.addAttribute("orden", orden);
        model.addAttribute("desde", ini);
        model.addAttribute("hasta", fin);
        model.addAttribute("hoy", hoy);
        model.addAttribute("codigo", codigo);
        model.addAttribute("limite", limite);
        model.addAttribute("residenciaFija", alcanceService.de(auth).residenciaGenero());
        return "reportes/movimientos";
    }

    private List<Map<String, Object>> filasConsolidadas(LocalDate ini, LocalDate fin, String codigo,
                                                        String orden, Authentication auth) {

        AlcanceDatos alcance = alcanceService.de(auth);
        List<Residente> residentes = alcance.residentesActivos();

        String busqueda = (codigo == null) ? "" : codigo.trim().toLowerCase();
        if (!busqueda.isEmpty()) {
            residentes = residentes.stream()
                    .filter(r -> (r.getCodigoAcceso() != null && r.getCodigoAcceso().toLowerCase().contains(busqueda))
                            || r.getNombreCompleto().toLowerCase().contains(busqueda))
                    .toList();
        }

        List<Map<String, Object>> filas = new ArrayList<>();
        for (Residente r : residentes) {
            ReporteIndividual rep = reporteService.individual(r, ini, fin);
            for (FilaDia f : rep.getFilas()) {
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("fecha", f.getFecha());
                fila.put("dia", f.getDia());
                fila.put("codigo", r.getCodigoAcceso());
                fila.put("nombre", r.getApellido() + ", " + r.getNombre());
                fila.put("residencia", r.getPabellon());
                fila.put("desayuno", f.getDesayuno());
                fila.put("almuerzo", f.getAlmuerzo());
                fila.put("cena", f.getCena());
                fila.put("observacion", f.getObservacion());
                filas.add(fila);
            }
        }

        java.util.Comparator<Map<String, Object>> porFecha =
                java.util.Comparator.comparing(f -> (LocalDate) f.get("fecha"));
        filas.sort("RECIENTES".equals(orden) ? porFecha.reversed() : porFecha);
        return filas;
    }

    private String textoEstado(Object estado) {
        return switch (estado == null ? "" : estado.toString()) {
            case "SI" -> "Consumió";
            case "JUST" -> "Justificado";
            case "NO" -> "No asistió";
            default -> "—";
        };
    }

    @org.springframework.web.bind.annotation.GetMapping("/exportar")
    public org.springframework.http.ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String codigo,
            @RequestParam(defaultValue = "RECIENTES") String orden,
            Authentication auth) throws java.io.IOException {
        LocalDate hoy = LocalDate.now();

        LocalDate ini = (desde != null) ? desde : hoy;
        LocalDate fin = (hasta != null && !hasta.isBefore(ini)) ? hasta : ini;
        if (fin.isAfter(hoy)) fin = hoy;

        List<Map<String, Object>> filas = filasConsolidadas(ini, fin, codigo, orden, auth);

        List<String[]> datos = new ArrayList<>();
        int n = 1;
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (Map<String, Object> fila : filas) {
            datos.add(new String[]{
                    String.valueOf(n++),
                    f.format((LocalDate) fila.get("fecha")),
                    String.valueOf(fila.get("dia")),
                    String.valueOf(fila.get("nombre")),
                    String.valueOf(fila.get("codigo")),
                    textoEstado(fila.get("desayuno")),
                    textoEstado(fila.get("almuerzo")),
                    textoEstado(fila.get("cena")),
                    fila.get("observacion") == null ? "" : String.valueOf(fila.get("observacion"))
            });
        }
        String filtros = "Período: " + f.format(ini) + (fin.equals(ini) ? "" : " al " + f.format(fin))
                + (codigo == null || codigo.isBlank() ? "" : "   Búsqueda: " + codigo);
        byte[] xlsx = excelService.exportarTabla("COMEDOR UPEU — Detalle de Movimientos", filtros,
                new String[]{"N°", "Fecha", "Día", "Residente", "Código", "Desayuno", "Almuerzo", "Cena", "Observaciones"},
                datos);
        String nombre = "movimientos_" + ini + (fin.equals(ini) ? "" : "_a_" + fin) + ".xlsx";
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }
}
