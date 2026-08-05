package com.upeu.comedorupeu.services;

import com.upeu.comedorupeu.dto.FilaDia;
import com.upeu.comedorupeu.dto.FilaMovimiento;
import com.upeu.comedorupeu.dto.ReporteGeneral;
import com.upeu.comedorupeu.dto.ReporteIndividual;
import com.upeu.comedorupeu.models.Apoderado;
import com.upeu.comedorupeu.models.Marcacion;
import com.upeu.comedorupeu.models.Residente;
import com.upeu.comedorupeu.models.Usuario;
import com.upeu.comedorupeu.repository.ApoderadoRepository;
import com.upeu.comedorupeu.repository.ResidenteRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelService {

    private static final String[] CABECERAS = {
            "Nombres", "Apellidos", "DNI", "Codigo", "Carrera", "Cuarto", "Celular",
            "Resp. Financiero", "Relacion", "DNI Resp.", "Telefono Resp.",
            "Inicio estancia (AAAA-MM-DD)", "Fin estancia (AAAA-MM-DD)", "Estado", "Estado de Pago"
    };

    private static final String[] ESTADOS = {"ACTIVO", "INACTIVO"};

    private static final String[] ESTADOS_PAGO = {"Al día", "Con deuda"};

    private final ResidenteRepository residenteRepo;
    private final ApoderadoRepository apoderadoRepo;

    private final CarrerasService carrerasService;

    public ExcelService(ResidenteRepository residenteRepo, ApoderadoRepository apoderadoRepo,
                        CarrerasService carrerasService) {
        this.residenteRepo = residenteRepo;
        this.apoderadoRepo = apoderadoRepo;
        this.carrerasService = carrerasService;
    }

    @Transactional
    public String importar(MultipartFile archivo, Usuario preceptor) throws IOException {
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || !nombre.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("El archivo debe ser un Excel (.xlsx).");
        }

        int importados = 0;
        int actualizados = 0;
        List<String> omitidos = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();

        try (Workbook wb = new XSSFWorkbook(archivo.getInputStream())) {
            Sheet hoja = wb.getSheetAt(0);
            int[] cols = columnasDeEstado(hoja, fmt);
            for (Row fila : hoja) {
                if (fila.getRowNum() == 0) continue;

                String nombres = celda(fmt, fila, 0);
                String apellidos = celda(fmt, fila, 1);
                String codigo = celda(fmt, fila, 3);
                if (nombres.isBlank() && apellidos.isBlank() && codigo.isBlank()) continue;

                if (codigo.isBlank()) {
                    omitidos.add("Fila " + (fila.getRowNum() + 1) + ": falta el código de residente, "
                            + "que es obligatorio para poder importar");
                    continue;
                }
                if (nombres.isBlank()) {
                    omitidos.add("Fila " + (fila.getRowNum() + 1) + ": falta el nombre");
                    continue;
                }
                String dniImportado = celda(fmt, fila, 2).trim();
                var porCodigo = residenteRepo.findByCodigoAcceso(codigo);
                var porDni = dniImportado.isEmpty()
                        ? java.util.Optional.<Residente>empty()
                        : residenteRepo.findFirstByDni(dniImportado);

                boolean actualizar = false;
                if (porCodigo.isPresent()) {
                    String dniGuardado = porCodigo.get().getDni();
                    boolean guardadoSinDni = dniGuardado == null || dniGuardado.isBlank();

                    if (guardadoSinDni) {

                        if (porDni.isPresent()
                                && !porDni.get().getIdResidente().equals(porCodigo.get().getIdResidente())) {
                            omitidos.add("Fila " + (fila.getRowNum() + 1) + ": el DNI " + dniImportado
                                    + " ya está registrado con OTRO código (" + porDni.get().getCodigoAcceso() + ")");
                            continue;
                        }
                        actualizar = true;
                    } else if (dniImportado.isEmpty()) {
                        omitidos.add("Fila " + (fila.getRowNum() + 1) + ": el código " + codigo
                                + " ya existe y ese residente tiene DNI registrado. Escribe también su DNI "
                                + "para poder actualizarlo");
                        continue;
                    } else if (dniGuardado.equals(dniImportado)) {
                        actualizar = true;
                    } else {
                        omitidos.add("Fila " + (fila.getRowNum() + 1) + ": el código " + codigo
                                + " ya existe pero con OTRO DNI (registrado " + dniGuardado
                                + ", en el archivo " + dniImportado + "). Revisa cuál de los dos está mal");
                        continue;
                    }
                } else if (porDni.isPresent()) {
                    omitidos.add("Fila " + (fila.getRowNum() + 1) + ": el DNI " + dniImportado
                            + " ya está registrado con OTRO código (" + porDni.get().getCodigoAcceso() + ")");
                    continue;
                }

                String largo = revisarLargos(fmt, fila);
                if (largo != null) {
                    omitidos.add("Fila " + (fila.getRowNum() + 1) + ": " + largo);
                    continue;
                }

                java.time.LocalDate hoy = java.time.LocalDate.now();
                java.time.LocalDate ingresoPrevio = actualizar ? porCodigo.get().getFechaIngreso() : null;
                java.time.LocalDate ingresoPorDefecto = (ingresoPrevio != null) ? ingresoPrevio : hoy;
                java.time.LocalDate ingresoFila = (cols[3] >= 0)
                        ? fechaDeCelda(fmt, fila, cols[3], ingresoPorDefecto) : ingresoPorDefecto;
                if (!actualizar && ingresoFila.isBefore(hoy)) {
                    omitidos.add("Fila " + (fila.getRowNum() + 1) + ": el inicio de estancia ("
                            + ingresoFila + ") es anterior a hoy. Un residente no puede ingresar en el pasado");
                    continue;
                }

                Residente r = actualizar ? porCodigo.get() : new Residente();
                r.setNombre(nombres);
                r.setApellido(apellidos);
                r.setDni(dniImportado.isEmpty() ? null : dniImportado);
                r.setCodigoAcceso(codigo);
                r.setCarrera(celda(fmt, fila, 4));

                r.setPabellon(preceptor != null ? preceptor.getPabellon() : null);
                r.setCuarto(celda(fmt, fila, 5));
                r.setCelular(celda(fmt, fila, 6));

                r.setFechaIngreso(ingresoFila);

                java.time.LocalDate finSemestre = ingresoFila.getMonthValue() >= 7
                        ? java.time.LocalDate.of(ingresoFila.getYear(), 12, 31)
                        : java.time.LocalDate.of(ingresoFila.getYear(), 6, 30);
                r.setFechaFinEstancia(cols[2] >= 0
                        ? fechaDeCelda(fmt, fila, cols[2], finSemestre)
                        : finSemestre);
                r.setEstado("INACTIVO".equalsIgnoreCase(celda(fmt, fila, cols[0])) ? "INACTIVO" : "ACTIVO");

                r.setDeuda(celda(fmt, fila, cols[1]).toLowerCase().contains("deuda"));
                if (r.getTokenAcceso() == null) r.setTokenAcceso(java.util.UUID.randomUUID().toString());
                if (r.getPreceptor() == null) r.setPreceptor(preceptor);

                String apoNombre = celda(fmt, fila, 7);
                if (!apoNombre.isBlank()) {
                    Apoderado a = (r.getApoderado() != null) ? r.getApoderado() : new Apoderado();
                    a.setNombre(apoNombre);
                    a.setRelacion(celda(fmt, fila, 8));
                    a.setDni(celda(fmt, fila, 9));
                    a.setTelefono(celda(fmt, fila, 10));
                    apoderadoRepo.save(a);
                    r.setApoderado(a);
                }
                residenteRepo.save(r);
                if (actualizar) actualizados++; else importados++;
            }
        }

        StringBuilder resumen = new StringBuilder("Importación completada: " + importados + " residente(s) registrados.");
        if (actualizados > 0) {
            resumen.append(" ").append(actualizados)
                    .append(" ya existían y se actualizaron con los datos del archivo.");
        }
        if (!omitidos.isEmpty()) {
            resumen.append(" Omitidos ").append(omitidos.size()).append(": ")
                    .append(String.join("; ", omitidos.subList(0, Math.min(5, omitidos.size()))));
            if (omitidos.size() > 5) resumen.append(" ...");
        }
        return resumen.toString();
    }

    private String revisarLargos(DataFormatter fmt, Row fila) {

        int[] columnas   = {  0,   1,   2,   3,   4,   5,   6,   7,   8,   9,  10 };
        String[] rotulos = { "Nombres", "Apellidos", "DNI", "Código", "Carrera", "Cuarto",
                             "Celular", "Resp. Financiero", "Relación", "DNI Resp.", "Teléfono Resp." };
        int[] maximos    = { 150, 150,  20,  50, 150,  50,  30, 150,  50,  20,  30 };
        for (int i = 0; i < columnas.length; i++) {
            String valor = celda(fmt, fila, columnas[i]);
            if (valor != null && valor.length() > maximos[i]) {
                return "\"" + rotulos[i] + "\" tiene " + valor.length()
                        + " caracteres (máximo " + maximos[i] + ")";
            }
        }
        return null;
    }

    public byte[] generarPlantilla() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet("Residentes");

            CellStyle estiloCabecera = wb.createCellStyle();
            Font fuente = wb.createFont();
            fuente.setBold(true);
            fuente.setColor(IndexedColors.WHITE.getIndex());
            estiloCabecera.setFont(fuente);
            estiloCabecera.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            estiloCabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row cab = hoja.createRow(0);
            for (int i = 0; i < CABECERAS.length; i++) {
                Cell c = cab.createCell(i);
                c.setCellValue(CABECERAS[i]);
                c.setCellStyle(estiloCabecera);
                hoja.setColumnWidth(i, 4500);
            }

            Sheet listas = wb.createSheet("Listas");
            int filaCarrera = 0;
            for (List<String> carrerasFacultad : carrerasService.facultades().values()) {
                for (String carrera : carrerasFacultad) {
                    listas.createRow(filaCarrera++).createCell(0).setCellValue(carrera);
                }
            }

            DataValidationHelper ayuda = hoja.getDataValidationHelper();

            DataValidation dvCarrera = ayuda.createValidation(
                    ayuda.createFormulaListConstraint("Listas!$A$1:$A$" + filaCarrera),
                    new org.apache.poi.ss.util.CellRangeAddressList(1, 500, 4, 4));
            dvCarrera.setShowErrorBox(true);
            hoja.addValidationData(dvCarrera);

            DataValidation dvEstado = ayuda.createValidation(
                    ayuda.createExplicitListConstraint(ESTADOS),
                    new org.apache.poi.ss.util.CellRangeAddressList(1, 500, 13, 13));
            dvEstado.setShowErrorBox(true);
            hoja.addValidationData(dvEstado);

            DataValidation dvPago = ayuda.createValidation(
                    ayuda.createExplicitListConstraint(ESTADOS_PAGO),
                    new org.apache.poi.ss.util.CellRangeAddressList(1, 500, 14, 14));
            dvPago.setShowErrorBox(true);
            hoja.addValidationData(dvPago);

            CellStyle estiloFecha = wb.createCellStyle();
            estiloFecha.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));
            hoja.setDefaultColumnStyle(11, estiloFecha);
            hoja.setDefaultColumnStyle(12, estiloFecha);

            DataValidation dvFechas = ayuda.createValidation(
                    ayuda.createDateConstraint(DataValidationConstraint.OperatorType.BETWEEN,
                            "DATE(2000,1,1)", "DATE(2100,12,31)", "yyyy-mm-dd"),
                    new org.apache.poi.ss.util.CellRangeAddressList(1, 500, 11, 12));
            dvFechas.setShowErrorBox(true);
            dvFechas.createErrorBox("Fecha inválida", "Escribe una fecha con formato AAAA-MM-DD.");
            hoja.addValidationData(dvFechas);

            wb.setSheetHidden(wb.getSheetIndex(listas), true);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private int[] columnasDeEstado(Sheet hoja, DataFormatter fmt) {
        Row cabecera = hoja.getRow(0);
        if (cabecera != null) {
            int estado = -1, pago = -1, fin = -1, ini = -1;
            for (int i = 0; i < 20; i++) {
                String texto = celda(fmt, cabecera, i).trim().toLowerCase();
                if (texto.startsWith("estado de pago")) pago = i;
                else if (texto.equals("estado")) estado = i;
                else if (texto.startsWith("fin estancia")) fin = i;
                else if (texto.startsWith("inicio estancia")) ini = i;
            }
            if (estado >= 0 && pago >= 0) return new int[]{estado, pago, fin, ini};
        }
        return new int[]{13, 14, 12, 11};
    }

    private java.time.LocalDate fechaDeCelda(DataFormatter fmt, Row fila, int i, java.time.LocalDate porDefecto) {
        String texto = celda(fmt, fila, i);
        if (texto.isBlank()) return porDefecto;
        try {
            return java.time.LocalDate.parse(texto);
        } catch (Exception e) {
            try {
                return java.time.LocalDate.parse(texto,
                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } catch (Exception e2) {
                return porDefecto;
            }
        }
    }

    public byte[] generarPlantillaLista() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet("Pase de Lista");
            CellStyle cab = estiloCabecera(wb);
            Row fila = hoja.createRow(0);
            Cell c = fila.createCell(0);
            c.setCellValue("Codigo");
            c.setCellStyle(cab);
            hoja.setColumnWidth(0, 5500);
            hoja.createRow(1).createCell(0).setCellValue("");

            wb.write(out);
            return out.toByteArray();
        }
    }

    public List<String> leerCodigos(MultipartFile archivo) throws IOException {
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || !nombre.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("El archivo debe ser un Excel (.xlsx).");
        }
        List<String> codigos = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = new XSSFWorkbook(archivo.getInputStream())) {
            for (Row fila : wb.getSheetAt(0)) {
                String cod = celda(fmt, fila, 0);
                if (!cod.isBlank() && !cod.equalsIgnoreCase("codigo") && !cod.equalsIgnoreCase("código")) {
                    codigos.add(cod);
                }
            }
        }
        return codigos;
    }

    private String celda(DataFormatter fmt, Row fila, int i) {
        Cell c = fila.getCell(i);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] exportarGeneral(ReporteGeneral rep, LocalDate desde, LocalDate hasta, String turno, String punto,
                                  String residencia,
                                  List<com.upeu.comedorupeu.models.SolicitudExtemporanea> reservas,
                                  List<com.upeu.comedorupeu.models.Ausencia> justificaciones,
                                  List<java.util.Map<String, Object>> eventos,
                                  boolean completo) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle cab = estiloCabecera(wb);
            CellStyle titulo = estiloTitulo(wb);
            String rango = FECHA.format(desde) + (desde.equals(hasta) ? "" : " al " + FECHA.format(hasta));
            String filtros = "Período: " + rango + "   Turno: " + turno + "   Punto: " + punto
                    + "   Residencia de Género: " + residencia;

            if (rep != null) {
                Sheet hoja = wb.createSheet("Normal");
                encabezadoHoja(hoja, titulo, cab, "COMEDOR UPEU — Asistencia (Normal)", filtros);

                String[] cabeceras = completo
                        ? new String[]{"N°", "Fecha", "Residente", "Código", "DNI", "Residencia de Género",
                                "Turno", "Hora", "Punto", "Estado", "Su día (D·A·C)", "Registró"}
                        : new String[]{"N°", "Fecha", "Código", "Residente", "Desayuno", "Almuerzo", "Cena"};
                escribirCabeceras(hoja, cab, cabeceras, 3);

                int fila = 4, n = 1;
                for (FilaMovimiento fm : rep.getFilas()) {
                    Marcacion m = fm.getMarcacion();
                    Row r = hoja.createRow(fila++);
                    if (completo) {

                        r.createCell(0).setCellValue(n++);
                        r.createCell(1).setCellValue(FECHA.format(m.getTurno().getFecha()));
                        r.createCell(2).setCellValue(m.getResidente().getApellido() + ", " + m.getResidente().getNombre());
                        r.createCell(3).setCellValue(m.getResidente().getCodigoAcceso());
                        r.createCell(4).setCellValue(m.getResidente().getDni() == null ? "—" : m.getResidente().getDni());
                        r.createCell(5).setCellValue(m.getResidente().getPabellon() == null ? "—" : m.getResidente().getPabellon());
                        r.createCell(6).setCellValue(capitalizar(m.getTurno().getTipo()));
                        r.createCell(7).setCellValue(HORA.format(m.getFechaHora()));
                        r.createCell(8).setCellValue(m.getPunto() == null ? "—" : m.getPunto().getNombre());

                        r.createCell(9).setCellValue(capitalizar(m.getEstado())
                                + (m.getIntentos() != null && m.getIntentos() > 1 ? " ×" + m.getIntentos() : ""));
                        r.createCell(10).setCellValue(marcaDia(fm.getDesayuno()) + " · "
                                + marcaDia(fm.getAlmuerzo()) + " · " + marcaDia(fm.getCena()));
                        r.createCell(11).setCellValue(m.getUsuario() == null ? "—" : m.getUsuario().getNombreCompleto());
                    } else {

                        r.createCell(0).setCellValue(n++);
                        r.createCell(1).setCellValue(FECHA.format(m.getTurno().getFecha()));
                        r.createCell(2).setCellValue(m.getResidente().getCodigoAcceso());
                        r.createCell(3).setCellValue(m.getResidente().getApellido() + ", " + m.getResidente().getNombre());
                        r.createCell(4).setCellValue(marcaDia(fm.getDesayuno()));
                        r.createCell(5).setCellValue(marcaDia(fm.getAlmuerzo()));
                        r.createCell(6).setCellValue(marcaDia(fm.getCena()));
                    }
                }

                fila++;
                Row rCabStats = hoja.createRow(fila++);
                String[] statsCab = {"Permitidos", "Denegados", "Justificados", "Ausentes"};
                long[] statsVal = {rep.getPermitidos(), rep.getDenegados(),
                        rep.getJustificados(), rep.getAusentes()};
                Row rStats = hoja.createRow(fila++);
                for (int i = 0; i < statsCab.length; i++) {
                    Cell c = rCabStats.createCell(i);
                    c.setCellValue(statsCab[i]);
                    c.setCellStyle(cab);
                    rStats.createCell(i).setCellValue(statsVal[i]);
                }
            }

            if (reservas != null) {
                Sheet hoja = wb.createSheet("Extras");
                encabezadoHoja(hoja, titulo, cab, "COMEDOR UPEU — Extras (raciones reservadas)", filtros);
                String[] cabRes = completo

                        ? new String[]{"N°", "Fecha", "Residente", "Código", "Residencia de Género", "Turno",
                                "Hora programada", "Hora real", "Reservó", "Estado", "Entregado a", "Motivo"}

                        : new String[]{"N°", "Fecha", "Residente", "Código", "Turno", "Estado"};
                escribirCabeceras(hoja, cab, cabRes, 3);
                int fila = 4, n = 1;
                for (var s : reservas) {
                    Row r = hoja.createRow(fila++);
                    String estado = "PENDIENTE".equals(s.getEstado()) ? "Pendiente" : "Entregada";
                    if (completo) {
                        r.createCell(0).setCellValue(n++);
                        r.createCell(1).setCellValue(FECHA.format(s.getFecha()));
                        r.createCell(2).setCellValue(s.getResidente().getApellido() + ", " + s.getResidente().getNombre());
                        r.createCell(3).setCellValue(s.getResidente().getCodigoAcceso());
                        r.createCell(4).setCellValue(s.getResidente().getPabellon() == null ? "—" : s.getResidente().getPabellon());
                        r.createCell(5).setCellValue(capitalizar(s.getTipoComida()));
                        r.createCell(6).setCellValue(s.getHoraRecojo() == null ? "—" : s.getHoraRecojo().toString());
                        r.createCell(7).setCellValue(s.getFechaHoraEntrega() == null ? "—" : HORA.format(s.getFechaHoraEntrega()));
                        r.createCell(8).setCellValue(s.getUsuario() == null ? "—" : s.getUsuario().getNombreCompleto());
                        r.createCell(9).setCellValue(estado);
                        r.createCell(10).setCellValue(s.getEntregadoATexto());
                        r.createCell(11).setCellValue(s.getMotivo() == null ? "" : s.getMotivo());
                    } else {

                        r.createCell(0).setCellValue(n++);
                        r.createCell(1).setCellValue(FECHA.format(s.getFecha()));
                        r.createCell(2).setCellValue(s.getResidente().getApellido() + ", " + s.getResidente().getNombre());
                        r.createCell(3).setCellValue(s.getResidente().getCodigoAcceso());
                        r.createCell(4).setCellValue(capitalizar(s.getTipoComida()));
                        r.createCell(5).setCellValue(estado);
                    }
                }
            }

            if (justificaciones != null) {
                Sheet hoja = wb.createSheet("Justificaciones");
                encabezadoHoja(hoja, titulo, cab, "COMEDOR UPEU — Justificaciones de inasistencia", filtros);
                String[] cabJus = completo

                        ? new String[]{"N°", "Fecha", "Residente", "Código", "Residencia de Género", "Registró", "Motivo"}

                        : new String[]{"N°", "Fecha", "Residente", "Código", "Registró"};
                escribirCabeceras(hoja, cab, cabJus, 3);
                int fila = 4, n = 1;
                for (var a : justificaciones) {
                    Row r = hoja.createRow(fila++);

                    String fechaTxt = FECHA.format(a.getFechaInicio())
                            + (a.getFechaInicio().equals(a.getFechaFin()) ? "" : " – " + FECHA.format(a.getFechaFin()));
                    int c = 0;
                    r.createCell(c++).setCellValue(n++);
                    r.createCell(c++).setCellValue(fechaTxt);
                    r.createCell(c++).setCellValue(a.getResidente().getApellido() + ", " + a.getResidente().getNombre());
                    r.createCell(c++).setCellValue(a.getResidente().getCodigoAcceso());
                    if (completo) {
                        r.createCell(c++).setCellValue(a.getResidente().getPabellon() == null ? "—" : a.getResidente().getPabellon());
                    }
                    r.createCell(c++).setCellValue(a.getUsuario() == null ? "—" : a.getUsuario().getNombreCompleto());

                    if (completo) {
                        r.createCell(c).setCellValue(a.getMotivo() == null ? "" : a.getMotivo());
                    }
                }
            }

            if (eventos != null) {
                Sheet hoja = wb.createSheet("Eventos");
                encabezadoHoja(hoja, titulo, cab, "COMEDOR UPEU — Eventos y entrega de raciones adicionales", filtros);

                escribirCabeceras(hoja, cab, new String[]{"Evento", "Fecha", "N°", "Residente",
                        "Código", "¿Recibió su ración?"}, 3);
                int fila = 4;
                for (var ev : eventos) {
                    var evento = (com.upeu.comedorupeu.models.EventoEspecial) ev.get("evento");
                    @SuppressWarnings("unchecked")
                    var filasEntrega = (List<java.util.Map<String, Object>>) ev.get("filas");
                    int n = 1;
                    for (var fe : filasEntrega) {
                        Row r = hoja.createRow(fila++);
                        r.createCell(0).setCellValue(evento.getNombre());
                        r.createCell(1).setCellValue(FECHA.format(evento.getFechaEvento()));
                        r.createCell(2).setCellValue(n++);
                        r.createCell(3).setCellValue(String.valueOf(fe.get("nombre")));
                        r.createCell(4).setCellValue(String.valueOf(fe.get("codigo")));
                        Object recibido = fe.get("recibido");
                        r.createCell(5).setCellValue(recibido == null ? "Sin pase de lista"
                                : (Boolean.TRUE.equals(recibido) ? "Sí" : "No"));
                    }
                }
            }

            if (wb.getNumberOfSheets() == 0) {
                Sheet hoja = wb.createSheet("Sin datos");
                encabezadoHoja(hoja, titulo, cab, "COMEDOR UPEU", "No se seleccionó ninguna sección para exportar.");
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void encabezadoHoja(Sheet hoja, CellStyle titulo, CellStyle cab, String texto, String filtros) {
        Row rTitulo = hoja.createRow(0);
        Cell cTitulo = rTitulo.createCell(0);
        cTitulo.setCellValue(texto);
        cTitulo.setCellStyle(titulo);
        hoja.createRow(1).createCell(0).setCellValue(filtros);
    }

    private void escribirCabeceras(Sheet hoja, CellStyle cab, String[] cabeceras, int filaIndice) {
        Row rCab = hoja.createRow(filaIndice);
        for (int i = 0; i < cabeceras.length; i++) {
            Cell c = rCab.createCell(i);
            c.setCellValue(cabeceras[i]);
            c.setCellStyle(cab);
            hoja.setColumnWidth(i, cabeceras[i].length() > 10 ? 6500 : 3800);
        }
    }

    public byte[] exportarTabla(String titulo, String filtros, String[] cabeceras,
                                List<String[]> filas) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet("Datos");
            CellStyle cab = estiloCabecera(wb);
            CellStyle tit = estiloTitulo(wb);
            encabezadoHoja(hoja, tit, cab, titulo, filtros);
            escribirCabeceras(hoja, cab, cabeceras, 3);
            int fila = 4;
            for (String[] datos : filas) {
                Row r = hoja.createRow(fila++);
                for (int i = 0; i < datos.length; i++) {
                    r.createCell(i).setCellValue(datos[i] == null ? "" : datos[i]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportarIndividual(ReporteIndividual rep,
                                     List<com.upeu.comedorupeu.models.SolicitudExtemporanea> reservas) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet("Reporte Individual");
            CellStyle cab = estiloCabecera(wb);
            CellStyle titulo = estiloTitulo(wb);

            Residente res = rep.getResidente();
            Row rTitulo = hoja.createRow(0);
            Cell cTitulo = rTitulo.createCell(0);
            cTitulo.setCellValue("COMEDOR UPEU — Reporte de " + res.getNombreCompleto());
            cTitulo.setCellStyle(titulo);

            hoja.createRow(1).createCell(0).setCellValue("Código: " + res.getCodigoAcceso()
                    + "   Carrera: " + (res.getCarrera() == null ? "—" : res.getCarrera())
                    + "   Período: " + FECHA.format(rep.getDesde()) + " a " + FECHA.format(rep.getHasta()));
            hoja.createRow(2).createCell(0).setCellValue("Asistencias: " + rep.getAsistencias() + " / " + rep.getTotalComidas()
                    + "   Justificadas: " + rep.getJustificadas()
                    + "   Injustificadas: " + rep.getInjustificadas());

            String[] cabeceras = {"Fecha", "Día", "Desayuno", "Almuerzo", "Cena", "Observaciones"};
            Row rCab = hoja.createRow(4);
            for (int i = 0; i < cabeceras.length; i++) {
                Cell c = rCab.createCell(i);
                c.setCellValue(cabeceras[i]);
                c.setCellStyle(cab);
                hoja.setColumnWidth(i, i == 5 ? 10000 : 3600);
            }

            int fila = 5;
            for (FilaDia f : rep.getFilas()) {
                Row r = hoja.createRow(fila++);
                r.createCell(0).setCellValue(FECHA.format(f.getFecha()));
                r.createCell(1).setCellValue(f.getDia());
                r.createCell(2).setCellValue(marcaDia(f.getDesayuno()));
                r.createCell(3).setCellValue(marcaDia(f.getAlmuerzo()));
                r.createCell(4).setCellValue(marcaDia(f.getCena()));
                r.createCell(5).setCellValue(f.getObservacion());
            }

            if (reservas != null && !reservas.isEmpty()) {
                fila++;
                Row rExtras = hoja.createRow(fila++);
                Cell cExtras = rExtras.createCell(0);
                cExtras.setCellValue("RACIONES RESERVADAS EN EL PERÍODO");
                cExtras.setCellStyle(cab);
                for (var s : reservas) {
                    Row r = hoja.createRow(fila++);
                    r.createCell(0).setCellValue(FECHA.format(s.getFecha()));
                    r.createCell(1).setCellValue(s.getTipoComida());
                    r.createCell(2).setCellValue(s.getHoraRecojo() == null ? "—" : s.getHoraRecojo().toString());
                    r.createCell(3).setCellValue(s.getMotivo());
                    r.createCell(4).setCellValue("PENDIENTE".equals(s.getEstado()) ? "Pendiente" : "Atendida");
                }
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    private String capitalizar(String texto) {
        if (texto == null || texto.isEmpty()) return "—";
        return texto.substring(0, 1).toUpperCase() + texto.substring(1).toLowerCase();
    }

    private String marcaDia(String estado) {
        return switch (estado == null ? "" : estado) {
            case "SI" -> "Asistió";
            case "JUST" -> "Justificado";
            case "NO" -> "Faltó";
            default -> "—";
        };
    }

    private CellStyle estiloCabecera(Workbook wb) {
        CellStyle estilo = wb.createCellStyle();
        Font fuente = wb.createFont();
        fuente.setBold(true);
        fuente.setColor(IndexedColors.WHITE.getIndex());
        estilo.setFont(fuente);
        estilo.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return estilo;
    }

    private CellStyle estiloTitulo(Workbook wb) {
        CellStyle estilo = wb.createCellStyle();
        Font fuente = wb.createFont();
        fuente.setBold(true);
        fuente.setFontHeightInPoints((short) 14);
        estilo.setFont(fuente);
        return estilo;
    }
}
