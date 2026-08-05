package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.EventoEspecial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EventoEspecialRepository extends JpaRepository<EventoEspecial, Long> {

    List<EventoEspecial> findAllByOrderByFechaEnvioDesc();

    List<EventoEspecial> findByGrupoEvento(String grupoEvento);

    List<EventoEspecial> findByEstadoAndFechaEvento(String estado, LocalDate fechaEvento);

    List<EventoEspecial> findByEstadoAndFechaEventoBetweenOrderByFechaEventoAsc(String estado, LocalDate desde, LocalDate hasta);

    long countByEstado(String estado);

    @org.springframework.data.jpa.repository.Query("SELECT e.fechaEnvio FROM EventoEspecial e "
            + "WHERE e.estado = 'PENDIENTE' AND e.fechaEnvio >= :desde")
    List<java.time.LocalDateTime> fechasPendientes(
            @org.springframework.data.repository.query.Param("desde") java.time.LocalDateTime desde);
}
