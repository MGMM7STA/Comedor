package com.upeu.comedorupeu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FilaSemana {
    private String etiqueta;
    private String rango;
    private long asistencias;
    private long justificadas;
    private long injustificadas;
    private long totalComidas;
}
