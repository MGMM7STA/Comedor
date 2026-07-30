package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.RacionEspecialDetalle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface RacionEspecialDetalleRepository extends JpaRepository<RacionEspecialDetalle, Long> {

    Optional<RacionEspecialDetalle> findFirstByRacionEspecialResidenteIdResidenteAndFechaAndTipoComida(
            Long idResidente, LocalDate fecha, String tipoComida);
}
