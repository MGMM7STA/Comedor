package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Table(name = "turno", uniqueConstraints = @UniqueConstraint(columnNames = {"fecha", "tipo"}))
@Data
public class Turno {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idTurno;

    private String tipo;

    private LocalDate fecha;

    private String estado = "DESACTIVADO";

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    private java.time.LocalDateTime ultimaAccionManual;
}
