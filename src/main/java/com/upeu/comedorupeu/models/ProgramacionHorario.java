package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "programacion_horario")
@Data
public class ProgramacionHorario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idProgramacion;

    private String objetivo = "PUNTO";

    @ManyToOne
    @JoinColumn(name = "id_punto")
    private PuntoAtencion punto;

    private String tipoTurno;

    @ManyToOne
    @JoinColumn(name = "id_cajero")
    private Usuario cajero;

    private Integer diaSemana;

    private LocalTime horaInicio;
    private LocalTime horaFin;

    private Boolean activo = true;

    public boolean cubre(DayOfWeek dia, LocalTime ahora) {
        if (!Boolean.TRUE.equals(activo) || diaSemana == null || horaInicio == null || horaFin == null) return false;
        if (diaSemana != dia.getValue()) return false;
        return !ahora.isBefore(horaInicio) && !ahora.isAfter(horaFin);
    }

    public String getRangoTexto() {
        if (horaInicio == null || horaFin == null) return "";
        return horaInicio + " - " + horaFin;
    }
}
