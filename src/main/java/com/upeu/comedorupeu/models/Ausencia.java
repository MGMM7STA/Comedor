package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ausencia")
@Data
public class Ausencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAusencia;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_residente")
    private Residente residente;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    @Column(length = 300)
    private String motivo;

    @OneToMany(mappedBy = "ausencia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AusenciaDetalle> detalles = new ArrayList<>();
}
