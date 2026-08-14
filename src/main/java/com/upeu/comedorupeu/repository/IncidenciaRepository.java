package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.Incidencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidenciaRepository extends JpaRepository<Incidencia, Long> {
    List<Incidencia> findTop30ByOrderByFechaHoraDesc();
    List<Incidencia> findTop30ByTipoOrderByFechaHoraDesc(String tipo);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(i) FROM Incidencia i "
            + "WHERE (i.atendida IS NULL OR i.atendida = false) AND (i.tipo IS NULL OR i.tipo <> 'EXCLUSION')")
    long contarPendientes();

    @org.springframework.data.jpa.repository.Query("SELECT i.fechaHora FROM Incidencia i "
            + "WHERE (i.atendida IS NULL OR i.atendida = false) AND i.fechaHora >= :desde")
    List<java.time.LocalDateTime> fechasPendientes(
            @org.springframework.data.repository.query.Param("desde") java.time.LocalDateTime desde);

    @org.springframework.data.jpa.repository.Query("SELECT i FROM Incidencia i WHERE i.tipo = 'EXCLUSION' "
            + "AND i.refEvento = :idEvento AND (i.atendida IS NULL OR i.atendida = false) ORDER BY i.fechaHora DESC")
    List<Incidencia> exclusionesPendientesDe(@org.springframework.data.repository.query.Param("idEvento") Long idEvento);
}
