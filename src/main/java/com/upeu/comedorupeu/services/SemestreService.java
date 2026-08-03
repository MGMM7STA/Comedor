package com.upeu.comedorupeu.services;

import com.upeu.comedorupeu.repository.ResidenteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class SemestreService {

    private final ResidenteRepository residenteRepo;

    public SemestreService(ResidenteRepository residenteRepo) {
        this.residenteRepo = residenteRepo;
    }

    public static String codigoDe(LocalDate fecha) {
        if (fecha == null) return null;
        return fecha.getYear() + "-" + (fecha.getMonthValue() >= 7 ? "2" : "1");
    }

    public String actual() {
        return codigoDe(LocalDate.now());
    }

    public LocalDate inicioDe(String codigo) {
        int[] p = partes(codigo);
        return p[1] == 1 ? LocalDate.of(p[0], 1, 1) : LocalDate.of(p[0], 7, 1);
    }

    public LocalDate finDe(String codigo) {
        int[] p = partes(codigo);
        return p[1] == 1 ? LocalDate.of(p[0], 6, 30) : LocalDate.of(p[0], 12, 31);
    }

    public String valido(String codigo) {
        if (codigo == null || codigo.isBlank()) return actual();
        try {
            partes(codigo);
            return codigo.trim();
        } catch (RuntimeException e) {
            return actual();
        }
    }

    public LocalDate recortarInicio(String codigo, LocalDate fecha) {
        LocalDate ini = inicioDe(codigo);
        LocalDate fin = finDe(codigo);
        if (fecha == null || fecha.isBefore(ini)) return ini;
        if (fecha.isAfter(fin)) return fin;
        return fecha;
    }

    public LocalDate recortarFin(String codigo, LocalDate fecha) {
        LocalDate ini = inicioDe(codigo);
        LocalDate fin = finDe(codigo);
        if (fecha == null || fecha.isAfter(fin)) return fin;
        if (fecha.isBefore(ini)) return ini;
        return fecha;
    }

    public String aplicar(org.springframework.ui.Model model, String semestreParam) {
        String s = valido(semestreParam);
        model.addAttribute("semestre", s);
        model.addAttribute("semIni", inicioDe(s));
        model.addAttribute("semFin", finDe(s));
        return s;
    }

    public LocalDate fechaPorDefecto(String codigo) {
        LocalDate hoy = LocalDate.now();
        return contiene(codigo, hoy) ? hoy : inicioDe(codigo);
    }

    public boolean contiene(String codigo, LocalDate fecha) {
        if (fecha == null) return false;
        return !fecha.isBefore(inicioDe(codigo)) && !fecha.isAfter(finDe(codigo));
    }

    public List<String> disponibles() {
        String hoy = actual();
        LocalDate primera = residenteRepo.primeraFechaIngreso();
        String desde = (primera != null) ? codigoDe(primera) : hoy;

        int[] d = partes(desde);
        int[] h = partes(hoy);
        if (d[0] > h[0] || (d[0] == h[0] && d[1] > h[1])) {
            d = h;
        }

        List<String> lista = new ArrayList<>();
        int anio = d[0], mitad = d[1];
        while (anio < h[0] || (anio == h[0] && mitad <= h[1])) {
            lista.add(anio + "-" + mitad);
            if (mitad == 1) {
                mitad = 2;
            } else {
                mitad = 1;
                anio++;
            }
        }
        java.util.Collections.reverse(lista);
        return lista;
    }

    private int[] partes(String codigo) {
        String[] t = codigo.trim().split("-");
        if (t.length != 2) throw new IllegalArgumentException("Semestre no válido: " + codigo);
        int anio = Integer.parseInt(t[0]);
        int mitad = Integer.parseInt(t[1]);
        if (mitad != 1 && mitad != 2) throw new IllegalArgumentException("Semestre no válido: " + codigo);
        return new int[]{anio, mitad};
    }
}
