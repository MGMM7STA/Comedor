package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "incidencia")
@Data
public class Incidencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idIncidencia;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "id_punto")
    private PuntoAtencion punto;

    @Column(length = 500)
    private String descripcion;

    private String tipo = "GENERAL";

    private Long refEvento;
    private String refCodigo;

    private Boolean atendida = false;

    private LocalDateTime fechaHora = LocalDateTime.now();
}
