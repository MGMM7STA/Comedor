package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.ConfigTurno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfigTurnoRepository extends JpaRepository<ConfigTurno, Long> {
    Optional<ConfigTurno> findByTipo(String tipo);
}
