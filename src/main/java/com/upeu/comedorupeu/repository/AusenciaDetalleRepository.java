package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.AusenciaDetalle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AusenciaDetalleRepository extends JpaRepository<AusenciaDetalle, Long> {

    Optional<AusenciaDetalle> findFirstByAusenciaResidenteIdResidenteAndFechaAndTipoComida(Long idResidente, LocalDate fecha, String tipoComida);

    List<AusenciaDetalle> findByFechaAndTipoComida(LocalDate fecha, String tipoComida);
}
