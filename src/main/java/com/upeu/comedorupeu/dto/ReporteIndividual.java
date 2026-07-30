package com.upeu.comedorupeu.dto;

import com.upeu.comedorupeu.models.Residente;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReporteIndividual {
    private Residente residente;
    private long asistencias;
    private long totalComidas;
    private long justificadas;
    private long injustificadas;

    private java.time.LocalDate desde;
    private java.time.LocalDate hasta;
    private List<FilaDia> filas = new ArrayList<>();

    private List<FilaSemana> semanas = new ArrayList<>();

    private List<com.upeu.comedorupeu.models.Marcacion> infracciones = new ArrayList<>();
}
