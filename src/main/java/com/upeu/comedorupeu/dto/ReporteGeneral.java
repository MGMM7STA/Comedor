package com.upeu.comedorupeu.dto;

import com.upeu.comedorupeu.models.Marcacion;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReporteGeneral {
    private long permitidos;
    private long denegados;
    private long ausentes;
    private long justificados;
    private long inasistencias;

    private List<Marcacion> movimientos = new ArrayList<>();

    private List<Marcacion> movimientosTodos = new ArrayList<>();

    private List<FilaMovimiento> filas = new ArrayList<>();

    private List<FilaHora> horasPico = new ArrayList<>();

    public long getTotalMovimientos() {
        return movimientos.size();
    }
}
