package com.upeu.comedorupeu.services;

import com.upeu.comedorupeu.models.Residente;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;

@Service
public class ImagenService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    private static final int LADO_FOTO = 800;
    private static final int LADO_EVIDENCIA = 1600;
    private static final long PESO_ACEPTABLE = 200L * 1024L;
    private static final float CALIDAD = 0.82f;

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

        byte[] listos = comprimir(datos, ext, LADO_FOTO);
        String extFinal = (listos == datos && !yaEsJpeg(listos)) ? ext : "jpg";

        String nombre = nombreLibre(dir, residente, extFinal, null);
        Files.write(dir.resolve(nombre), listos);
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

        byte[] listos = comprimir(datos, ext, LADO_EVIDENCIA);
        String extFinal = (listos == datos) ? ext : "jpg";

        String nombre = prefijo + "_" + System.currentTimeMillis() + "." + extFinal;
        Files.write(dir.resolve(nombre), listos);
        return "/uploads/" + nombre;
    }

    public byte[] comprimir(byte[] datos, String ext, int ladoMaximo) {
        if (datos == null || datos.length == 0) return datos;
        if ("webp".equals(ext)) return datos;

        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(datos));
            if (original == null) return datos;

            int ancho = original.getWidth();
            int alto = original.getHeight();
            int lado = Math.max(ancho, alto);

            boolean cabeDeLado = lado <= ladoMaximo;
            if (cabeDeLado && yaEsJpeg(datos)) return datos;
            if (cabeDeLado && datos.length <= PESO_ACEPTABLE) return datos;

            double escala = (lado > ladoMaximo) ? (double) ladoMaximo / lado : 1.0;
            int nuevoAncho = Math.max(1, (int) Math.round(ancho * escala));
            int nuevoAlto = Math.max(1, (int) Math.round(alto * escala));

            BufferedImage destino = new BufferedImage(nuevoAncho, nuevoAlto, BufferedImage.TYPE_INT_RGB);
            Graphics2D lienzo = destino.createGraphics();
            lienzo.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            lienzo.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            lienzo.setColor(Color.WHITE);
            lienzo.fillRect(0, 0, nuevoAncho, nuevoAlto);
            lienzo.drawImage(original, 0, 0, nuevoAncho, nuevoAlto, null);
            lienzo.dispose();

            byte[] salida = aJpeg(destino);
            return (salida != null && salida.length > 0 && salida.length < datos.length) ? salida : datos;
        } catch (Exception e) {
            return datos;
        }
    }

    private boolean yaEsJpeg(byte[] datos) {
        return datos != null && datos.length > 3
                && (datos[0] & 0xFF) == 0xFF && (datos[1] & 0xFF) == 0xD8 && (datos[2] & 0xFF) == 0xFF;
    }

    private byte[] aJpeg(BufferedImage imagen) throws IOException {
        var escritores = ImageIO.getImageWritersByFormatName("jpg");
        if (!escritores.hasNext()) return null;

        ImageWriter escritor = escritores.next();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (var destino = ImageIO.createImageOutputStream(salida)) {
            escritor.setOutput(destino);
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            if (parametros.canWriteCompressed()) {
                parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parametros.setCompressionQuality(CALIDAD);
            }
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }

    public String encoger(Residente residente) throws IOException {
        String url = residente.getFotoUrl();
        if (url == null || url.isBlank()) return null;

        Path dir = Paths.get(uploadDir).toAbsolutePath();
        String actual = url.substring(url.lastIndexOf('/') + 1);
        Path archivo = dir.resolve(actual);
        if (!Files.exists(archivo)) return null;

        byte[] datos = Files.readAllBytes(archivo);
        byte[] listos = comprimir(datos, obtenerExtension(actual), LADO_FOTO);

        String sinExtension = actual.contains(".") ? actual.substring(0, actual.lastIndexOf('.')) : actual;
        String nombre = sinExtension + ".jpg";
        boolean cambiaNombre = yaEsJpeg(listos) && !nombre.equals(actual);
        if (listos == datos && !cambiaNombre) return null;

        Files.write(dir.resolve(nombre), listos);
        if (!nombre.equals(actual)) Files.deleteIfExists(archivo);
        return "/uploads/" + nombre;
    }

    public static String enKb(long bytes) {
        return Math.max(1L, Math.round(bytes / 1024.0)) + " KB";
    }

    public int encogerEvidencias() {
        Path dir = Paths.get(uploadDir).toAbsolutePath();
        if (!Files.isDirectory(dir)) return 0;

        int tocadas = 0;
        try (var archivos = Files.list(dir)) {
            for (Path archivo : archivos.toList()) {
                String nombre = archivo.getFileName().toString();
                if (!nombre.startsWith("ausencia_") && !nombre.startsWith("dieta_")) continue;

                byte[] datos = Files.readAllBytes(archivo);
                byte[] listos = comprimir(datos, obtenerExtension(nombre), LADO_EVIDENCIA);
                if (listos == datos) continue;

                Files.write(archivo, listos);
                tocadas++;
            }
        } catch (IOException e) {
            System.out.println(">> No se pudieron revisar las evidencias: " + e.getMessage());
        }
        return tocadas;
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
