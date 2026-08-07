package com.upeu.comedorupeu.services.alcance;

import com.upeu.comedorupeu.models.Ausencia;
import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.models.SolicitudExtemporanea;
import com.upeu.comedorupeu.repository.AusenciaRepository;
import com.upeu.comedorupeu.repository.ResidenteRepository;
import com.upeu.comedorupeu.repository.SolicitudExtemporaneaRepository;

import java.time.LocalDate;
import java.util.List;

public class AlcanceTotal implements AlcanceDatos {

    private final ResidenteRepository residenteRepo;
    private final SolicitudExtemporaneaRepository solicitudRepo;
    private final AusenciaRepository ausenciaRepo;

    public AlcanceTotal(ResidenteRepository residenteRepo,
                        SolicitudExtemporaneaRepository solicitudRepo,
                        AusenciaRepository ausenciaRepo) {
        this.residenteRepo = residenteRepo;
        this.solicitudRepo = solicitudRepo;
        this.ausenciaRepo = ausenciaRepo;
    }

    @Override
    public String residenciaGenero() {
        return null;
    }

    @Override
    public List<Residente> residentesActivos() {
        return residenteRepo.findByEstadoOrderByApellidoAsc("ACTIVO").stream()
                .filter(AlcanceDatos::yaEnVigencia)
                .toList();
    }

    @Override
    public List<Residente> residentesParaHistorial() {
        return residenteRepo.findAll().stream()
                .sorted(java.util.Comparator.comparing(r -> r.getApellido() == null ? "" : r.getApellido()))
                .toList();
    }

    @Override
    public List<SolicitudExtemporanea> reservas(LocalDate desde, LocalDate hasta) {
        return solicitudRepo.findByFechaBetweenOrderByFechaAscTipoComidaAsc(desde, hasta).stream()
                .filter(s -> AlcanceDatos.vigenteEn(s.getResidente(), s.getFecha()))
                .toList();
    }

    @Override
    public List<Ausencia> justificaciones(LocalDate desde, LocalDate hasta) {
        return ausenciaRepo.findByFechaInicioLessThanEqualAndFechaFinGreaterThanEqualOrderByFechaInicioAsc(hasta, desde)
                .stream()
                .filter(a -> AlcanceDatos.vigenteEn(a.getResidente(), a.getFechaFin()))
                .toList();
    }

    @Override
    public boolean alcanza(Residente residente) {
        return residente != null;
    }
}
