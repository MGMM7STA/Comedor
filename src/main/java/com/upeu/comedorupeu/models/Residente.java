package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity

@Table(name = "residente", indexes = @Index(name = "idx_residente_pabellon", columnList = "pabellon"))
@Data
public class Residente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idResidente;

    private String nombre;
    private String apellido;

    @Column(unique = true)
    private String dni;

    @Column(unique = true, nullable = false)
    private String codigoAcceso;

    private String fotoUrl;
    private String carrera;
    private String pabellon;
    private String cuarto;
    private String celular;

    private String estado = "ACTIVO";

    private Boolean eliminado = false;

    public boolean estaBorrado() {
        return Boolean.TRUE.equals(eliminado);
    }

    private Boolean deuda = false;

    private java.time.LocalDate fechaIngreso;

    private java.time.LocalDateTime fechaRegistro;

    @PrePersist
    public void sellarFechaDeRegistro() {
        if (fechaRegistro == null) fechaRegistro = java.time.LocalDateTime.now();
    }

    public String getSemestreTexto() {
        if (fechaIngreso == null) return "—";
        return fechaIngreso.getYear() + "-" + (fechaIngreso.getMonthValue() >= 7 ? "2" : "1");
    }

    private java.time.LocalDate fechaFinEstancia;

    @Column(unique = true)
    private String tokenAcceso;

    @ManyToOne
    @JoinColumn(name = "id_apoderado")
    private Apoderado apoderado;

    @ManyToOne
    @JoinColumn(name = "id_preceptor")
    private Usuario preceptor;

    public String getNombreCompleto() {
        return (nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido);
    }
}
