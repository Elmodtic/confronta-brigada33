# Sistema de Confronta Diaria — Brigada de Comunicaciones N.º 33 "Rumiñahui"

App móvil (Android/Kotlin) + API REST (Node.js/Express) + base de datos (MySQL)
para digitalizar la confronta diaria del personal militar: saldo prepago,
reserva de comida, ticket QR de un solo uso, canje en el comedor, reportes
Excel y panel de KPIs. Roles: **ADMIN, TESORERO, RANCHERO, OPERADOR, CONSULTA**.

El ingreso lleva **verificación en dos pasos (TOTP) obligatoria para todos los
roles salvo el ADMIN**.

## Arquitectura

```
android/   → app Kotlin (Retrofit), consume la API por HTTPS
backend/   → API Node.js + Express, JWT, bcrypt, TOTP, exceljs
db/        → esquema y migraciones MySQL
docs/      → documentación técnica y de despliegue
```

```
Teléfono ──HTTPS──> Cloudflare ──túnel saliente──> Nginx ──> Node ──> MySQL
                                                   :443      :3000    :3306
```

Documentación: [docs/CONTEXTO_TECNICO.md](docs/CONTEXTO_TECNICO.md) ·
[docs/GUIA_ARRANQUE.md](docs/GUIA_ARRANQUE.md) ·
[docs/PUBLICAR_SERVIDOR.md](docs/PUBLICAR_SERVIDOR.md) (túnel, certificado,
cortafuegos y cómo la app encuentra la dirección vigente).

---

## Levantarlo desde cero en una PC nueva

### 1. Requisitos

- **Git**
- **Node.js** 18 o superior (probado en v22)
- **XAMPP** (MySQL/MariaDB) o MySQL 8 standalone
- **Android Studio** reciente (JDK 21 / JBR, AGP 9.2.1, Gradle 9.4.1, minSdk 26)

Solo para publicar el servidor fuera de la red local: **Nginx** y
**cloudflared** (ver `docs/PUBLICAR_SERVIDOR.md`).

### 2. Clonar

```bash
git clone https://github.com/Elmodtic/confronta-brigada33.git
cd confronta-brigada33
```

### 3. Base de datos

Arranca MySQL desde el panel de XAMPP y deja que la migración construya el
esquema completo. **No importes `db/schema.sql` a mano**: está congelado en una
versión temprana y le faltan las tablas y columnas que se agregaron después.

```bash
cd backend
npm install
node migrar.js
```

`migrar.js` crea la base, todas las tablas y las columnas de TOTP y de
recuperación de cuenta. Es seguro repetirlo: solo aplica lo que falte.

**Se ejecuta con un usuario que pueda crear tablas** (`root` en XAMPP). El
usuario restringido con el que después corre la aplicación no puede, y eso es
deliberado. Para crearlo:

```bash
C:\xampp\mysql\bin\mysql.exe -u root < ../db/seguridad_db.sql
```

Antes reemplaza `CAMBIA_ESTA_CONTRASENA` por una contraseña real. **Evita el
carácter `#`**: `dotenv` lo lee como inicio de comentario y la conexión falla
con un mensaje que no apunta a la causa.

Para cargar los catálogos oficiales (17 grados y 9 unidades de la Fuerza
Terrestre) conservando el ADMIN:

```bash
node resetcatalogos.js
```

### 4. Variables de entorno

```bash
copy .env.example .env
```

Dos valores **se generan, no se inventan**:

```bash
node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"
```

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

El primero es `JWT_SECRET`, el segundo `TOTP_LLAVE`.

| Variable | Valor típico | Para qué sirve |
|---|---|---|
| `PORT` | `3000` | Puerto de la API. |
| `DB_HOST` · `DB_PORT` | `localhost` · `3306` | Servidor MySQL. |
| `DB_USER` · `DB_PASSWORD` | usuario restringido | Credenciales de la aplicación. |
| `DB_NAME` | `confronta_brigada` | Base de datos. |
| `JWT_SECRET` | 96 hex aleatorios | Firma de los tokens. Nunca reutilizar entre entornos. |
| `JWT_EXPIRES` | `8h` | Vigencia del token. |
| `CORS_ORIGINS` | `*` en desarrollo | Orígenes permitidos. |
| `TOTP_LLAVE` | **64 hex exactos** | Cifra (AES-256-GCM) los secretos TOTP guardados. |

El servidor **no arranca** si `JWT_SECRET` está vacío, es el valor de plantilla
o mide menos de 32 caracteres, ni si `TOTP_LLAVE` no son 64 hexadecimales. Es a
propósito: una configuración a medias se descubre al arrancar, no en producción.

