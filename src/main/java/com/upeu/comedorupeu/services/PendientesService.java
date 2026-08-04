package com.upeu.comedorupeu.services;

import com.upeu.comedorupeu.repository.EventoEspecialRepository;
import com.upeu.comedorupeu.repository.IncidenciaRepository;
import com.upeu.comedorupeu.repository.SolicitudExtemporaneaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class PendientesService {

    private static final int MESES_ATRAS = 12;

    private final IncidenciaRepository incidenciaRepo;
    private final SolicitudExtemporaneaRepository solicitudRepo;
    private final EventoEspecialRepository eventoRepo;

    public PendientesService(IncidenciaRepository incidenciaRepo,
                             SolicitudExtemporaneaRepository solicitudRepo,
                             EventoEspecialRepository eventoRepo) {
        this.incidenciaRepo = incidenciaRepo;
        this.solicitudRepo = solicitudRepo;
        this.eventoRepo = eventoRepo;
    }

    public Map<String, Integer> incidencias() {
        Map<String, Integer> mapa = new HashMap<>();
        for (LocalDateTime f : incidenciaRepo.fechasPendientes(desde().atStartOfDay())) {
            if (f != null) sumar(mapa, f.toLocalDate());
        }
        return mapa;
    }

    public Map<String, Integer> reservas() {
        Map<String, Integer> mapa = new HashMap<>();
        for (LocalDate f : solicitudRepo.fechasPendientes(desde())) {
            if (f != null) sumar(mapa, f);
        }
        return mapa;
    }

    public Map<String, Integer> eventos() {
        Map<String, Integer> mapa = new HashMap<>();
        for (LocalDateTime f : eventoRepo.fechasPendientes(desde().atStartOfDay())) {
            if (f != null) sumar(mapa, f.toLocalDate());
        }
        return mapa;
    }

    private LocalDate desde() {
        return LocalDate.now().minusMonths(MESES_ATRAS);
    }

    private void sumar(Map<String, Integer> mapa, LocalDate fecha) {
        String clave = fecha.toString();
        mapa.put(clave, mapa.getOrDefault(clave, 0) + 1);
    }
}
