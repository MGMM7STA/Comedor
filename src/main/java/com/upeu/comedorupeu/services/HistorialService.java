package com.upeu.comedorupeu.services;

import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class HistorialService {

    private static final LocalDate DESDE_SIEMPRE = LocalDate.of(2000, 1, 1);

    private final MarcacionRepository marcacionRepo;
    private final SolicitudExtemporaneaRepository solicitudRepo;
    private final AusenciaRepository ausenciaRepo;
    private final EventoEntregaRepository entregaRepo;
    private final RacionEspecialRepository racionEspecialRepo;

    public HistorialService(MarcacionRepository marcacionRepo,
                            SolicitudExtemporaneaRepository solicitudRepo,
                            AusenciaRepository ausenciaRepo,
                            EventoEntregaRepository entregaRepo,
                            RacionEspecialRepository racionEspecialRepo) {
        this.marcacionRepo = marcacionRepo;
        this.solicitudRepo = solicitudRepo;
        this.ausenciaRepo = ausenciaRepo;
        this.entregaRepo = entregaRepo;
        this.racionEspecialRepo = racionEspecialRepo;
    }

    private LocalDate hasta() {
        return LocalDate.now().plusYears(5);
    }

    public String resumen(Residente r) {
        Long id = (r == null) ? null : r.getIdResidente();
        if (id == null) return null;

        long marcaciones = marcacionRepo.findByResidenteIdResidenteAndFechaHoraBetween(
                id, DESDE_SIEMPRE.atStartOfDay(), hasta().atStartOfDay()).size();
        long reservas = solicitudRepo.findByResidenteIdResidenteOrderByFechaHoraDesc(id).size();
        long ausencias = ausenciaRepo.findByResidenteIdResidenteOrderByFechaInicioDesc(id).size();
        long eventos = entregaRepo.findByResidenteIdResidente(id).size();
        long raciones = racionEspecialRepo.findByResidenteIdResidenteOrderByFechaInicioDesc(id).size();

        List<String> partes = new ArrayList<>();
        if (marcaciones > 0) partes.add(marcaciones + " ingreso(s) registrados en caja");
        if (reservas > 0) partes.add(reservas + " reserva(s)");
        if (ausencias > 0) partes.add(ausencias + " justificación(es)");
        if (eventos > 0) partes.add(eventos + " participación(es) en eventos");
        if (raciones > 0) partes.add(raciones + " ración(es) especial(es)");
        return partes.isEmpty() ? null : String.join(", ", partes);
    }

    public void borrar(Residente r) {
        Long id = (r == null) ? null : r.getIdResidente();
        if (id == null) return;

        marcacionRepo.deleteAll(marcacionRepo.findByResidenteIdResidenteAndFechaHoraBetween(
                id, DESDE_SIEMPRE.atStartOfDay(), hasta().atStartOfDay()));
        solicitudRepo.deleteAll(solicitudRepo.findByResidenteIdResidenteOrderByFechaHoraDesc(id));
        ausenciaRepo.deleteAll(ausenciaRepo.findByResidenteIdResidenteOrderByFechaInicioDesc(id));
        entregaRepo.deleteAll(entregaRepo.findByResidenteIdResidente(id));
        racionEspecialRepo.deleteAll(racionEspecialRepo.findByResidenteIdResidenteOrderByFechaInicioDesc(id));
    }
}
