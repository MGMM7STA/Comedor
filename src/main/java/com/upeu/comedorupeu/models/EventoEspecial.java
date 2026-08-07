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

    private Boolean sustituyeDesayuno = false;
    private Boolean sustituyeAlmuerzo = false;
    private Boolean sustituyeCena = false;

    private String comida;

    private String grupoEvento;

    private String canceladoPor;

    @Column(length = 300)
    private String motivoCancelacion;

    private LocalDateTime fechaCancelacion;

    public String getComidaTexto() {
        if (comida == null || comida.isBlank()) return "Ración adicional";
        return comida.charAt(0) + comida.substring(1).toLowerCase();
    }

    public String getNombreConComida() {
        if (comida == null || comida.isBlank()) return nombre;
        return nombre + " (" + getComidaTexto() + ")";
    }

    public boolean sustituye(String tipoComida) {
        if (tipoComida == null) return false;
        return switch (tipoComida) {
            case "DESAYUNO" -> Boolean.TRUE.equals(sustituyeDesayuno);
            case "ALMUERZO" -> Boolean.TRUE.equals(sustituyeAlmuerzo);
            case "CENA" -> Boolean.TRUE.equals(sustituyeCena);
            default -> false;
        };
    }

    public String getComidasSustituidasTexto() {
        java.util.List<String> l = new java.util.ArrayList<>();
        if (Boolean.TRUE.equals(sustituyeDesayuno)) l.add("Desayuno");
        if (Boolean.TRUE.equals(sustituyeAlmuerzo)) l.add("Almuerzo");
        if (Boolean.TRUE.equals(sustituyeCena)) l.add("Cena");
        return l.isEmpty() ? "Ninguna (ración adicional)" : String.join(" + ", l);
    }

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
