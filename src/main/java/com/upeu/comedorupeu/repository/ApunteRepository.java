package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.Apunte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApunteRepository extends JpaRepository<Apunte, Long> {

    List<Apunte> findTop10ByOrderByFechaHoraDesc();

    List<Apunte> findTop10ByTipoOrderByFechaHoraDesc(String tipo);
}
