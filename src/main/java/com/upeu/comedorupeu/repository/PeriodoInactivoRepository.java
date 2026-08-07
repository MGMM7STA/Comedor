package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.PeriodoInactivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PeriodoInactivoRepository extends JpaRepository<PeriodoInactivo, Long> {

    List<PeriodoInactivo> findByResidenteIdResidenteOrderByDesdeAsc(Long idResidente);

    Optional<PeriodoInactivo> findFirstByResidenteIdResidenteAndHastaIsNull(Long idResidente);
}
