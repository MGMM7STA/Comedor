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

    long countByFechaAndEstado(LocalDate fecha, String estado);

    List<SolicitudExtemporanea> findTop20ByOrderByFechaHoraDesc();

    List<SolicitudExtemporanea> findByFechaOrderByTipoComidaAsc(LocalDate fecha);

    Optional<SolicitudExtemporanea> findFirstByResidenteIdResidenteAndFechaAndEstado(Long idResidente, LocalDate fecha, String estado);

    List<SolicitudExtemporanea> findByResidenteIdResidenteAndFechaBetweenOrderByFechaAsc(Long idResidente, LocalDate desde, LocalDate hasta);

    List<SolicitudExtemporanea> findByFechaBetweenOrderByFechaAscTipoComidaAsc(LocalDate desde, LocalDate hasta);

    List<SolicitudExtemporanea> findByFechaBetweenAndResidentePabellonOrderByFechaAscTipoComidaAsc(
            LocalDate desde, LocalDate hasta, String pabellon);

    List<SolicitudExtemporanea> findByFechaAndEstadoAndGrupoLoteNotNull(LocalDate fecha, String estado);

    List<SolicitudExtemporanea> findByGrupoLoteAndFechaAndEstado(String grupoLote, LocalDate fecha, String estado);
}
