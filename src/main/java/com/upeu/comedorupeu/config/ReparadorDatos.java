package com.upeu.comedorupeu.config;

import com.upeu.comedorupeu.models.Marcacion;
import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.repository.MarcacionRepository;
import com.upeu.comedorupeu.repository.ResidenteRepository;
import com.upeu.comedorupeu.services.ImagenService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(2)
public class ReparadorDatos implements CommandLineRunner {

    private final ResidenteRepository residenteRepo;
    private final MarcacionRepository marcacionRepo;
    private final ImagenService imagenService;

    private com.upeu.comedorupeu.services.CarrerasService carrerasService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setCarrerasService(com.upeu.comedorupeu.services.CarrerasService carrerasService) {
        this.carrerasService = carrerasService;
    }

    public ReparadorDatos(ResidenteRepository residenteRepo, MarcacionRepository marcacionRepo,
                          ImagenService imagenService) {
        this.residenteRepo = residenteRepo;
        this.marcacionRepo = marcacionRepo;
        this.imagenService = imagenService;
    }

    private com.upeu.comedorupeu.repository.ProgramacionHorarioRepository horarioRepo;

    @org.springframework.beans.factory.annotation.Autowired
    public void setHorarioRepo(com.upeu.comedorupeu.repository.ProgramacionHorarioRepository horarioRepo) {
        this.horarioRepo = horarioRepo;
    }

    @Override
    public void run(String... args) {
        revisarCajerosDelHorario();

        int evidencias = imagenService.encogerEvidencias();
        if (evidencias > 0) {
            System.out.println(">> " + evidencias + " evidencia(s) de ausencia o dieta se comprimieron.");
        }

        List<Residente> todos = residenteRepo.findAll();
        if (todos.isEmpty()) return;

        sellarFechasDeRegistro(todos);
        renombrarFotos(todos);
        encogerFotosPesadas(todos);
        normalizarCarreras(todos);
    }

    private void revisarCajerosDelHorario() {
        if (horarioRepo == null) return;
        java.util.List<String> huerfanas = new java.util.ArrayList<>();

        for (var celda : horarioRepo.findByObjetivo(com.upeu.comedorupeu.services.AgendaService.CELDA)) {
            if (celda.getFecha() != null) continue;
            if (!Boolean.TRUE.equals(celda.getActivo())) continue;
            if (celda.getHoraInicio() == null || celda.getHoraFin() == null) continue;
            if (celda.getCajero() != null) continue;
            if (celda.getPunto() != null && celda.getPunto().getCajero() != null) continue;

            huerfanas.add(nombreDia(celda.getDiaSemana()) + " "
                    + (celda.getTipoTurno() == null ? "?" : celda.getTipoTurno()) + " "
                    + celda.getRangoTexto() + " en "
                    + (celda.getPunto() == null ? "(punto borrado)" : celda.getPunto().getNombre()));
        }

        if (huerfanas.isEmpty()) return;

        System.out.println(">> OJO: " + huerfanas.size() + " casilla(s) del horario base abren el punto "
                + "sin ningún cajero asignado, así que ese turno queda sin quien atienda:");
        for (String h : huerfanas) {
            System.out.println(">>   " + h);
        }
        System.out.println(">>   Entra a Programar, elige ese día y asígnale personal. "
                + "No se tocó ningún dato: esto es solo un aviso.");
    }

    private String nombreDia(Integer dia) {
        if (dia == null || dia < 1 || dia > 7) return "(día ?)";
        return new String[]{"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"}[dia - 1];
    }

    private void normalizarCarreras(List<Residente> todos) {
        if (carrerasService == null) {
            System.out.println(">> Revisión de carreras omitida: el catálogo no estaba disponible.");
            return;
        }
        java.util.List<String> catalogo = carrerasService.todas();
        System.out.println(">> Revisando carreras de " + todos.size() + " residente(s) contra "
                + catalogo.size() + " del catálogo.");
        int corregidos = 0;
        java.util.List<String> sinReconocer = new java.util.ArrayList<>();

        for (Residente r : todos) {
            String actual = r.getCarrera();
            if (actual == null || actual.isBlank()) continue;
            if (catalogo.contains(actual)) continue;

            String reconocida = carrerasService.reconocer(actual);
            if (reconocida != null) {
                System.out.println(">> Carrera corregida: \"" + actual + "\" -> \"" + reconocida
                        + "\" (" + r.getCodigoAcceso() + ")");
                r.setCarrera(reconocida);
                residenteRepo.save(r);
                corregidos++;
            } else {
                sinReconocer.add(r.getCodigoAcceso() + ": " + actual);
            }
        }

        if (corregidos > 0) {
            System.out.println(">> " + corregidos + " residente(s) tenían la carrera escrita de otra forma "
                    + "y se ajustaron al catálogo del sistema.");
        }
        if (!sinReconocer.isEmpty()) {
            System.out.println(">> OJO: estas carreras no se pudieron reconocer y quedan sin marcar "
                    + "al editar al residente: " + String.join(" | ", sinReconocer));
        }
    }

