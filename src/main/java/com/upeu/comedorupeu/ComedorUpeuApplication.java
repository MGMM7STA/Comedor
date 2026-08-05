package com.upeu.comedorupeu;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication

@org.springframework.scheduling.annotation.EnableScheduling
public class ComedorUpeuApplication {

    @Value("${app.zona-horaria:America/Lima}")
    private String zonaHoraria;

    @PostConstruct
    public void fijarZonaHoraria() {
        TimeZone.setDefault(TimeZone.getTimeZone(zonaHoraria));
        System.out.println(">> Zona horaria fijada: " + zonaHoraria
                + " — fecha y hora del sistema: " + java.time.LocalDateTime.now());
    }

    public static void main(String[] args) {
        SpringApplication.run(ComedorUpeuApplication.class, args);
    }

}
