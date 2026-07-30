# COMEDOR-UPEU — Sistema de Control de Asistencia al Comedor

Sistema de control de acceso al comedor universitario de residentes de la Universidad
Peruana Unión, con tres roles: **Administrador**, **Cajero** y **Preceptor**.

> **Principio del sistema: el sistema informa, el cajero decide.**
> Al leer un código, la pantalla muestra los datos del residente, su consumo del día y
> las alertas que correspondan (doble entrada, deuda, justificación vigente, ración
> reservada, ración especial), pero **Permitir** y **Denegar** siempre los pulsa una persona.

---

## Tecnologías

| Componente | Versión |
|---|---|
| Java | 17 |
| Spring Boot | 4.0.5 |
| Spring Security | 7 |
| Thymeleaf | 3.1 |
| MySQL | 8 |
| Apache POI | Excel (importar y exportar) |

---

## Puesta en marcha

### 1. Requisitos

- Java 17 o superior
- MySQL 8 accesible
- No hace falta crear la base de datos a mano: se crea sola al arrancar, y las tablas
  se generan a partir de las clases del proyecto.

### 2. Configuración

Crea un archivo llamado `.env` en la raíz del proyecto (junto a `pom.xml`) con los
valores de tu servidor:

```
DB_URL=jdbc:mysql://SERVIDOR:3306/dbcomedorupeu?createDatabaseIfNotExist=true&serverTimezone=UTC
DB_USUARIO=usuario_de_la_base
DB_CLAVE=clave_de_la_base
ADMIN_CORREO=admin@upeu.edu.pe
ADMIN_CLAVE=escribe_aqui_una_clave_fuerte
ADMIN_RESET=false
```

`ADMIN_RESET` sirve para recuperar el acceso si el administrador olvida su contraseña:
se pone en `true`, se reinicia la aplicación (su clave vuelve al valor de `ADMIN_CLAVE`)
y luego se deja de nuevo en `false`.

`ADMIN_CLAVE` es **obligatoria**: si no está definida, la aplicación no arranca. Es
intencional, para que ningún servidor quede funcionando con una contraseña de ejemplo.

El archivo `.env` está en `.gitignore` y **nunca debe subirse al repositorio**.

### 3. Arranque

```bash
mvnw spring-boot:run
```

La aplicación queda en `http://localhost:8080`. Para usar otro puerto:

```bash
mvnw spring-boot:run -Dspring-boot.run.jvmArguments=-Dserver.port=8090
```

### 4. Primer ingreso

Al arrancar por primera vez el sistema crea **únicamente** la cuenta administradora,
con el correo y la contraseña que definiste en el `.env`. Desde ahí se crean las
demás cuentas (cajeros y preceptores), los puntos de acceso y los residentes.

> Se recomienda entrar y cambiar la contraseña del administrador desde
> **Cuentas → Mi cuenta** apenas se despliegue el sistema.

---

## Qué hace cada rol

### Administrador
- Puntos de acceso: crear, editar, abrir y cerrar; los que ya tienen historial se archivan
  en lugar de borrarse, para no perder registros.
- Programación semanal: horarios por turno y por entrada, con copiar y pegar día completo.
  Un verificador automático abre y cierra turnos y entradas a la hora exacta, sin que
  nadie tenga que hacerlo.
- Cuentas de cajeros y preceptores, con restablecimiento de contraseña.
- Bandeja de incidencias: raciones alteradas, reportes de cajeros y avisos de preceptores.
- Justificaciones y reservas de todo el sistema, con exportación a Excel.
- Avisos dirigidos a cajeros o preceptores, con vigencia por fecha y franja horaria.

### Cajero
- Lectura del código por teclado, lector de código de barras o QR (envío automático).
- Ficha del residente con foto grande y todas las alertas del momento.
- Registro de la decisión: permitir, denegar o registrar como justificado.
- Entrega de raciones reservadas, individuales o por grupo completo.
- Deshacer la última acción dentro de los 10 minutos.
- Reporte de incidencias al administrador.

### Preceptor
- Residentes de su residencia de género: registro individual o importación masiva desde
  Excel, con plantilla descargable.
- Justificaciones de ausencia por rango de fechas o de un solo día, marcando las comidas
  en orden cronológico.
- Raciones especiales para días y comidas concretos.
- Reservas de ración, individuales o para varios residentes a la vez.
- Eventos especiales con pase de lista.

### Reportes (administrador y preceptores)
- Vista general con cuatro secciones: asistencia, reservas, justificaciones y eventos.
- Modo simple para lectura rápida y modo completo con todas las columnas y filtros.
- Reporte individual por residente, por días o por semanas, con el detalle de cada día.
- Gráfico de horas de mayor afluencia.
- Exportación a Excel de cualquier vista, respetando los filtros aplicados.
- Enlace público para apoderados: consultan la asistencia de su residente sin necesidad
  de tener cuenta.

---

## Seguridad

- Las contraseñas se guardan cifradas con **BCrypt**. No se pueden consultar ni recuperar:
  si alguien la olvida, se genera una nueva.
- Tras **5 intentos fallidos** la cuenta queda bloqueada **5 minutos**.
- Solo se aceptan correos institucionales **@upeu.edu.pe**.
- Cada rol está restringido en el servidor, no solo ocultando opciones del menú.
- Cada preceptor solo ve y gestiona los residentes de su residencia de género.
- Las credenciales viven en el archivo `.env`, fuera del repositorio.
- Los registros con historial no se eliminan: se anulan o se archivan, para conservar
  la trazabilidad.

---

## Estructura del proyecto

```
src/main/java/com/upeu/comedorupeu/
├── models/       Entidades: residentes, turnos, marcaciones, usuarios...
├── repository/   Acceso a la base de datos
├── services/     Lógica de negocio (turnos, reportes, validación, Excel)
├── controller/   Rutas y pantallas por rol
├── config/       Seguridad, datos iniciales y verificador automático
└── dto/          Objetos de apoyo para armar los reportes

src/main/resources/
├── templates/    Pantallas (Thymeleaf), organizadas por rol
└── application.properties
```

Las fotos de los residentes se guardan en `uploads/residentes` y se sirven en `/uploads/**`.
Esa carpeta tampoco se sube al repositorio.

---

## Respaldo de los datos

La base de datos no vive dentro del proyecto. Para llevártela a otra máquina:

```bash
respaldar-datos.bat
```

Genera `copia-de-datos/datos.sql`. En el equipo de destino, con MySQL encendido:

```bash
restaurar-datos.bat
```

Esa copia contiene datos reales, por lo que está excluida del repositorio.
