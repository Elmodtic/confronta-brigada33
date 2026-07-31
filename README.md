# Sistema de Confronta Diaria — Brigada de Comunicaciones N.º 33 "Rumiñahui"

App móvil (Android/Kotlin) + API REST (Node.js/Express) + base de datos (MySQL)
para digitalizar la confronta diaria del personal militar: saldo prepago,
reserva de comida, ticket QR de un solo uso, canje en el comedor, reportes
Excel y panel de KPIs. Roles: **ADMIN, TESORERO, RANCHERO, OPERADOR, CONSULTA**.

Documentación técnica ampliada: [docs/CONTEXTO_TECNICO.md](docs/CONTEXTO_TECNICO.md)
y [docs/GUIA_ARRANQUE.md](docs/GUIA_ARRANQUE.md).

## Arquitectura

```
android/   → app Kotlin (Retrofit), consume la API por HTTP
backend/   → API Node.js + Express, JWT, bcrypt, exceljs
db/        → esquema MySQL (db/schema.sql)
```

El backend y MySQL corren en la PC de desarrollo; la app Android se conecta
por HTTP a la IP LAN de esa PC (o `10.0.2.2` desde el emulador).

---

## Cómo retomar el desarrollo en OTRA PC (clonar y continuar)

### 1. Requisitos previos a instalar en la PC nueva

- **Git**
- **Node.js** v18+ (`node --version`)
- **XAMPP** (o MySQL 8+ standalone) — para la base de datos
- **Android Studio** reciente (JDK 21 / JBR incluido, AGP 9.2.1, Gradle 9.4.1)

### 2. Clonar el repositorio

```bash
git clone https://github.com/Elmodtic/confronta-brigada33.git
cd confronta-brigada33
```

### 3. Base de datos (MySQL vía XAMPP)

1. Arranca **MySQL** desde el panel de XAMPP (usuario `root`, sin contraseña
   por defecto en `localhost:3306`).
2. Importa `db/schema.sql` desde phpMyAdmin, o por consola:
   ```bash
   mysql -u root -p < db/schema.sql
   ```
   Esto crea la base `confronta_brigada` con catálogos y datos de prueba.
3. (Opcional) Para recargar los catálogos oficiales (grados/unidades) sin
   perder el usuario ADMIN:
   ```bash
   cd backend
   node resetcatalogos.js
   ```

### 4. Backend (Node.js / Express)

```bash
cd backend
npm install
copy .env.example .env      # en PowerShell: Copy-Item .env.example .env
```

Edita `backend/.env` con tus datos reales (ver tabla de variables abajo), y
luego:

```bash
npm start
```

Debe mostrar `API escuchando en http://localhost:3000` (o el puerto que
hayas definido). También puedes usar `iniciar_backend.bat` (doble clic) en
Windows, que hace `cd backend && node server.js` automáticamente.

### 5. Variables de entorno (`backend/.env`)

**No se sube al repositorio** (está en `.gitignore`). Cópialo desde
`backend/.env.example`, que sí está versionado como plantilla. Variables que
usa el backend (`process.env.*` en `server.js`):

| Variable | Ejemplo / valor típico en desarrollo | Para qué sirve |
|---|---|---|
| `PORT` | `3000` | Puerto en el que escucha la API Express. |
| `DB_HOST` | `localhost` | Host del servidor MySQL. |
| `DB_PORT` | `3306` | Puerto de MySQL (el de XAMPP por defecto). |
| `DB_USER` | `root` | Usuario de MySQL. |
| `DB_PASSWORD` | *(vacío en XAMPP por defecto)* | Contraseña de MySQL. |
| `DB_NAME` | `confronta_brigada` | Nombre de la base de datos (ver `db/schema.sql`). |
| `JWT_SECRET` | *(cadena aleatoria larga, propia de cada entorno)* | Clave para firmar/verificar los tokens JWT. **Nunca reutilizar la de otro entorno ni subirla a git.** |
| `JWT_EXPIRES` | `8h` | Tiempo de expiración del token JWT. |
| `CORS_ORIGINS` | `*` en desarrollo; dominio real en producción | Orígenes permitidos por CORS (separados por coma si son varios). |

> Genera un `JWT_SECRET` nuevo y fuerte en cada entorno, por ejemplo:
> `node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"`

### 6. App Android — apuntar al backend de la PC nueva

1. Abre `android/` en Android Studio ("Open" → seleccionar la carpeta) y
   espera a que sincronice Gradle.
2. En Windows, averigua la IP LAN de la PC nueva: `ipconfig` (busca
   "Dirección IPv4", ej. `10.20.91.170`).
3. Edita la constante `BASE_URL` en
   [android/app/src/main/java/com/brigada/confronta/data/ApiClient.kt](android/app/src/main/java/com/brigada/confronta/data/ApiClient.kt):
   ```kotlin
   const val BASE_URL = "http://TU_IP_LAN:3000/"
   ```
   - Para el emulador de Android Studio también funciona `http://10.0.2.2:3000/`.
   - Para un celular físico, debe ser la IP LAN real, y el celular debe estar
     en la misma red WiFi que la PC.
4. Abre el puerto 3000 en el Firewall de Windows (una sola vez, como
   administrador): clic derecho sobre `abrir_firewall_puerto3000.bat` →
   "Ejecutar como administrador".
5. Ejecuta ▶ (Run 'app') con el backend ya corriendo.

### 7. Usuarios de prueba

Ver [docs/CONTEXTO_TECNICO.md](docs/CONTEXTO_TECNICO.md) para el detalle
completo de roles, endpoints y modelo de datos. El login del personal es por
**cédula** (excepto el ADMIN, que usa `admin`).

### 8. Qué NO se sube al repo (y por qué)

- `backend/.env` — credenciales y `JWT_SECRET` reales de cada entorno.
- `Credenciales.txt` — notas locales con contraseñas/datos sensibles.
- `node_modules/`, `build/`, `.gradle/`, `local.properties`, APKs — se
  regeneran con `npm install` / Gradle Sync, no deben versionarse.
- `CLAUDE.md` — notas internas de desarrollo con Claude Code (ver
  `docs/CONTEXTO_TECNICO.md` como equivalente versionado).

Si al clonar en la PC nueva falta alguno de estos archivos "de plantilla"
(`.env.example`), es intencional: cópialo y complétalo tú con tus propios
valores, nunca reutilices `JWT_SECRET` de otra máquina.
