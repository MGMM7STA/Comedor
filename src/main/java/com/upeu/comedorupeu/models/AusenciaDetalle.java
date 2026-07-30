package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Entity

@Table(name = "ausencia_detalle", indexes = @Index(name = "idx_detalle_fecha_comida", columnList = "fecha, tipoComida"))
@Data
public class AusenciaDetalle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAusenciaDetalle;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_ausencia")
    private Ausencia ausencia;

    private LocalDate fecha;

    private String tipoComida;
}
