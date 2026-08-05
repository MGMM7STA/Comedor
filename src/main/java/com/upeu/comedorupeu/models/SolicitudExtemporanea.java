package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity

@Table(name = "solicitud_extemporanea", indexes = {
        @Index(name = "idx_solicitud_fecha_estado", columnList = "fecha, estado"),
        @Index(name = "idx_solicitud_grupo", columnList = "grupoLote")
})
@Data
public class SolicitudExtemporanea {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idSolicitud;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_residente")
    private Residente residente;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    private LocalDate fecha;

    private String tipoComida;

    private java.time.LocalTime horaRecojo;

    @Column(length = 300)
    private String motivo;

    private String estado = "PENDIENTE";

    private String entregadoA;

    private LocalDateTime fechaHoraEntrega;

    private String grupoLote;

    private Boolean traeTaper = false;

    private String canceladaPor;

    public boolean estaVencida() {
        return "PENDIENTE".equals(estado) && fecha != null && fecha.isBefore(java.time.LocalDate.now());
    }

    public String getEstadoTexto() {
        if ("CANCELADA".equals(estado)) return "Cancelada";
        if (estaVencida()) return "No recogida";
        return "PENDIENTE".equals(estado) ? "Pendiente" : "Entregada";
    }

    private LocalDateTime fechaCancelacion;

    private LocalDateTime fechaHora = LocalDateTime.now();

    public String getEntregadoATexto() {
        if ("PRECEPTOR".equals(entregadoA)) return "Preceptor a cargo";
        if ("RESIDENTE".equals(entregadoA)) return "Residente titular";
        return "—";
    }
}
