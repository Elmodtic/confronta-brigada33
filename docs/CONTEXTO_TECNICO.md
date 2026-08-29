# Sistema de Confronta Diaria — Brigada de Comunicaciones N.º 33 "Rumiñahui"

Proyecto integrador (7mo semestre). Digitaliza la "confronta diaria" militar:
el registro del estado de cada persona (presente, permiso, comisión, operación,
franco) y las raciones que consume (desayuno/almuerzo/merienda), con trazabilidad
de quién registró cada dato.

## Arquitectura

- **Backend** (`backend/`): API REST en Node.js + Express. Autenticación con
  JWT, contraseñas hasheadas con bcrypt, roles (ADMIN/OPERADOR/CONSULTA) y tabla
  de auditoría para trazabilidad.
- **Base de datos** (`db/`): MySQL, corre localmente en XAMPP
  (`localhost:3306`, usuario `root` sin contraseña). Base: `confronta_brigada`.
  Esquema en `db/schema.sql`.
- **App Android** (`android/`): cliente en Kotlin, consume la API con Retrofit.
  AGP 9.2.1 (Kotlin integrado, sin plugin aparte), Gradle 9.4.1, JDK 21 (JBR),
  minSdk 26, compileSdk 37, viewBinding. Pantallas: login, registro, olvidé
  contraseña, menú, calendario de consumo, historial por meses, liquidación.
- **Docs** (`docs/`): guía de arranque y documentación del proyecto.

El backend y MySQL corren en la máquina de desarrollo; la app Android se
conecta por HTTP a la IP de esa máquina (`10.0.2.2` desde el emulador, o la IP
local desde un celular físico en la misma red).

## Modelo de datos (tablas clave)

- `grado`, `unidad` — catálogos.
- `usuario` — login del sistema (`username`, `password_hash`, `rol`, `activo`).
- `personal` — militares (cédula, nombres, apellidos, grado, unidad).
- `confronta` — registro diario por persona y fecha (estado, raciones,
  novedad, quién y cuándo lo registró). Única por `(id_personal, fecha)`.
- `auditoria` — log de acciones (login, registros de confronta, etc.).

## Backend — endpoints

Cuentas/seguridad:
- `POST /api/login` — autenticación, devuelve JWT (incluye `id_personal`).
- `POST /api/registro` — crea ficha de personal + cuenta (rol OPERADOR).
- `GET /api/olvido/pregunta?username=` — pregunta de seguridad del usuario.
- `POST /api/olvido/reset` — verifica respuesta y cambia la contraseña.

Catálogos y tarifa:
- `GET /api/grados`, `GET /api/unidades` — para los combos del registro.
- `GET /api/tarifa` — precios vigentes. `PUT /api/tarifa` — cambiarlos (solo ADMIN).

Administración (solo ADMIN/root) y producción (ranchero):
- `GET /api/usuarios` — lista de usuarios. `PUT /api/usuarios/:id` — cambiar rol/activo.
- `POST /api/usuarios/:id/password` — restablecer contraseña. `GET /api/auditoria` — registros.
- `GET /api/produccion/:fecha` — platos a cocinar por fecha + desglose por unidad (ADMIN/RANCHERO).

Consumo (autoservicio con `/api/mi/...`, o por persona para ADMIN/OPERADOR):
- `POST /api/confronta` — registra/actualiza consumo de un día (propio o ajeno).
- `GET /api/mi/consumo/:anio/:mes` — días del mes con costo (calendario).
- `GET /api/mi/historial/:anio` — totales por mes de todo el año.
- `GET /api/mi/liquidacion/:anio/:mes` — desglose y total a pagar del mes.
- `GET /api/personal`, `GET /api/confronta/:fecha`, `GET /api/reporte/:fecha`
  — consultas para gestores (ADMIN/OPERADOR).
- `GET /api/liquidacion/:idPersonal/:anio/:mes` — misma liquidación mensual que
  `/api/mi/liquidacion`, pero de una persona indicada (ADMIN/OPERADOR).

El costo se calcula con la tarifa vigente a la fecha (desayuno 1.90, almuerzo
3.00, merienda 1.75).

## Seguridad y extras

- **Contraseñas**: mínimo 8 con mayúscula, minúscula, número y carácter especial
  (validado en registro, recuperación y reset del admin).
