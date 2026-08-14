package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.Ausencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AusenciaRepository extends JpaRepository<Ausencia, Long> {
    List<Ausencia> findByResidenteIdResidenteOrderByFechaInicioDesc(Long idResidente);
    List<Ausencia> findAllByOrderByFechaInicioDesc();

    List<Ausencia> findByFechaInicioLessThanEqualAndFechaFinGreaterThanEqualOrderByFechaInicioAsc(
            java.time.LocalDate hasta, java.time.LocalDate desde);

    List<Ausencia> findByFechaInicioLessThanEqualAndFechaFinGreaterThanEqualAndResidentePabellonOrderByFechaInicioAsc(
            java.time.LocalDate hasta, java.time.LocalDate desde, String pabellon);
}
