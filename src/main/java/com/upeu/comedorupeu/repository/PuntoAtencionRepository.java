package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.PuntoAtencion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PuntoAtencionRepository extends JpaRepository<PuntoAtencion, Long> {

    List<PuntoAtencion> findAllByOrderByNombreAsc();

    List<PuntoAtencion> findByActivoTrue();

    Optional<PuntoAtencion> findFirstByCajeroIdUsuario(Long idUsuario);

    List<PuntoAtencion> findByCajeroIdUsuario(Long idUsuario);

    @org.springframework.data.jpa.repository.Query(
            "SELECT p FROM PuntoAtencion p WHERE p.eliminado IS NULL OR p.eliminado = false ORDER BY p.nombre")
    List<PuntoAtencion> vigentes();

    @org.springframework.data.jpa.repository.Query(
            "SELECT p FROM PuntoAtencion p WHERE p.eliminado = true ORDER BY p.nombre")
    List<PuntoAtencion> eliminados();
}
