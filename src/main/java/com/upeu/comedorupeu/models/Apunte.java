package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "apunte")
@Data
public class Apunte {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idApunte;

    @Column(length = 400)
    private String texto;

    private String tipo = "AVISO";

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    private java.time.LocalDate fecha;

    private LocalTime horaInicio;
    private LocalTime horaFin;

    private LocalDateTime fechaHora = LocalDateTime.now();

    public boolean estaVigente() {
        if (fecha != null && !fecha.equals(java.time.LocalDate.now())) return false;
        LocalTime ahora = LocalTime.now();
        if (horaInicio != null && ahora.isBefore(horaInicio)) return false;
        if (horaFin != null && ahora.isAfter(horaFin)) return false;
        return true;
    }

    public String getFranjaTexto() {
        if (horaInicio == null && horaFin == null) return "Todo el día";
        return (horaInicio == null ? "--:--" : horaInicio.toString())
                + " - " + (horaFin == null ? "--:--" : horaFin.toString());
    }
}
