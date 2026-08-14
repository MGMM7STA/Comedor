package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Usuario findByCorreo(String correo);


    List<Usuario> findByRol(String rol);

    List<Usuario> findByRolIn(List<String> roles);
}