- **Bloqueo de login**: 3 intentos fallidos → cuenta bloqueada 10 min; al expirar,
  3 intentos más (columnas `intentos_fallidos`, `bloqueado_hasta`).
- **Cabeceras** con `helmet` y **CORS restringido** (`CORS_ORIGINS`).
- **App**: guarda credenciales cifradas (EncryptedSharedPreferences) e ingreso con
  **huella/rostro/PIN** (BiometricPrompt). Saludo con grado + nombre + apellido.
- **Reportes Excel** (`exceljs`): `/api/reportes/mi-consumo.xlsx` (usuario),
  `/produccion.xlsx` (ranchero), `/recargas.xlsx` (tesorero), `/general.xlsx` (admin).
- **KPIs** (`GET /api/kpis`, admin): panel de Gobierno de TI (asistencia, saldo en
  circulación, recaudo/consumo del mes, cumplimiento, desperdicio, usuarios por rol).
- Pendiente para la fase de nube: HTTPS/TLS y APK firmado de release.

## Roles

- **ADMIN (root)**: gestiona usuarios (asigna roles, restablece contraseñas,
  activa/desactiva), ve auditoría, edita precios, ve producción, canjea QR.
- **RANCHERO**: ve cuántos platos cocinar por fecha (desglose por unidad) y
  **canjea los QR** de los comensales en el comedor.
- **TESORERO**: **recarga el saldo** de los usuarios buscándolos por cédula.
- **OPERADOR / CONSULTA**: comensales base.

Todos los roles son también comensales: reservan, generan su QR y ven su saldo.

## Login y flujo de comida (prepago + QR)

- **Login por cédula**: el `username` de cada usuario ES su cédula. Solo el
  ADMIN entra con un usuario distinto (`admin`).
- **Saldo prepago**: el tesorero recarga; el saldo se descuenta al comer.
- **Reserva**: el comensal reserva sus comidas; el cupo cierra a las **17:00 del
  día anterior** (para que el ranchero prevea). Tabla `confronta`.
- **QR de un solo uso** (tabla `ticket`): el comensal genera un QR SOLO de las
  comidas que reservó. El ranchero lo canjea (`/api/canjear`), lo que descuenta
  el precio de la comida del saldo y marca el ticket como CANJEADO (un solo uso).
- Endpoints clave: `GET /api/mi/estado` (saldo+movimientos),
  `GET /api/tesoreria/buscar?cedula=`, `POST /api/recargas`, `POST /api/reserva`,
  `POST /api/qr`, `POST /api/canjear`.

Catálogos oficiales: 17 grados de la Fuerza Terrestre y 9 unidades (con siglas),
definidos en `backend/catalogos.js`. `node resetcatalogos.js` los recarga
(borra datos de prueba dependientes; conserva ADMIN).

Usuarios de prueba (login por cédula; el admin es la excepción). Las contraseñas
de personal deben cumplir la política; en las demos se usa `Clave123*`:
admin/admin123 (ADMIN root), 1801112221/Clave123* (Darío, OPERADOR),
1804445551/Clave123* (RANCHERO), 1805556661/Clave123* (TESORERO).
Se recrean con `node resetcatalogos.js` + el script de demo del scratchpad.

## Conexión app ↔ backend (desarrollo)

- La app apunta a la IP LAN de la PC (`ApiClient.BASE_URL`), que sirve para el
  emulador y para celulares físicos en la misma WiFi. Si la PC cambia de IP,
  actualizar esa constante (ver con `ipconfig`).
- Requiere el puerto 3000 abierto en el Firewall de Windows
  (`abrir_firewall_puerto3000.bat`, ejecutar como administrador).
- Levantar el backend con `node server.js` en `backend/` antes de usar la app.

## Convenciones

- Nombres de tablas/columnas y mensajes de la API en español.
- Contraseñas SIEMPRE hasheadas (bcrypt); nunca en texto plano.
- `backend/.env` (no versionado) define `DB_HOST`, `DB_PORT`, `DB_USER`,
  `DB_PASSWORD`, `DB_NAME`, `JWT_SECRET`, `JWT_EXPIRES`, `PORT`. Ver
  `backend/.env.example` como plantilla.
- Este proyecto integra varias asignaturas (móviles, seguridad informática,
  gestión de configuración con Git, gobierno de TI, documentación científica);
  ver [docs/GUIA_ARRANQUE.md](docs/GUIA_ARRANQUE.md) para el detalle.
