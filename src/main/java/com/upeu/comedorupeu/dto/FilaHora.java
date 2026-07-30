package com.upeu.comedorupeu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FilaHora {
    private String etiqueta;
    private long cantidad;
    private int porcentaje;
}
