package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Entity

@Table(name = "racion_especial_detalle",
       indexes = @Index(name = "idx_racion_esp_fecha_comida", columnList = "fecha, tipoComida"))
@Data
public class RacionEspecialDetalle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idRacionEspecialDetalle;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_racion_especial")
    private RacionEspecial racionEspecial;

    private LocalDate fecha;

    private String tipoComida;
}
