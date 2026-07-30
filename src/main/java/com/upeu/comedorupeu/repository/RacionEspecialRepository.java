package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.RacionEspecial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RacionEspecialRepository extends JpaRepository<RacionEspecial, Long> {

    List<RacionEspecial> findByResidenteIdResidenteOrderByFechaInicioDesc(Long idResidente);

    List<RacionEspecial> findByResidenteIdResidenteAndFechaFinGreaterThanEqualOrderByFechaInicioAsc(
            Long idResidente, LocalDate fecha);
}
