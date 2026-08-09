package com.upeu.comedorupeu.services;

import com.upeu.comedorupeu.models.ProgramacionHorario;
import com.upeu.comedorupeu.repository.ProgramacionHorarioRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgendaService {

    public static final String CELDA = "TURNO_PUNTO";

    private final ProgramacionHorarioRepository programacionRepo;

    public AgendaService(ProgramacionHorarioRepository programacionRepo) {
        this.programacionRepo = programacionRepo;
    }

    private static String llave(ProgramacionHorario c) {
        Long idPunto = c.getPunto() == null ? null : c.getPunto().getIdPunto();
        return c.getTipoTurno() + "-" + idPunto;
    }

    public Map<String, ProgramacionHorario> celdasDe(LocalDate fecha) {
        Map<String, ProgramacionHorario> efectivas = new LinkedHashMap<>();

        for (ProgramacionHorario c : programacionRepo
                .findByObjetivoAndDiaSemanaAndFechaIsNull(CELDA, fecha.getDayOfWeek().getValue())) {
            if (c.getPunto() != null && c.getTipoTurno() != null) efectivas.put(llave(c), c);
        }

        for (ProgramacionHorario c : programacionRepo.findByObjetivoAndFecha(CELDA, fecha)) {
            if (c.getPunto() == null || c.getTipoTurno() == null) continue;

            if (Boolean.FALSE.equals(c.getActivo())) {
                efectivas.remove(llave(c));
            } else {
                efectivas.put(llave(c), c);
            }
        }
        return efectivas;
    }

    public List<ProgramacionHorario> listaDe(LocalDate fecha) {
        return new ArrayList<>(celdasDe(fecha).values());
    }

    public List<ProgramacionHorario> listaDe(LocalDate fecha, Long idPunto) {
        List<ProgramacionHorario> propias = new ArrayList<>();
        for (ProgramacionHorario c : listaDe(fecha)) {
            if (c.getPunto() != null && c.getPunto().getIdPunto().equals(idPunto)) propias.add(c);
        }
        return propias;
    }

    public boolean esHeredada(ProgramacionHorario celda) {
        return celda != null && celda.getFecha() == null;
    }
}
