package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "usuario")
@Data
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idUsuario;

    private String nombre;
    private String apellido;
    private String dni;
    private String telefono;
    private String codigoUsuario;

    @Column(unique = true, nullable = false)
    private String correo;

    private String clave;

    private String rol;

    private String pabellon;

    private Boolean activo = true;

    public String getNombreCompleto() {
        return (nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido);
    }
}
