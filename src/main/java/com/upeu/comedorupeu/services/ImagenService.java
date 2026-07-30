package com.upeu.comedorupeu.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class ImagenService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    public String guardar(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return null;

        String ext = obtenerExtension(file.getOriginalFilename());
        if (!ext.matches("jpg|jpeg|png|webp")) {
            throw new IllegalArgumentException("Solo se permiten imágenes JPG, PNG o WEBP.");
        }

        byte[] datos = file.getBytes();
        if (!pareceImagen(datos)) {
            throw new IllegalArgumentException("El archivo no es una imagen válida: elige una foto JPG, PNG o WEBP.");
        }

        Path dir = Paths.get(uploadDir).toAbsolutePath();
        Files.createDirectories(dir);

        String nombre = UUID.randomUUID() + "." + ext;
        Files.write(dir.resolve(nombre), datos);
        return "/uploads/" + nombre;
    }

    private boolean pareceImagen(byte[] datos) {
        if (datos == null || datos.length < 12) return false;

        if ((datos[0] & 0xFF) == 0x89 && datos[1] == 'P' && datos[2] == 'N' && datos[3] == 'G') return true;

        if ((datos[0] & 0xFF) == 0xFF && (datos[1] & 0xFF) == 0xD8 && (datos[2] & 0xFF) == 0xFF) return true;

        return datos[0] == 'R' && datos[1] == 'I' && datos[2] == 'F' && datos[3] == 'F'
                && datos[8] == 'W' && datos[9] == 'E' && datos[10] == 'B' && datos[11] == 'P';
    }

    private String obtenerExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
