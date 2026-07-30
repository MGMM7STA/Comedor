package com.upeu.comedorupeu.services;

import com.upeu.comedorupeu.dto.ValidacionResultado;
import com.upeu.comedorupeu.models.Marcacion;
import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.models.SolicitudExtemporanea;
import com.upeu.comedorupeu.models.Turno;
import com.upeu.comedorupeu.repository.MarcacionRepository;
import com.upeu.comedorupeu.repository.ResidenteRepository;
import com.upeu.comedorupeu.repository.SolicitudExtemporaneaRepository;
import com.upeu.comedorupeu.repository.TurnoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class ValidacionService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ResidenteRepository residenteRepo;
    private final MarcacionRepository marcacionRepo;
    private final TurnoRepository turnoRepo;
    private final TurnoService turnoService;
    private final JustificacionService justificacionService;
    private final SolicitudExtemporaneaRepository solicitudRepo;

    public ValidacionService(ResidenteRepository residenteRepo, MarcacionRepository marcacionRepo,
                             TurnoRepository turnoRepo, TurnoService turnoService,
                             JustificacionService justificacionService,
                             SolicitudExtemporaneaRepository solicitudRepo) {
        this.residenteRepo = residenteRepo;
        this.marcacionRepo = marcacionRepo;
        this.turnoRepo = turnoRepo;
        this.turnoService = turnoService;
        this.justificacionService = justificacionService;
        this.solicitudRepo = solicitudRepo;
    }

    private com.upeu.comedorupeu.repository.RacionEspecialDetalleRepository racionEspecialRepo;

    @org.springframework.beans.factory.annotation.Autowired
    public void setRacionEspecialRepo(com.upeu.comedorupeu.repository.RacionEspecialDetalleRepository repo) {
        this.racionEspecialRepo = repo;
    }

    public ValidacionResultado validar(String codigo) {
        ValidacionResultado res = evaluar(codigo);
        if (res.getResidente() != null && res.getTurnoActivo() != null) {
            racionEspecialRepo.findFirstByRacionEspecialResidenteIdResidenteAndFechaAndTipoComida(
                            res.getResidente().getIdResidente(), LocalDate.now(), res.getTurnoActivo().getTipo())
                    .ifPresent(d -> res.setRacionEspecial(true));
        }
        return res;
    }

    private ValidacionResultado evaluar(String codigo) {
        ValidacionResultado res = new ValidacionResultado();

        Optional<Residente> opt = residenteRepo.findByCodigoAcceso(codigo == null ? "" : codigo.trim());
        if (opt.isEmpty()) {
            res.setTipo("NO_ENCONTRADO");
            res.setMotivo("El código \"" + codigo + "\" no corresponde a ningún residente registrado.");
            return res;
        }
        Residente residente = opt.get();
        res.setResidente(residente);
        cargarConsumoDia(res, residente);

        Optional<Turno> turnoOpt = turnoService.turnoActivo();
        if (turnoOpt.isEmpty()) {

            if (buscarReserva(res, residente)) return res;

            Optional<SolicitudExtemporanea> entregada = solicitudRepo
                    .findFirstByResidenteIdResidenteAndFechaAndEstado(
                            residente.getIdResidente(), LocalDate.now(), "ATENDIDA");
            if (entregada.isPresent()) {
                res.setTipo("RESERVA_ENTREGADA");
                res.setReservaEntregada(entregada.get());
                return res;
            }
            res.setTipo("SIN_TURNO");
            res.setMotivo("No hay ningún turno de comida activo y el residente no tiene ración reservada.");
            return res;
        }
        Turno turno = turnoOpt.get();
        res.setTurnoActivo(turno);

        solicitudRepo.findFirstByResidenteIdResidenteAndFechaAndEstado(
                        residente.getIdResidente(), LocalDate.now(), "PENDIENTE")
                .ifPresent(res::setReservaExtra);

        if (res.getReservaExtra() == null) {
            solicitudRepo.findFirstByResidenteIdResidenteAndFechaAndEstado(
                            residente.getIdResidente(), LocalDate.now(), "ATENDIDA")
                    .ifPresent(res::setReservaEntregada);
        }

        if (!"ACTIVO".equals(residente.getEstado())) {
            res.setTipo("ADVERTENCIA");
            res.setMotivo("Residente INACTIVO — no está habilitado para el comedor.");
            res.setAdvertencia("El sistema recomienda denegar el ingreso.");
            return res;
        }

        List<Marcacion> previas = marcacionRepo.consumosVigentes(residente.getIdResidente(), turno.getIdTurno());
        if (!previas.isEmpty()) {
            Marcacion previa = previas.get(0);
            res.setTipo("ADVERTENCIA");
            res.setMarcacionPrevia(previa);
            res.setMotivo("Ya registró ingreso a " + capitalizar(turno.getTipo()) + " hoy a las "
                    + HORA.format(previa.getFechaHora())
                    + (previa.getPunto() != null ? " por " + previa.getPunto().getNombre() : "") + ".");
            res.setAdvertencia("Posible doble entrada o suplantación. Verifique la identidad con la foto. "
                    + "Si el registro anterior fue un error, puede marcarlo como ración alterada más abajo.");
            return res;
        }

        if (Boolean.TRUE.equals(residente.getDeuda())) {
            res.setTipo("ADVERTENCIA");
            res.setMotivo("Deuda pendiente — incumplimiento de pago registrado por preceptoría.");
            res.setAdvertencia("El sistema recomienda denegar hasta regularizar el pago.");
            return res;
        }

        var justificacion = justificacionService.buscar(residente, LocalDate.now(), turno.getTipo());
        if (justificacion.isPresent()) {
            res.setTipo("JUSTIFICADO");
            res.setJustificacion(justificacion.get());
            res.setAdvertencia("Residente con justificación activa. No se registra falta, figura como justificado.");
            return res;
        }

        res.setTipo("OK");
        res.getChecks().add("Primera entrada del turno");
        res.getChecks().add("Residente activo");
        res.getChecks().add("Sin deuda pendiente");
        res.getChecks().add("Sin permiso de viaje");
        return res;
    }

    private boolean buscarReserva(ValidacionResultado res, Residente residente) {
        LocalDate hoy = LocalDate.now();
        for (Turno t : turnoRepo.findByFecha(hoy)) {
            Optional<SolicitudExtemporanea> sol = solicitudRepo
                    .findFirstByResidenteIdResidenteAndFechaAndTipoComidaAndEstado(
                            residente.getIdResidente(), hoy, t.getTipo(), "PENDIENTE");
            if (sol.isPresent()) {
                res.setTipo("RESERVA");
                res.setTurnoActivo(t);
                res.setSolicitud(sol.get());
                res.getChecks().add("Ración reservada — llegada tardía autorizada");
                res.getChecks().add("Turno: " + capitalizar(t.getTipo()));
                if (sol.get().getHoraRecojo() != null) {
                    res.getChecks().add("Hora de recojo prevista: " + sol.get().getHoraRecojo());
                }
                res.getChecks().add("Motivo: " + sol.get().getMotivo());
                if (sol.get().getUsuario() != null) {
                    res.getChecks().add("Autoriza: " + sol.get().getUsuario().getNombreCompleto());
                }
                return true;
            }
        }
        return false;
    }

    private void cargarConsumoDia(ValidacionResultado res, Residente residente) {
        LocalDate hoy = LocalDate.now();
        for (String tipo : TurnoService.TIPOS) {
            boolean consumio = turnoRepo.findByFechaAndTipo(hoy, tipo)
                    .map(t -> !marcacionRepo.consumosVigentes(residente.getIdResidente(), t.getIdTurno()).isEmpty())
                    .orElse(false);
            res.getConsumoDia().put(tipo, consumio);
        }
    }

    public static String capitalizar(String s) {
        if (s == null || s.isBlank()) return "";
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
