package com.upeu.comedorupeu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   com.upeu.comedorupeu.services.IntentosLoginService intentos) throws Exception {
        http
                .authorizeHttpRequests((requests) -> requests

                        .requestMatchers("/css/**", "/js/**", "/images/**", "/*.jpg", "/*.png", "/*.gif", "/*.js", "/*.css", "/*.svg").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/padres/**").permitAll()

                        .requestMatchers("/login-error", "/login-bloqueado").permitAll()
                        .requestMatchers("/admin/justificaciones/**").hasAnyRole("ADMIN", "PRECEPTOR")
                        .requestMatchers("/admin/reservas/**").hasAnyRole("ADMIN", "PRECEPTOR")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/cajero/**").hasAnyRole("CAJERO", "ADMIN")
                        .requestMatchers("/preceptor/**").hasAnyRole("PRECEPTOR", "ADMIN")
                        .requestMatchers("/reportes/**").hasAnyRole("ADMIN", "PRECEPTOR")
                        .requestMatchers("/movimientos/**").hasAnyRole("ADMIN", "PRECEPTOR")
                        .anyRequest().authenticated()
                )
                .formLogin((form) -> form
                        .loginPage("/login")

                        .successHandler((request, response, authentication) -> {
                            intentos.limpiar(authentication.getName());
                            response.sendRedirect("/");
                        })

                        .failureHandler((request, response, exception) -> {
                            String correo = request.getParameter("username");
                            boolean recienBloqueado = intentos.registrarFallo(correo);
                            boolean bloqueado = recienBloqueado || intentos.estaBloqueado(correo);

                            response.sendRedirect(bloqueado ? "/login-bloqueado" : "/login-error");
                        })
                        .permitAll()
                )
                .logout((logout) -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
