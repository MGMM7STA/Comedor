package com.upeu.comedorupeu.config;

import com.upeu.comedorupeu.models.*;
import com.upeu.comedorupeu.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepo;
    private final PasswordEncoder encoder;

    @org.springframework.beans.factory.annotation.Value("${app.admin.correo}")
    private String adminCorreo;
    @org.springframework.beans.factory.annotation.Value("${app.admin.clave}")
    private String adminClave;

    @org.springframework.beans.factory.annotation.Value("${app.admin.reset:false}")
    private boolean adminReset;

    public DataSeeder(UsuarioRepository usuarioRepo, PasswordEncoder encoder) {
        this.usuarioRepo = usuarioRepo;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {

        Usuario adminExistente = usuarioRepo.findByCorreo(adminCorreo);

        if (adminReset && adminExistente != null) {
            adminExistente.setClave(encoder.encode(adminClave));
            usuarioRepo.save(adminExistente);
            System.out.println(">> ADMIN_RESET activo: la contraseña de " + adminCorreo + " se restableció.");
        }

        crearUsuario("Admin", "General", adminCorreo, adminClave, "ADMIN", "USR-2024-001", null);
    }

    private Usuario crearUsuario(String nombre, String apellido, String correo, String clave, String rol,
                                 String codigo, String pabellon) {
        Usuario existente = usuarioRepo.findByCorreo(correo);
        if (existente != null) return existente;
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setApellido(apellido);
        u.setCorreo(correo);
        u.setClave(encoder.encode(clave));
        u.setRol(rol);
        u.setCodigoUsuario(codigo);
        u.setPabellon(pabellon);
        u.setActivo(true);
        return usuarioRepo.save(u);
    }
}
