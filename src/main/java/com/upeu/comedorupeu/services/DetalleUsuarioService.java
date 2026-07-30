package com.upeu.comedorupeu.services;

import com.upeu.comedorupeu.models.Usuario;
import com.upeu.comedorupeu.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Primary
public class DetalleUsuarioService implements UserDetailsService {
    @Autowired
    private UsuarioRepository repo;

    @Autowired
    private IntentosLoginService intentos;

    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (intentos.estaBloqueado(username)) {
            throw new org.springframework.security.authentication.LockedException(
                    "Cuenta bloqueada temporalmente por demasiados intentos fallidos");
        }
        Usuario u = repo.findByCorreo(username);
        if (u == null) throw new UsernameNotFoundException("Usuario no encontrado");

        if (u.getActivo() != null && !u.getActivo()) throw new UsernameNotFoundException("Cuenta desactivada");

        return User.withUsername(u.getCorreo())
                .password(u.getClave())
                .roles(u.getRol())
                .build();
    }
}
