package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.EventoEspecial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EventoEspecialRepository extends JpaRepository<EventoEspecial, Long> {

    List<EventoEspecial> findAllByOrderByFechaEnvioDesc();

    List<EventoEspecial> findByEstadoAndFechaEvento(String estado, LocalDate fechaEvento);

    List<EventoEspecial> findByEstadoAndFechaEventoBetweenOrderByFechaEventoAsc(String estado, LocalDate desde, LocalDate hasta);

    long countByEstado(String estado);
}
