package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity

@Table(name = "marcacion", indexes = @Index(name = "idx_marcacion_fecha", columnList = "fechaHora"))
@Data
public class Marcacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idMarcacion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_residente")
    private Residente residente;

    @ManyToOne
    @JoinColumn(name = "id_punto")
    private PuntoAtencion punto;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_turno")
    private Turno turno;

    private LocalDateTime fechaHora = LocalDateTime.now();

    private String estado;

    @Column(length = 300)
    private String observacion;

    private Boolean anulada = false;

    @Column(length = 300)
    private String aclaracion;

    private Integer intentos = 1;
}
