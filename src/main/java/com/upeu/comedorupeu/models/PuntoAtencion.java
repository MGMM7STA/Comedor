package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalTime;

@Entity
@Table(name = "punto_atencion")
@Data
public class PuntoAtencion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idPunto;

    private String nombre;
    private String ubicacion;

    private String modo = "MANUAL";

    private LocalTime horaInicio;
    private LocalTime horaFin;

    @Column(length = 500)
    private String notas;

    private Boolean activo = false;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private Usuario cajero;

    private Boolean eliminado = false;

    private java.time.LocalDateTime ultimaAccionManual;

    private String turnoManual;

    public boolean isOperativo() {
        if (Boolean.TRUE.equals(eliminado)) return false;
        if ("HORARIO".equals(modo) && (horaInicio != null || horaFin != null)) {
            LocalTime ahora = LocalTime.now();
            if (horaInicio == null) {

                return !ahora.isAfter(horaFin) && Boolean.TRUE.equals(activo);
            }
            LocalTime fin = (horaFin != null) ? horaFin : LocalTime.MAX;
            return !ahora.isBefore(horaInicio) && !ahora.isAfter(fin);
        }
        return Boolean.TRUE.equals(activo);
    }

    public String getHorarioTexto() {
        if (horaInicio == null && horaFin == null) return "Sin horario";

        return (horaInicio == null ? "--:--" : horaInicio.toString()) + " - "
                + (horaFin == null ? "--:--" : horaFin.toString());
    }
}
