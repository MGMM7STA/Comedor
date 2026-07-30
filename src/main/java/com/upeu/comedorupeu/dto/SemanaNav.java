package com.upeu.comedorupeu.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SemanaNav {

    private final LocalDate fecha;
    private final List<LocalDate> dias;
    private final LocalDate hoy = LocalDate.now();

    private SemanaNav(LocalDate fecha) {
        this.fecha = fecha;

        LocalDate domingo = fecha.minusDays(fecha.getDayOfWeek().getValue() % 7);
        this.dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) dias.add(domingo.plusDays(i));
    }

    public static SemanaNav de(LocalDate fecha) {
        return new SemanaNav(fecha != null ? fecha : LocalDate.now());
    }

    public LocalDate getFecha() { return fecha; }
    public List<LocalDate> getDias() { return dias; }
    public LocalDate getHoy() { return hoy; }

    public LocalDate getAnterior() { return fecha.minusDays(7); }

    public LocalDate getSiguiente() {
        LocalDate s = fecha.plusDays(7);
        return s.isAfter(hoy) ? hoy : s;
    }

    public boolean isHaySiguiente() { return dias.get(6).isBefore(hoy); }

    public String getEtiqueta() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM");
        return "Semana del " + dias.get(0).format(f) + " al " + dias.get(6).format(f);
    }
}
