package com.upeu.comedorupeu.services.alcance;

import com.upeu.comedorupeu.models.Ausencia;
import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.models.SolicitudExtemporanea;
import com.upeu.comedorupeu.repository.AusenciaRepository;
import com.upeu.comedorupeu.repository.ResidenteRepository;
import com.upeu.comedorupeu.repository.SolicitudExtemporaneaRepository;

import java.time.LocalDate;
import java.util.List;

public class AlcancePorResidencia implements AlcanceDatos {

    private final String residencia;
    private final ResidenteRepository residenteRepo;
    private final SolicitudExtemporaneaRepository solicitudRepo;
    private final AusenciaRepository ausenciaRepo;

    public AlcancePorResidencia(String residencia,
                                ResidenteRepository residenteRepo,
                                SolicitudExtemporaneaRepository solicitudRepo,
                                AusenciaRepository ausenciaRepo) {
        this.residencia = residencia;
        this.residenteRepo = residenteRepo;
        this.solicitudRepo = solicitudRepo;
        this.ausenciaRepo = ausenciaRepo;
    }

    @Override
    public String residenciaGenero() {
        return residencia;
    }

    @Override
    public List<Residente> residentesActivos() {
        return residenteRepo.findByEstadoAndPabellonOrderByApellidoAsc("ACTIVO", residencia).stream()
                .filter(AlcanceDatos::yaEnVigencia)
                .toList();
    }

    @Override
    public List<SolicitudExtemporanea> reservas(LocalDate desde, LocalDate hasta) {
        return solicitudRepo.findByFechaBetweenAndResidentePabellonOrderByFechaAscTipoComidaAsc(desde, hasta, residencia);
    }

    @Override
    public List<Ausencia> justificaciones(LocalDate desde, LocalDate hasta) {
        return ausenciaRepo
                .findByFechaInicioLessThanEqualAndFechaFinGreaterThanEqualAndResidentePabellonOrderByFechaInicioAsc(
                        hasta, desde, residencia);
    }

    @Override
    public boolean alcanza(Residente residente) {
        return residente != null && residencia.equals(residente.getPabellon());
    }
}
