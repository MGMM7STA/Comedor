package com.upeu.comedorupeu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication

@org.springframework.scheduling.annotation.EnableScheduling
public class ComedorUpeuApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComedorUpeuApplication.class, args);
    }

}
