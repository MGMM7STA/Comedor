package com.upeu.comedorupeu.dto;

import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.models.SolicitudExtemporanea;
import com.upeu.comedorupeu.models.Turno;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ValidacionResultado {

    private String tipo;
    private Residente residente;

    private Turno turnoActivo;
    private String motivo;
    private JustificacionInfo justificacion;
    private SolicitudExtemporanea solicitud;

    private SolicitudExtemporanea reservaExtra;

    private SolicitudExtemporanea reservaEntregada;

    private com.upeu.comedorupeu.models.Marcacion marcacionPrevia;

    private Map<String, Boolean> consumoDia = new LinkedHashMap<>();

    private List<String> checks = new ArrayList<>();

    private String advertencia;

    private boolean racionEspecial;

    public boolean isPuedeDecidir() {
        return !"NO_ENCONTRADO".equals(tipo) && !"SIN_TURNO".equals(tipo);
    }
}
