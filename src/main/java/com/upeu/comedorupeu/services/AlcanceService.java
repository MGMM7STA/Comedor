package com.upeu.comedorupeu.services;

import com.upeu.comedorupeu.models.Usuario;
import com.upeu.comedorupeu.repository.AusenciaRepository;
import com.upeu.comedorupeu.repository.ResidenteRepository;
import com.upeu.comedorupeu.repository.SolicitudExtemporaneaRepository;
import com.upeu.comedorupeu.repository.UsuarioRepository;
import com.upeu.comedorupeu.services.alcance.AlcanceDatos;
import com.upeu.comedorupeu.services.alcance.AlcancePorResidencia;
import com.upeu.comedorupeu.services.alcance.AlcanceTotal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AlcanceService {

    private final UsuarioRepository usuarioRepo;
    private final ResidenteRepository residenteRepo;
    private final SolicitudExtemporaneaRepository solicitudRepo;
    private final AusenciaRepository ausenciaRepo;

    public AlcanceService(UsuarioRepository usuarioRepo,
                          ResidenteRepository residenteRepo,
                          SolicitudExtemporaneaRepository solicitudRepo,
                          AusenciaRepository ausenciaRepo) {
        this.usuarioRepo = usuarioRepo;
        this.residenteRepo = residenteRepo;
        this.solicitudRepo = solicitudRepo;
        this.ausenciaRepo = ausenciaRepo;
    }

    public AlcanceDatos de(Authentication auth) {
        Usuario u = (auth == null) ? null : usuarioRepo.findByCorreo(auth.getName());
        if (u != null && "PRECEPTOR".equals(u.getRol()) && u.getPabellon() != null) {
            return new AlcancePorResidencia(u.getPabellon(), residenteRepo, solicitudRepo, ausenciaRepo);
        }
        return new AlcanceTotal(residenteRepo, solicitudRepo, ausenciaRepo);
    }

    public AlcanceDatos deConFiltro(Authentication auth, String residenciaElegida) {
        AlcanceDatos propio = de(auth);

        if (propio.residenciaGenero() != null) return propio;
        if (residenciaElegida == null || residenciaElegida.isBlank() || "TODOS".equalsIgnoreCase(residenciaElegida)) {
            return propio;
        }
        return new AlcancePorResidencia(residenciaElegida, residenteRepo, solicitudRepo, ausenciaRepo);
    }

    public boolean estaBloqueadoPorResidencia(Authentication auth) {
        return de(auth).residenciaGenero() != null;
    }

    public AlcanceDatos porResidencia(String residencia) {
        if (residencia == null || residencia.isBlank()) {
            return new AlcanceTotal(residenteRepo, solicitudRepo, ausenciaRepo);
        }
        return new AlcancePorResidencia(residencia, residenteRepo, solicitudRepo, ausenciaRepo);
    }
}
