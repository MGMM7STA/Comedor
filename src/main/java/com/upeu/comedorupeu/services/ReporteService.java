package com.upeu.comedorupeu.services;

import com.upeu.comedorupeu.dto.FilaDia;
import com.upeu.comedorupeu.dto.FilaHora;
import com.upeu.comedorupeu.dto.FilaMovimiento;
import com.upeu.comedorupeu.dto.FilaSemana;
import com.upeu.comedorupeu.dto.ReporteGeneral;
import com.upeu.comedorupeu.dto.ReporteIndividual;
import com.upeu.comedorupeu.models.Marcacion;
import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.repository.MarcacionRepository;
import com.upeu.comedorupeu.repository.ResidenteRepository;
import com.upeu.comedorupeu.repository.TurnoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class ReporteService {

    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final MarcacionRepository marcacionRepo;
    private final ResidenteRepository residenteRepo;
    private final TurnoRepository turnoRepo;
    private final JustificacionService justificacionService;

    private final TurnoService turnoService;

    public ReporteService(MarcacionRepository marcacionRepo, ResidenteRepository residenteRepo,
                          TurnoRepository turnoRepo, JustificacionService justificacionService,
                          TurnoService turnoService) {
        this.marcacionRepo = marcacionRepo;
        this.residenteRepo = residenteRepo;
        this.turnoRepo = turnoRepo;
        this.justificacionService = justificacionService;
        this.turnoService = turnoService;
    }

    private static boolean vigente(Marcacion m) {
        return m.getAnulada() == null || !m.getAnulada();
    }

    public ReporteGeneral general(LocalDate desde, LocalDate hasta, String tipoTurno, Long idPunto,
                                  String pabellon, boolean recientesPrimero, String codigoFiltro) {
        ReporteGeneral rep = new ReporteGeneral();
        List<Residente> activos = (pabellon == null)
                ? residenteRepo.findByEstadoOrderByApellidoAsc("ACTIVO")
                : residenteRepo.findByEstadoAndPabellonOrderByApellidoAsc("ACTIVO", pabellon);

        String busca = (codigoFiltro == null) ? "" : codigoFiltro.trim().toLowerCase();
        if (!busca.isEmpty()) {
            activos = activos.stream()
                    .filter(r -> (r.getCodigoAcceso() != null && r.getCodigoAcceso().toLowerCase().contains(busca))
                            || r.getNombreCompleto().toLowerCase().contains(busca))
                    .toList();
        }
        boolean todos = "TODOS".equalsIgnoreCase(tipoTurno);
        List<String> tipos = todos ? TurnoService.TIPOS : List.of(tipoTurno);

        List<Marcacion> movimientos = new ArrayList<>();
        for (LocalDate f = desde; !f.isAfter(hasta); f = f.plusDays(1)) {
            for (String tipo : tipos) {
                turnoRepo.findByFechaAndTipo(f, tipo).ifPresent(t ->
                        movimientos.addAll(marcacionRepo.findByTurnoOrderByFechaHoraAsc(t)));
            }
        }
        movimientos.sort(recientesPrimero
                ? Comparator.comparing(Marcacion::getFechaHora).reversed()
                : Comparator.comparing(Marcacion::getFechaHora));

        List<Marcacion> filtrados = movimientos.stream()

                .filter(m -> idPunto == null
                        || (idPunto == -1L
                            ? (m.getPunto() != null && Boolean.TRUE.equals(m.getPunto().getEliminado()))
                            : (m.getPunto() != null && idPunto.equals(m.getPunto().getIdPunto()))))
                .filter(m -> pabellon == null || pabellon.equals(m.getResidente().getPabellon()))

                .filter(m -> busca.isEmpty()
                        || (m.getResidente().getCodigoAcceso() != null
                            && m.getResidente().getCodigoAcceso().toLowerCase().contains(busca))
                        || m.getResidente().getNombreCompleto().toLowerCase().contains(busca))
                .toList();

        Map<String, Marcacion> unaPorDia = new java.util.LinkedHashMap<>();
        for (Marcacion m : filtrados) {
            if (!vigente(m)) continue;
            String clave = m.getResidente().getIdResidente() + ":" + m.getTurno().getFecha();
            Marcacion actual = unaPorDia.get(clave);
            if (actual == null || m.getFechaHora().isAfter(actual.getFechaHora())) {
                unaPorDia.put(clave, m);
            }
        }
        List<Marcacion> visibles = new ArrayList<>(unaPorDia.values());

        visibles.sort(recientesPrimero
                ? Comparator.comparing(Marcacion::getFechaHora).reversed()
                : Comparator.comparing(Marcacion::getFechaHora));
        rep.setMovimientos(visibles);

        List<Marcacion> todosVigentes = new ArrayList<>(filtrados.stream()
                .filter(ReporteService::vigente)
                .toList());
        todosVigentes.sort(recientesPrimero
                ? Comparator.comparing(Marcacion::getFechaHora).reversed()
                : Comparator.comparing(Marcacion::getFechaHora));
        rep.setMovimientosTodos(todosVigentes);

        Set<Long> idsActivos = new HashSet<>();
        activos.forEach(r -> idsActivos.add(r.getIdResidente()));
        for (Marcacion m : filtrados) {
            if (!vigente(m)) continue;
            switch (m.getEstado()) {
                case "PERMITIDO" -> rep.setPermitidos(rep.getPermitidos() + 1);
                case "DENEGADO" -> rep.setDenegados(rep.getDenegados() + 1);
            }
        }

        LocalDate hoy = LocalDate.now();
        for (LocalDate f = desde; !f.isAfter(hasta); f = f.plusDays(1)) {
            if (f.isAfter(hoy)) break;
            final LocalDate dia = f;
            for (String tipo : tipos) {

                if (turnoRepo.findByFechaAndTipo(dia, tipo).isEmpty()) continue;

                if (dia.equals(hoy) && !turnoService.turnoYaOcurrio(tipo, dia)) continue;
                final String comida = tipo;

                Set<Long> atendidosComida = new HashSet<>();
                for (Marcacion m : filtrados) {
                    if (vigente(m) && dia.equals(m.getTurno().getFecha())
                            && comida.equals(m.getTurno().getTipo())
                            && ("PERMITIDO".equals(m.getEstado()) || "JUSTIFICADO".equals(m.getEstado()))) {
                        atendidosComida.add(m.getResidente().getIdResidente());
                    }
                }

                Set<Long> justificadosComida = new HashSet<>();
                for (Long id : justificacionService.idsJustificados(dia, comida)) {
                    if (idsActivos.contains(id)) justificadosComida.add(id);
                }

                rep.setJustificados(rep.getJustificados() + justificadosComida.size());
                rep.setAusentes(rep.getAusentes()
                        + activos.stream().filter(r -> !atendidosComida.contains(r.getIdResidente())
                                && !justificadosComida.contains(r.getIdResidente())).count());

                rep.setInasistencias(rep.getInasistencias()
                        + activos.stream().filter(r -> !atendidosComida.contains(r.getIdResidente())
                                && !justificadosComida.contains(r.getIdResidente())).count());
            }
        }

        Map<Integer, Long> porHora = new TreeMap<>();
        for (Marcacion m : filtrados) {
            if (vigente(m) && ("PERMITIDO".equals(m.getEstado()) || "JUSTIFICADO".equals(m.getEstado()))) {
                porHora.merge(m.getFechaHora().getHour(), 1L, Long::sum);
            }
        }
        long max = porHora.values().stream().mapToLong(Long::longValue).max().orElse(1);
        for (var e : porHora.entrySet()) {

            int hora = e.getKey();
            String rango = String.format("%02d:00 - %02d:00", hora, (hora + 1) % 24);
            rep.getHorasPico().add(new FilaHora(rango, e.getValue(),
                    (int) (e.getValue() * 100 / max)));
        }
        return rep;
    }

    public List<FilaMovimiento> construirFilas(List<Marcacion> visibles) {
        List<FilaMovimiento> filas = new ArrayList<>();
        Map<String, String[]> cacheDia = new HashMap<>();
        for (Marcacion m : visibles) {
            LocalDate fecha = m.getTurno().getFecha();
            String clave = m.getResidente().getIdResidente() + ":" + fecha;
            String[] dia = cacheDia.computeIfAbsent(clave, k -> miniDia(m.getResidente(), fecha));
            filas.add(new FilaMovimiento(m, dia[0], dia[1], dia[2]));
        }
        return filas;
    }

    private String[] miniDia(Residente r, LocalDate fecha) {
        List<Marcacion> marcas = marcacionRepo.findByResidenteIdResidenteAndFechaHoraBetween(
                r.getIdResidente(), fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay());
        String[] res = new String[3];
        LocalDate hoy = LocalDate.now();
        int i = 0;
        for (String tipo : TurnoService.TIPOS) {
            if (fecha.isAfter(hoy)) { res[i++] = ""; continue; }
            FilaDia tmp = new FilaDia();
            res[i++] = estadoComida(r, marcas, fecha, tipo, tmp);
        }
        return res;
    }

    public ReporteIndividual individual(Residente residente, LocalDate desde, LocalDate hasta) {

        if (residente.getFechaIngreso() != null && desde.isBefore(residente.getFechaIngreso())) {
            desde = residente.getFechaIngreso();
        }
        LocalDate hoy = LocalDate.now();
        if (hasta.isAfter(hoy)) hasta = hoy;
        if (residente.getFechaFinEstancia() != null && hasta.isAfter(residente.getFechaFinEstancia())) {
            hasta = residente.getFechaFinEstancia();
        }
        if (hasta.isBefore(desde)) hasta = desde;

        ReporteIndividual rep = new ReporteIndividual();
        rep.setResidente(residente);
        rep.setDesde(desde);
        rep.setHasta(hasta);

        List<Marcacion> marcaciones = marcacionRepo.findByResidenteIdResidenteAndFechaHoraBetween(
                residente.getIdResidente(), desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());

        rep.setInfracciones(marcaciones.stream()
                .filter(m -> Boolean.TRUE.equals(m.getAnulada()))
                .toList());

        for (LocalDate f = desde; !f.isAfter(hasta); f = f.plusDays(1)) {
            FilaDia fila = new FilaDia();
            fila.setFecha(f);
            String dia = f.getDayOfWeek().getDisplayName(TextStyle.FULL, ES);
            fila.setDia(dia.substring(0, 1).toUpperCase() + dia.substring(1));

            if (!f.isAfter(hoy)) {
                fila.setDesayuno(estadoComida(residente, marcaciones, f, "DESAYUNO", fila));
                fila.setAlmuerzo(estadoComida(residente, marcaciones, f, "ALMUERZO", fila));
                fila.setCena(estadoComida(residente, marcaciones, f, "CENA", fila));

                long si = contar(fila, "SI");
                long just = contar(fila, "JUST");
                long no = contar(fila, "NO");
                rep.setAsistencias(rep.getAsistencias() + si);
                rep.setJustificadas(rep.getJustificadas() + just);
                rep.setInjustificadas(rep.getInjustificadas() + no);

                rep.setTotalComidas(rep.getTotalComidas() + si + just + no);

                if (no > 0) {
                    fila.setInjustificada(true);
                    agregarObservacion(fila, "Inasistencia injustificada");
                }
            }
            rep.getFilas().add(fila);
        }
        agruparSemanas(rep);
        return rep;
    }

    private void agruparSemanas(ReporteIndividual rep) {
        List<FilaDia> pendientes = new ArrayList<>();
        int numero = 1;
        for (FilaDia f : rep.getFilas()) {
            pendientes.add(f);
            boolean cierraSemana = f.getFecha().getDayOfWeek() == java.time.DayOfWeek.SUNDAY
                    || f == rep.getFilas().get(rep.getFilas().size() - 1);
            if (cierraSemana) {
                long si = 0, just = 0, no = 0, total = 0;
                for (FilaDia d : pendientes) {
                    long dSi = contar(d, "SI"), dJust = contar(d, "JUST"), dNo = contar(d, "NO");
                    si += dSi;
                    just += dJust;
                    no += dNo;

                    total += dSi + dJust + dNo;
                }
                String rango = FECHA_CORTA.format(pendientes.get(0).getFecha()) + " – "
                        + FECHA_CORTA.format(pendientes.get(pendientes.size() - 1).getFecha());
                rep.getSemanas().add(new FilaSemana("Semana " + numero++, rango, si, just, no, total));
                pendientes.clear();
            }
        }
    }

    private String estadoComida(Residente residente, List<Marcacion> marcaciones, LocalDate fecha,
                                String tipo, FilaDia fila) {

        if (residente.getFechaIngreso() != null && fecha.isBefore(residente.getFechaIngreso())) {
            return "PEND";
        }

        var asistio = marcaciones.stream()
                .filter(m -> "PERMITIDO".equals(m.getEstado())
                        && vigente(m)
                        && tipo.equals(m.getTurno().getTipo())
                        && fecha.equals(m.getTurno().getFecha()))
                .findFirst();
        if (asistio.isPresent()) {

            if (asistio.get().getFechaHora() != null) {
                setHoraComida(fila, tipo, HORA.format(asistio.get().getFechaHora()));
            }
            return "SI";
        }

        var just = justificacionService.buscar(residente, fecha, tipo);
        if (just.isPresent()) {
            agregarObservacion(fila, just.get().getMotivo());
            setMotivoComida(fila, tipo, just.get().getMotivo());
            return "JUST";
        }

        if (!turnoService.turnoYaOcurrio(tipo, fecha)) return "PEND";

        if (comioEnUnEvento(residente, fecha, tipo)) {
            agregarObservacion(fila, "Comió en un evento");
            setMotivoComida(fila, tipo, "Comió en un evento especial");
            return "JUST";
        }
        return "NO";
    }

    private boolean comioEnUnEvento(Residente residente, LocalDate fecha, String tipo) {
        if (eventoRepo == null) return false;
        for (var ev : eventoRepo.findByEstadoAndFechaEvento("APROBADO", fecha)) {
            if (!ev.sustituye(tipo)) continue;
            if (ev.getExcluidosLista().contains(residente.getCodigoAcceso())) continue;
            return true;
        }
        return false;
    }

    private com.upeu.comedorupeu.repository.EventoEspecialRepository eventoRepo;

    @org.springframework.beans.factory.annotation.Autowired
    public void setEventoRepo(com.upeu.comedorupeu.repository.EventoEspecialRepository eventoRepo) {
        this.eventoRepo = eventoRepo;
    }

    private void setHoraComida(FilaDia fila, String tipo, String hora) {
        if ("DESAYUNO".equals(tipo)) fila.setHoraDesayuno(hora);
        else if ("ALMUERZO".equals(tipo)) fila.setHoraAlmuerzo(hora);
        else fila.setHoraCena(hora);
    }

    private void setMotivoComida(FilaDia fila, String tipo, String motivo) {
        if ("DESAYUNO".equals(tipo)) fila.setMotivoDesayuno(motivo);
        else if ("ALMUERZO".equals(tipo)) fila.setMotivoAlmuerzo(motivo);
        else fila.setMotivoCena(motivo);
    }

    private void agregarObservacion(FilaDia fila, String texto) {
        if (texto == null || texto.isBlank()) return;
        String actual = fila.getObservacion();
        if (actual.contains(texto)) return;
        fila.setObservacion(actual.isEmpty() ? texto : actual + " · " + texto);
    }

    private long contar(FilaDia fila, String estado) {
        long c = 0;
        if (estado.equals(fila.getDesayuno())) c++;
        if (estado.equals(fila.getAlmuerzo())) c++;
        if (estado.equals(fila.getCena())) c++;
        return c;
    }
}
