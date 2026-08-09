package com.upeu.comedorupeu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JustificacionInfo {
    private String motivo;
    private String periodo;
    private String autorizadoPor;

    private String fuente;

    private String evidenciaUrl;
}
