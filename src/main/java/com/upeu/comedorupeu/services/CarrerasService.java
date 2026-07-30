package com.upeu.comedorupeu.services;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CarrerasService {

    private static final Map<String, List<String>> FACULTADES = new LinkedHashMap<>();

    static {
        FACULTADES.put("Facultad de Ciencias Humanas y Educación", List.of(
                "Educación: Especialidad Primaria",
                "Educación Inicial y Puericultura",
                "Educación: Especialidad Lingüística e Inglés",
                "Educación, Especialidad Inglés y Español",
                "Educación, Especialidad Educación Física, Recreación y Deportes",
                "Educación, Especialidad Primaria y Pedagogía Terapéutica",
                "Derecho"));
        FACULTADES.put("Facultad de Ingeniería y Arquitectura", List.of(
                "Ingeniería de Sistemas",
                "Ingeniería Civil",
                "Ingeniería Ambiental",
                "Ingeniería de Industrias Alimentarias",
                "Arquitectura y Urbanismo",
                "Arquitectura"));
        FACULTADES.put("Facultad de Ciencias de la Salud", List.of(
                "Enfermería, Presencial",
                "Psicología",
                "Nutrición Humana"));
        FACULTADES.put("Facultad de Ciencias Empresariales", List.of(
                "Contabilidad y Gestión Tributaria",
                "Contabilidad, Gestión Tributaria y Aduanera",
                "Administración"));
        FACULTADES.put("Facultad de Teología", List.of(
                "Teología"));
    }

    public Map<String, List<String>> facultades() {
        return FACULTADES;
    }
}
