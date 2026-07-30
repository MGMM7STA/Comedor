package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "apoderado")
@Data
public class Apoderado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idApoderado;

    private String nombre;
    private String dni;
    private String telefono;

    private String relacion;
}