> Si se pierde `TOTP_LLAVE`, ningún código de verificación podrá validarse y
> habrá que reinscribir a todos los usuarios desde la cuenta de administrador.

### 5. Arrancar la API

```bash
npm start
```

Debe imprimir `API escuchando en http://localhost:3000`. En Windows también
sirve `iniciar_backend.bat`.

Para publicarla fuera de la red local (Nginx + túnel), seguir
[docs/PUBLICAR_SERVIDOR.md](docs/PUBLICAR_SERVIDOR.md) y usar
`arrancar_todo.bat`.

### 6. App Android

Abre `android/` en Android Studio y espera la sincronización de Gradle.

**No hace falta editar ninguna IP a mano.** La app resuelve sola la dirección
del servidor:

1. Al abrirse consulta `servidor.json` de este repositorio (vía
   `raw.githubusercontent.com`) y se reconfigura con la dirección que encuentre.
2. Si eso falla, usa `URL_POR_DEFECTO` en
   [ApiClient.kt](android/app/src/main/java/com/brigada/confronta/data/ApiClient.kt).
3. Como último recurso manual: en la pantalla de ingreso, **mantener pulsado el
   escudo** abre el campo para escribir la dirección del servidor.

`iniciar_tunel.ps1` actualiza `servidor.json` y lo sube al repositorio en cada
arranque, así que un cambio de túnel no obliga a reinstalar el APK.

Para desarrollo contra un backend local: apuntar a `http://10.0.2.2:3000/`
desde el emulador, o a la IP LAN de la PC desde un celular en la misma WiFi
(con el puerto 3000 abierto: `abrir_firewall_puerto3000.bat` como administrador).

### 7. Primer ingreso

El `username` de cada usuario **es su cédula**; el ADMIN es la única excepción
y entra como `admin`. La contraseña inicial del ADMIN se define al crear la
base — cámbiala apenas entres.

Todos los roles salvo el ADMIN deben inscribir su segundo factor antes de poder
usar la aplicación: al ingresar por primera vez la app lleva directo a la
pantalla de inscripción del código TOTP (Google Authenticator, Aegis, etc.).

Si un usuario pierde el teléfono, el administrador lo busca por cédula y usa
**Reiniciar cuenta**: la contraseña vuelve a ser la propia cédula, con 24 horas
de vigencia, y al ingresar se le obliga a cambiarla y a volver a inscribir el
segundo factor.

---

## Qué NO se sube al repositorio (y por qué)

- `backend/.env` — credenciales, `JWT_SECRET` y `TOTP_LLAVE` reales.
- `certs/*.key` — llave privada TLS. El certificado público sí está versionado;
  la llave se regenera (ver `docs/PUBLICAR_SERVIDOR.md`).
- `android/keystore/` y `android/keystore.properties` — **firma del APK de
  release**. Si se pierde, no se puede volver a firmar una actualización de la
  app ya instalada; si se filtra, cualquiera puede publicar actualizaciones
  falsas. Consérvala fuera del repositorio, en un lugar seguro.
- `Credenciales.txt`, `db/respaldos/` — datos sensibles y cédulas reales.
- `node_modules/`, `build/`, `.gradle/`, `local.properties`, APKs — se
  regeneran con `npm install` y Gradle Sync.
- `url_tunel.txt`, `*.log` — efímeros, cambian en cada arranque.

Los archivos de plantilla (`.env.example`) sí están versionados: cópialos y
complétalos con tus propios valores, nunca reutilices claves de otra máquina.

## Advertencia de seguridad pendiente

Mientras el túnel esté activo, **la API queda accesible desde internet**. Con
datos de prueba es aceptable. Antes de cargar cédulas reales del personal hay
que cerrar el acceso (red privada tipo Tailscale, o Cloudflare Access delante
del túnel). Está anotado como recomendación en el informe del proyecto.

## Convención de commits y trazabilidad

Todo cambio no trivial sigue este flujo, auditado en `CHECKLIST_AUDITORIA.md`:

1. Se crea un **issue** describiendo el hallazgo o la funcionalidad.
2. Se crea una **rama** con prefijo `feature/`, `audit/`, `docs/` o `fix/`.
3. Los **commits** usan el formato `tipo: descripción (#issue)`, con tipos:
   `feat`, `fix`, `docs`, `chore`, `audit`.
4. Se abre un **Pull Request** hacia `main` usando la plantilla de
   `.github/pull_request_template.md`, referenciando el issue con
   `Closes #N` o `Refs #N`.
5. El release se etiqueta con **SemVer** (`vMAJOR.MINOR.PATCH`) únicamente
   desde `main`, después del merge, con notas de la versión.
