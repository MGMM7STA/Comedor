package com.upeu.comedorupeu.services;

import com.upeu.comedorupeu.models.PeriodoInactivo;
import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.repository.PeriodoInactivoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class VigenciaService {

    private final PeriodoInactivoRepository periodoRepo;

    public VigenciaService(PeriodoInactivoRepository periodoRepo) {
        this.periodoRepo = periodoRepo;
    }

    public void marcarInactivo(Residente r) {
        if (periodoRepo.findFirstByResidenteIdResidenteAndHastaIsNull(r.getIdResidente()).isPresent()) return;
        PeriodoInactivo p = new PeriodoInactivo();
        p.setResidente(r);
        p.setDesde(LocalDateTime.now());
        p.setMotivo("INACTIVO");
        periodoRepo.save(p);
    }

    public void marcarActivo(Residente r) {
        periodoRepo.findFirstByResidenteIdResidenteAndHastaIsNull(r.getIdResidente())
                .ifPresent(p -> {
                    if (!"BORRADO".equals(p.getMotivo())) {
                        p.setHasta(LocalDateTime.now());
                        periodoRepo.save(p);
                    }
                });
    }

    public void marcarBorrado(Residente r) {
        periodoRepo.findFirstByResidenteIdResidenteAndHastaIsNull(r.getIdResidente())
                .ifPresentOrElse(
                        p -> { p.setMotivo("BORRADO"); periodoRepo.save(p); },
                        () -> {
                            PeriodoInactivo p = new PeriodoInactivo();
                            p.setResidente(r);
                            p.setDesde(LocalDateTime.now());
                            p.setMotivo("BORRADO");
                            periodoRepo.save(p);
                        });
    }

    public LocalDate diaDelBorrado(Residente r) {
        if (r == null || r.getIdResidente() == null || !r.estaBorrado()) return null;
        return periodoRepo.findByResidenteIdResidenteOrderByDesdeAsc(r.getIdResidente()).stream()
                .filter(p -> "BORRADO".equals(p.getMotivo()))
                .map(p -> p.getDesde() == null ? null : p.getDesde().toLocalDate())
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }

    public String estadoEn(Residente r, LocalDate fecha, java.time.LocalTime hora) {
        if (r == null || r.getIdResidente() == null) return null;
        LocalDateTime momento = fecha.atTime(hora == null ? java.time.LocalTime.NOON : hora);
        List<PeriodoInactivo> periodos = periodoRepo.findByResidenteIdResidenteOrderByDesdeAsc(r.getIdResidente());
        for (PeriodoInactivo p : periodos) {
            if (p.cubre(momento)) return "BORRADO".equals(p.getMotivo()) ? "BORRADO" : "INACTIVO";
        }
        return null;
    }
}
