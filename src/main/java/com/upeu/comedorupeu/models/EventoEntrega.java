package com.upeu.comedorupeu.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "evento_entrega", uniqueConstraints = @UniqueConstraint(columnNames = {"id_evento", "id_residente"}))
@Data
public class EventoEntrega {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idEntrega;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_evento")
    private EventoEspecial evento;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_residente")
    private Residente residente;

    private Boolean recibido = true;

    private LocalDateTime fechaHora = LocalDateTime.now();
}
