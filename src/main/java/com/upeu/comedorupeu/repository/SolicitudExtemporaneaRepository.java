package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.SolicitudExtemporanea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SolicitudExtemporaneaRepository extends JpaRepository<SolicitudExtemporanea, Long> {

    Optional<SolicitudExtemporanea> findFirstByResidenteIdResidenteAndFechaAndTipoComidaAndEstado(
            Long idResidente, LocalDate fecha, String tipoComida, String estado);

    List<SolicitudExtemporanea> findByResidenteIdResidenteOrderByFechaHoraDesc(Long idResidente);

    List<SolicitudExtemporanea> findByFechaOrderByTipoComidaAsc(LocalDate fecha);

    Optional<SolicitudExtemporanea> findFirstByResidenteIdResidenteAndFechaAndEstado(Long idResidente, LocalDate fecha, String estado);

    List<SolicitudExtemporanea> findByResidenteIdResidenteAndFechaBetweenOrderByFechaAsc(Long idResidente, LocalDate desde, LocalDate hasta);

    List<SolicitudExtemporanea> findByFechaBetweenOrderByFechaAscTipoComidaAsc(LocalDate desde, LocalDate hasta);

    List<SolicitudExtemporanea> findByFechaBetweenAndResidentePabellonOrderByFechaAscTipoComidaAsc(
            LocalDate desde, LocalDate hasta, String pabellon);

    @org.springframework.data.jpa.repository.Query("SELECT s.fecha FROM SolicitudExtemporanea s "
            + "WHERE s.estado = 'PENDIENTE' AND s.fecha >= :desde")
    List<LocalDate> fechasPendientes(
            @org.springframework.data.repository.query.Param("desde") LocalDate desde);
}
