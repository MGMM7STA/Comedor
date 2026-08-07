package com.upeu.comedorupeu.services.alcance;

import com.upeu.comedorupeu.models.Ausencia;
import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.models.SolicitudExtemporanea;

import java.time.LocalDate;
import java.util.List;

public interface AlcanceDatos {

    String residenciaGenero();

    List<Residente> residentesActivos();

    List<Residente> residentesParaHistorial();

    static boolean yaEnVigencia(Residente r) {
        return vigenteEn(r, LocalDate.now());
    }

    static boolean vigenteEn(Residente r, LocalDate fecha) {
        if (r != null && r.estaBorrado()) return false;
        return r == null || r.getFechaIngreso() == null || fecha == null
                || !r.getFechaIngreso().isAfter(fecha);
    }

    List<SolicitudExtemporanea> reservas(LocalDate desde, LocalDate hasta);

    List<Ausencia> justificaciones(LocalDate desde, LocalDate hasta);

    boolean alcanza(Residente residente);
}
