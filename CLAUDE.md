# Sistema de Confronta Diaria — Brigada de Comunicaciones N.º 33 "Rumiñahui"

Proyecto integrador de 7mo semestre. Digitaliza la "confronta diaria" militar:
el registro del estado de cada persona y las raciones que consume
(desayuno/almuerzo/merienda), con saldo prepago, ticket QR de un solo uso y
trazabilidad de quién registró cada dato.

Para levantarlo desde cero: [README.md](README.md). Para publicarlo fuera de la
red de la unidad: [docs/PUBLICAR_SERVIDOR.md](docs/PUBLICAR_SERVIDOR.md).

## Arquitectura

- **Backend** (`backend/`): API REST Node.js + Express. JWT con `jti` y lista de
  revocación en memoria, bcrypt, TOTP, roles y tabla de auditoría.
- **Base de datos** (`db/`): MySQL/MariaDB. El esquema se construye con
  `node backend/migrar.js`, **no** importando `db/schema.sql` (quedó congelado
  en una versión temprana).
- **App Android** (`android/`): Kotlin + Retrofit. AGP 9.2.1, Gradle 9.4.1,
  JDK 21 (JBR), minSdk 26, viewBinding, Material 3.
- **Docs** (`docs/`): contexto técnico, guía de arranque y despliegue.

## Convenciones del proyecto

- Nombres de tablas, columnas, endpoints y mensajes de la API **en español**.
- Contraseñas siempre hasheadas con bcrypt; nunca en texto plano.
- Los secretos TOTP se guardan cifrados con AES-256-GCM (`backend/totp.js`).
- `backend/.env` no se versiona; `backend/.env.example` es la plantilla.

## Modelo de datos (tablas clave)

- `grado`, `unidad` — catálogos oficiales (17 grados, 9 unidades), en
  `backend/catalogos.js`.
- `usuario` — cuenta de ingreso (`username`, `password_hash`, `rol`, `activo`,
  columnas de TOTP y de recuperación de cuenta).
- `personal` — militares (cédula, nombres, apellidos, grado, unidad).
- `confronta` — reserva diaria por persona y fecha. Única por `(id_personal, fecha)`.
- `ticket` — QR de un solo uso, con estados `ACTIVO` / `CANJEADO` / `ANULADO`.
- `transferencia`, `gasto_rancho`, `relevo` — movimientos del fondo de rancho.
- `auditoria` — log de acciones.

## Roles

- **ADMIN** (`admin`): gestiona usuarios y roles, reinicia cuentas, ve auditoría,
  edita precios, ve producción, canjea QR. Es el único exento de TOTP.
- **RANCHERO**: ve la confronta a cocinar por fecha (desglose por unidad, con
  cuántos ya pasaron y cuántos faltan) y canjea los QR en el comedor.
- **TESORERO**: recarga el saldo de los usuarios buscándolos por cédula.
- **OPERADOR / CONSULTA**: comensales base.

Todos los roles son también comensales: reservan, generan su QR y ven su saldo.
El `username` de cada usuario **es su cédula**; el ADMIN es la excepción.

## Flujo de comida (prepago + QR)

1. El tesorero recarga el saldo.
2. El comensal reserva sus comidas. El cupo cierra a las **17:00 del día
   anterior**, para que el ranchero prevea la cantidad.
3. El comensal genera un QR solo de lo que reservó (`POST /api/qr`).
4. El ranchero lo canjea (`POST /api/canjear`): descuenta el precio del saldo y
   marca el ticket como `CANJEADO`.

El canje usa **bloqueo pesimista de fila** (`SELECT ... FOR UPDATE`) para que un
mismo QR no pueda canjearse dos veces aunque llegue por dos peticiones
simultáneas. El avance del día se cuenta sobre `ticket`, no sobre `confronta`:
`confronta` es lo reservado, `ticket` es lo que realmente pasó.

## Verificación en dos pasos

Obligatoria para todos los roles **salvo el ADMIN**. Implementación en
`backend/totp.js` (RFC 6238 vía `otplib`), con ventana de tolerancia de 30 s y
anti-reuso del paso ya consumido (`totp_ultimo_paso`).

La autorización tiene **un solo punto de control**, `verificarToken` en
`backend/auth.js`, con listas de rutas exentas. Tres barreras en orden:

1. `paso` — token parcial: pasó la contraseña pero falta el código.
2. `cambiar` — debe cambiar la contraseña temporal antes de seguir.
3. `!totp` — no ha inscrito el segundo factor.

> Al tocar esas listas, recordar que `/api/mi/password` **debe** estar exenta de
> la barrera de TOTP: sin eso, una cuenta recién reiniciada por el administrador
> no puede ni cambiar su contraseña ni inscribir el segundo factor, y queda
> atrapada. Ya pasó una vez.

### Recuperación de cuenta

- **Perdió el teléfono** → el administrador lo busca por cédula y usa
  *Reiniciar cuenta*: la contraseña pasa a ser su propia cédula, válida 24
  horas; al ingresar se le obliga a cambiarla y a reinscribir el TOTP.
- **Olvidó la contraseña** → pregunta de seguridad; la restablece **sin**
  desactivar el TOTP.

No hay códigos de respaldo: se quitaron a propósito, y la app dirige al
administrador de la unidad.

## Cómo encuentra la app al servidor

El túnel gratuito de Cloudflare asigna una dirección nueva en cada arranque, así
que la app **no** lleva la IP escrita a mano:

1. `iniciar_tunel.ps1` escribe la dirección vigente en `servidor.json` y la sube
   al repositorio.
2. La app consulta ese archivo al abrirse y se reconfigura sola.
3. Si el ingreso falla por conexión, vuelve a consultarlo y reintenta una vez.
4. Vía manual: mantener pulsado el escudo en la pantalla de ingreso.

Al mover el túnel, actualizar también `URL_POR_DEFECTO` en `ApiClient.kt`.

## Cosas que muerden

- **Fechas**: usar componentes locales, no `toISOString()`. En Ecuador (UTC-5)
  eso corre el día y el ranchero ve la confronta equivocada. Hay un helper
  `fechaIso()` en `server.js`.
- **`dotenv` y el carácter `#`**: en una contraseña de base de datos lo
  interpreta como inicio de comentario. La conexión falla con un mensaje que no
  apunta a la causa.
- **`schema.sql` está obsoleto**: la fuente de verdad del esquema es
  `backend/migrar.js`, verificado contra el esquema real columna por columna.
- **La migración necesita permisos DDL** (`root`). El usuario restringido con el
  que corre la aplicación no puede crear tablas, y eso es deliberado.

## Pendiente conocido

Mientras el túnel está activo, la API queda accesible desde internet. Es
aceptable con datos de prueba; antes de cargar cédulas reales del personal hay
que cerrar el acceso (Tailscale o Cloudflare Access delante del túnel).
