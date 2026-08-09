package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "racion_especial")
@Data
public class RacionEspecial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idRacionEspecial;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_residente")
    private Residente residente;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    private java.time.LocalDateTime fechaHora = java.time.LocalDateTime.now();

    private String evidenciaUrl;

    @Column(length = 300)
    private String indicacion;

    @OneToMany(mappedBy = "racionEspecial", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RacionEspecialDetalle> detalles = new ArrayList<>();
}
