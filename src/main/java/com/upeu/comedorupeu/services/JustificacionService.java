package com.upeu.comedorupeu.services;

import com.upeu.comedorupeu.dto.JustificacionInfo;
import com.upeu.comedorupeu.models.Ausencia;
import com.upeu.comedorupeu.models.AusenciaDetalle;
import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.repository.AusenciaDetalleRepository;
import com.upeu.comedorupeu.repository.AusenciaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class JustificacionService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM");

    private final AusenciaDetalleRepository detalleRepo;
    private final AusenciaRepository ausenciaRepo;
    private final TurnoService turnoService;

    public JustificacionService(AusenciaDetalleRepository detalleRepo,
                                AusenciaRepository ausenciaRepo,
                                TurnoService turnoService) {
        this.detalleRepo = detalleRepo;
        this.ausenciaRepo = ausenciaRepo;
        this.turnoService = turnoService;
    }

    public String estadoDe(Ausencia a) {
        LocalDate hoy = LocalDate.now();
        if (a.getFechaInicio().isAfter(hoy)) return "FUTURA";
        boolean quedanTurnos = a.getDetalles().stream()
                .anyMatch(d -> !turnoService.turnoYaOcurrio(d.getTipoComida(), d.getFecha()));
        return quedanTurnos ? "EN_CURSO" : "PASADA";
    }

    public String cierreAnticipado(Ausencia a) {
        if (!"EN_CURSO".equals(estadoDe(a))) return null;

        a.getDetalles().removeIf(d -> !turnoService.turnoYaOcurrio(d.getTipoComida(), d.getFecha()));
        if (a.getDetalles().isEmpty()) {

            ausenciaRepo.delete(a);
            return "La justificación se canceló por completo (ningún turno llegó a transcurrir justificado).";
        }

        LocalDate nuevoFin = a.getDetalles().stream()
                .map(AusenciaDetalle::getFecha)
                .max(LocalDate::compareTo)
                .orElse(a.getFechaInicio());
        a.setFechaFin(nuevoFin);
        ausenciaRepo.save(a);
        return "Cierre anticipado aplicado: la justificación ahora termina el " + nuevoFin
                + " y el historial previo se conserva intacto.";
    }

    public Optional<JustificacionInfo> buscar(Residente residente, LocalDate fecha, String tipoComida) {
        Optional<AusenciaDetalle> detalle = detalleRepo
                .findFirstByAusenciaResidenteIdResidenteAndFechaAndTipoComida(residente.getIdResidente(), fecha, tipoComida);
        if (detalle.isPresent()) {
            var a = detalle.get().getAusencia();
            String periodo = FECHA.format(a.getFechaInicio()) + " – " + FECHA.format(a.getFechaFin());
            String autoriza = a.getUsuario() != null ? a.getUsuario().getNombreCompleto() : "Preceptoría";
            return Optional.of(new JustificacionInfo(a.getMotivo(), periodo, autoriza, "AUSENCIA"));
        }
        return Optional.empty();
    }

    public Set<Long> idsJustificados(LocalDate fecha, String tipoComida) {
        Set<Long> ids = new HashSet<>();
        for (AusenciaDetalle d : detalleRepo.findByFechaAndTipoComida(fecha, tipoComida)) {
            ids.add(d.getAusencia().getResidente().getIdResidente());
        }
        return ids;
    }
}
