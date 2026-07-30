package com.upeu.comedorupeu.services;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IntentosLoginService {

    private static final int MAX_INTENTOS = 5;
    private static final int MINUTOS_BLOQUEO = 5;

    private final Map<String, Integer> fallos = new ConcurrentHashMap<>();

    private final Map<String, LocalDateTime> bloqueadosHasta = new ConcurrentHashMap<>();

    private String clave(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase();
    }

    public boolean registrarFallo(String correo) {
        if (correo == null || correo.isBlank()) return false;

        if (fallos.size() > 10000) fallos.clear();
        bloqueadosHasta.values().removeIf(h -> LocalDateTime.now().isAfter(h));
        int total = fallos.merge(clave(correo), 1, Integer::sum);
        if (total >= MAX_INTENTOS) {
            bloqueadosHasta.put(clave(correo), LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEO));
            fallos.remove(clave(correo));
            return true;
        }
        return false;
    }

    public boolean estaBloqueado(String correo) {
        LocalDateTime hasta = bloqueadosHasta.get(clave(correo));
        if (hasta == null) return false;
        if (LocalDateTime.now().isAfter(hasta)) {
            bloqueadosHasta.remove(clave(correo));
            return false;
        }
        return true;
    }

    public void limpiar(String correo) {
        fallos.remove(clave(correo));
        bloqueadosHasta.remove(clave(correo));
    }
}
