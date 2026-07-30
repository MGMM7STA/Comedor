package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.EventoEntrega;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventoEntregaRepository extends JpaRepository<EventoEntrega, Long> {

    List<EventoEntrega> findByEventoIdEvento(Long idEvento);

    Optional<EventoEntrega> findFirstByEventoIdEventoAndResidenteIdResidente(Long idEvento, Long idResidente);

    long countByEventoIdEventoAndRecibidoTrue(Long idEvento);

    List<EventoEntrega> findByResidenteIdResidente(Long idResidente);
}
