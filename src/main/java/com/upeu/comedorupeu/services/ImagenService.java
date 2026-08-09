package com.upeu.comedorupeu.services;

import com.upeu.comedorupeu.models.Residente;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;

@Service
public class ImagenService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    public String guardar(MultipartFile file, Residente residente) throws IOException {
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

        borrarAnterior(dir, residente);

        String nombre = nombreLibre(dir, residente, ext, null);
        Files.write(dir.resolve(nombre), datos);
        return "/uploads/" + nombre;
    }

    public String guardarEvidencia(MultipartFile file, String prefijo) throws IOException {
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

        String nombre = prefijo + "_" + System.currentTimeMillis() + "." + ext;
        Files.write(dir.resolve(nombre), datos);
        return "/uploads/" + nombre;
    }

    public String corregirNombre(Residente residente) throws IOException {
        String url = residente.getFotoUrl();
        if (url == null || url.isBlank()) return null;

        String actual = url.substring(url.lastIndexOf('/') + 1);
        String ext = obtenerExtension(actual);
        if (ext.isBlank()) return null;

        Path dir = Paths.get(uploadDir).toAbsolutePath();
        Path origen = dir.resolve(actual);
        if (!Files.exists(origen)) return null;

        String esperado = nombreLibre(dir, residente, ext, actual);
        if (esperado.equals(actual)) return null;

        Files.move(origen, dir.resolve(esperado));
        return "/uploads/" + esperado;
    }

    public static String nombreBase(Residente residente) {
        String nombre = limpiar(primeraPalabra(residente.getNombre()));
        String apellido = limpiar(primeraPalabra(residente.getApellido()));
        if (nombre.isBlank() && apellido.isBlank()) return "residente";
        if (nombre.isBlank()) return apellido;
        if (apellido.isBlank()) return nombre;
        return nombre + "_" + apellido;
    }

    private String nombreLibre(Path dir, Residente residente, String ext, String archivoPropio) {
        String base = nombreBase(residente);
        String candidato = base + "." + ext;
        if (candidato.equals(archivoPropio) || !Files.exists(dir.resolve(candidato))) return candidato;

        String codigo = limpiar(residente.getCodigoAcceso());
        return base + "_" + (codigo.isBlank() ? String.valueOf(residente.getIdResidente()) : codigo) + "." + ext;
    }

    private void borrarAnterior(Path dir, Residente residente) {
        String url = residente.getFotoUrl();
        if (url == null || url.isBlank()) return;
        try {
            Files.deleteIfExists(dir.resolve(url.substring(url.lastIndexOf('/') + 1)));
        } catch (IOException e) {
            System.out.println(">> No se pudo borrar la foto anterior de " + residente.getNombreCompleto());
        }
    }

    private static String primeraPalabra(String texto) {
        if (texto == null) return "";
        String limpio = texto.trim();
        if (limpio.isEmpty()) return "";
        int espacio = limpio.indexOf(' ');
        return espacio < 0 ? limpio : limpio.substring(0, espacio);
    }

    private static String limpiar(String texto) {
        if (texto == null) return "";
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "");
        return sinTildes.toLowerCase().replaceAll("[^a-z0-9]", "");
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
