package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.Marcacion;
import com.upeu.comedorupeu.models.Turno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MarcacionRepository extends JpaRepository<Marcacion, Long> {

    boolean existsByResidenteIdResidenteAndTurnoIdTurnoAndEstado(Long idResidente, Long idTurno, String estado);

    @org.springframework.data.jpa.repository.Query("SELECT m FROM Marcacion m WHERE m.residente.idResidente = :idResidente " +
            "AND m.turno.idTurno = :idTurno AND m.estado IN ('PERMITIDO','JUSTIFICADO') " +
            "AND (m.anulada IS NULL OR m.anulada = false) ORDER BY m.fechaHora DESC")
    List<Marcacion> consumosVigentes(@org.springframework.data.repository.query.Param("idResidente") Long idResidente,
                                     @org.springframework.data.repository.query.Param("idTurno") Long idTurno);

    List<Marcacion> findTop8ByOrderByFechaHoraDesc();

    List<Marcacion> findByTurnoOrderByFechaHoraAsc(Turno turno);

    long countByTurnoIdTurnoAndEstado(Long idTurno, String estado);
    long countByTurnoIdTurno(Long idTurno);

    List<Marcacion> findByResidenteIdResidenteAndFechaHoraBetween(Long idResidente, LocalDateTime desde, LocalDateTime hasta);

    List<Marcacion> findByFechaHoraBetweenOrderByFechaHoraAsc(LocalDateTime desde, LocalDateTime hasta);

    Optional<Marcacion> findFirstByUsuarioIdUsuarioOrderByFechaHoraDesc(Long idUsuario);

    Optional<Marcacion> findFirstByResidenteIdResidenteOrderByFechaHoraAsc(Long idResidente);

    Optional<Marcacion> findFirstByResidenteIdResidenteAndTurnoIdTurnoAndEstadoOrderByFechaHoraDesc(
            Long idResidente, Long idTurno, String estado);
}
