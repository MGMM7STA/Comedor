package com.upeu.comedorupeu.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class FilaDia {
    private LocalDate fecha;
    private String dia;
    private String desayuno = "";
    private String almuerzo = "";
    private String cena = "";
    private String observacion = "";
    private boolean injustificada;

    private String horaDesayuno;
    private String horaAlmuerzo;
    private String horaCena;
    private String motivoDesayuno;
    private String motivoAlmuerzo;
    private String motivoCena;
}
