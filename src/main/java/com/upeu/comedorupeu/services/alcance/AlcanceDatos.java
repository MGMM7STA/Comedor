package com.upeu.comedorupeu.services.alcance;

import com.upeu.comedorupeu.models.Ausencia;
import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.models.SolicitudExtemporanea;

import java.time.LocalDate;
import java.util.List;

public interface AlcanceDatos {

    String residenciaGenero();

    List<Residente> residentesActivos();

    static boolean yaEnVigencia(Residente r) {
        return r.getFechaIngreso() == null || !r.getFechaIngreso().isAfter(LocalDate.now());
    }

    List<SolicitudExtemporanea> reservas(LocalDate desde, LocalDate hasta);

    List<Ausencia> justificaciones(LocalDate desde, LocalDate hasta);

    boolean alcanza(Residente residente);
}
