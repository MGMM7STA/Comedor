package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.RacionEspecial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RacionEspecialRepository extends JpaRepository<RacionEspecial, Long> {

    List<RacionEspecial> findByResidenteIdResidenteOrderByFechaInicioDesc(Long idResidente);
}
