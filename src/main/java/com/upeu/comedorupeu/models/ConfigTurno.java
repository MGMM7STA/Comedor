package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalTime;

@Entity
@Table(name = "config_turno")
@Data
public class ConfigTurno {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idConfig;

    @Column(unique = true, nullable = false)
    private String tipo;

    private Boolean usarHorario = false;

    private LocalTime horaInicio;
    private LocalTime horaFin;

    public boolean enVentana() {
        if (!Boolean.TRUE.equals(usarHorario) || horaInicio == null) return false;
        LocalTime ahora = LocalTime.now();
        if (ahora.isBefore(horaInicio)) return false;
        return horaFin == null || !ahora.isAfter(horaFin);
    }
}
