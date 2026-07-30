package com.upeu.comedorupeu.dto;

import com.upeu.comedorupeu.models.Marcacion;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FilaMovimiento {
    private Marcacion marcacion;
    private String desayuno;
    private String almuerzo;
    private String cena;
}
