package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.ProgramacionHorario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProgramacionHorarioRepository extends JpaRepository<ProgramacionHorario, Long> {

    List<ProgramacionHorario> findByObjetivo(String objetivo);

    Optional<ProgramacionHorario> findFirstByObjetivoAndTipoTurnoAndDiaSemana(String objetivo, String tipoTurno, Integer diaSemana);

    List<ProgramacionHorario> findByObjetivoAndDiaSemana(String objetivo, Integer diaSemana);

    List<ProgramacionHorario> findByObjetivoAndPuntoIdPuntoAndDiaSemana(String objetivo, Long idPunto, Integer diaSemana);

    List<ProgramacionHorario> findByObjetivoAndFecha(String objetivo, java.time.LocalDate fecha);

    List<ProgramacionHorario> findByObjetivoAndDiaSemanaAndFechaIsNull(String objetivo, Integer diaSemana);

    Optional<ProgramacionHorario> findFirstByObjetivoAndPuntoIdPuntoAndTipoTurnoAndFecha(
            String objetivo, Long idPunto, String tipoTurno, java.time.LocalDate fecha);

    Optional<ProgramacionHorario> findFirstByObjetivoAndPuntoIdPuntoAndTipoTurnoAndDiaSemanaAndFechaIsNull(
            String objetivo, Long idPunto, String tipoTurno, Integer diaSemana);
}
