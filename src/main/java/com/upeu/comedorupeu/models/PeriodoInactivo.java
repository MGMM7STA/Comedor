package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "periodo_inactivo", indexes = @Index(name = "idx_periodo_residente", columnList = "id_residente"))
@Data
public class PeriodoInactivo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idPeriodo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_residente")
    private Residente residente;

    private LocalDateTime desde;

    private LocalDateTime hasta;

    private String motivo = "INACTIVO";

    public boolean cubre(LocalDateTime momento) {
        if (desde == null || momento == null) return false;
        if (momento.isBefore(desde)) return false;
        return hasta == null || momento.isBefore(hasta);
    }
}