    private void sellarFechasDeRegistro(List<Residente> todos) {
        int sellados = 0;
        int corregidos = 0;
        int sinEvidencia = 0;

        for (Residente r : todos) {
            if (r.getFechaRegistro() != null) continue;

            LocalDateTime evidencia = marcacionRepo
                    .findFirstByResidenteIdResidenteOrderByFechaHoraAsc(r.getIdResidente())
                    .map(Marcacion::getFechaHora)
                    .orElse(null);

            boolean sospechosa = pareceFechaDeSemestre(r.getFechaIngreso());

            if (sospechosa && evidencia != null && evidencia.toLocalDate().isAfter(r.getFechaIngreso())) {
                r.setFechaIngreso(evidencia.toLocalDate());
                r.setFechaRegistro(evidencia);
                corregidos++;
            } else {
                r.setFechaRegistro(r.getFechaIngreso() != null
                        ? r.getFechaIngreso().atStartOfDay()
                        : LocalDateTime.now());
                if (sospechosa) sinEvidencia++;
            }

            residenteRepo.save(r);
            sellados++;
        }

        if (sellados == 0) return;

        System.out.println(">> REPARADOR: " + sellados + " residente(s) recibieron su fecha de registro.");
        if (corregidos > 0) {
            System.out.println(">>   " + corregidos + " tenían fecha de inicio de semestre y se corrigieron "
                    + "con la fecha de su primera comida registrada.");
        }
        if (sinEvidencia > 0) {
            System.out.println(">>   " + sinEvidencia + " siguen con fecha de inicio de semestre: nunca comieron, "
                    + "así que no hay ningún dato para deducir cuándo se registraron. Se dejaron intactos.");
        }
    }

    private void encogerFotosPesadas(List<Residente> todos) {
        int encogidas = 0;
        long antes = 0, despues = 0;

        for (Residente r : todos) {
            if (r.getFotoUrl() == null || r.getFotoUrl().isBlank()) continue;
            try {
                long pesaba = pesoDe(r.getFotoUrl());
                String nueva = imagenService.encoger(r);
                if (nueva == null) continue;

                r.setFotoUrl(nueva);
                residenteRepo.save(r);
                encogidas++;
                antes += pesaba;
                despues += pesoDe(nueva);
            } catch (Exception e) {
                System.out.println(">> No se pudo comprimir la foto de " + r.getNombreCompleto()
                        + " (" + e.getMessage() + ")");
            }
        }

        if (encogidas > 0) {
            System.out.println(">> " + encogidas + " foto(s) de residente se ajustaron: "
                    + com.upeu.comedorupeu.services.ImagenService.enKb(antes) + " -> "
                    + com.upeu.comedorupeu.services.ImagenService.enKb(despues) + ".");
        }

    }

    private long pesoDe(String url) {
        if (url == null || url.isBlank()) return 0;
        try {
            java.nio.file.Path archivo = java.nio.file.Paths.get(carpetaFotos).toAbsolutePath()
                    .resolve(url.substring(url.lastIndexOf('/') + 1));
            return java.nio.file.Files.exists(archivo) ? java.nio.file.Files.size(archivo) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @org.springframework.beans.factory.annotation.Value("${app.upload.dir}")
    private String carpetaFotos;

    private void renombrarFotos(List<Residente> todos) {
        int renombradas = 0;
        int fallidas = 0;

        for (Residente r : todos) {
            if (r.getFotoUrl() == null || r.getFotoUrl().isBlank()) continue;
            try {
                String nueva = imagenService.corregirNombre(r);
                if (nueva == null) continue;
                r.setFotoUrl(nueva);
                residenteRepo.save(r);
                renombradas++;
            } catch (Exception e) {
                fallidas++;
                System.out.println(">> REPARADOR: no se pudo renombrar la foto de "
                        + r.getNombreCompleto() + " (" + e.getMessage() + ")");
            }
        }

        if (renombradas > 0) {
            System.out.println(">> REPARADOR: " + renombradas + " foto(s) renombradas con el nombre de su residente.");
        }
        if (fallidas > 0) {
            System.out.println(">> REPARADOR: " + fallidas + " foto(s) no se pudieron renombrar.");
        }
    }

    private boolean pareceFechaDeSemestre(LocalDate fecha) {
        return fecha != null && fecha.getDayOfMonth() == 1
                && (fecha.getMonthValue() == 1 || fecha.getMonthValue() == 7);
    }
}
