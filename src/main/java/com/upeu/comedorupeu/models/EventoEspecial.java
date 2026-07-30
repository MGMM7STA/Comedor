package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "evento_especial")
@Data
public class EventoEspecial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idEvento;

    private String nombre;

    private LocalDate fechaEvento;

    private String turnos;

    @Column(length = 1000)
    private String excluidos;

    private String estado = "PENDIENTE";

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "id_revisor")
    private Usuario revisor;

    private LocalDateTime fechaEnvio = LocalDateTime.now();

    public List<String> getTurnosLista() {
        if (turnos == null || turnos.isBlank()) return List.of();
        return Arrays.stream(turnos.split(",")).map(String::trim).toList();
    }

    public List<String> getExcluidosLista() {
        if (excluidos == null || excluidos.isBlank()) return List.of();
        return Arrays.stream(excluidos.split(",")).map(String::trim).toList();
    }
}
