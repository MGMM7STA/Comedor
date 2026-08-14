package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.Turno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

    Optional<Turno> findByFechaAndTipo(LocalDate fecha, String tipo);

    List<Turno> findByFecha(LocalDate fecha);

}
