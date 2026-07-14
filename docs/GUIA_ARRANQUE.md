# GUÍA DE ARRANQUE — Sistema de Confronta Diaria (para Darío)

Proyecto: App móvil para la Brigada de Comunicaciones N.º 33 "Rumiñahui".
Tu rol: construir el aplicativo. Tu máquina = centro de operaciones (servidor + base de datos).

## Arquitectura (el "mapa")

    ┌────────────────────┐        HTTP/JSON        ┌──────────────────────┐
    │  App Android        │  ───────────────────▶  │  Backend Node/Express │
    │  (Kotlin,           │  ◀───────────────────  │  (corre en tu PC)     │
    │   Android Studio)   │                         └───────────┬──────────┘
    └────────────────────┘                                     │ SQL
                                                                ▼
                                                    ┌──────────────────────┐
                                                    │  MySQL (XAMPP)        │
                                                    │  confronta_brigada    │
                                                    └──────────────────────┘

El backend y MySQL viven en TU máquina. La app Android se conecta a la IP de tu PC.

---

## PASO 0 — Instalar Claude Code (una sola vez)

Recuerda: ESTE chat no toca tu computadora. Claude Code SÍ. Instálalo:
1. Ten Node.js instalado (v18+). Compruébalo: `node --version`
2. Instala Claude Code:  `npm install -g @anthropic-ai/claude-code`
   (o descarga la app de escritorio de Claude e inicia Claude Code desde ahí)
3. Abre una terminal en la carpeta donde quieras el proyecto y escribe:  `claude`

A partir de ahí, todo lo que sigue se lo pides a Claude Code en lenguaje natural.

---

## PASO 1 — Base de datos MySQL (con XAMPP)

1. Abre el panel de XAMPP y arranca **Apache** y **MySQL**.
2. Entra a phpMyAdmin (http://localhost/phpmyadmin).
3. Importa el archivo `db/schema.sql` (pestaña "Importar").
   → Esto crea la base `confronta_brigada` con tablas y datos de prueba.

O bien, pídeselo a Claude Code:
> "Ejecuta el archivo db/schema.sql contra mi MySQL local (usuario root, sin
>  contraseña, puerto 3306) para crear la base de datos."

---

## PASO 2 — Backend / API (Node.js)

En la carpeta `backend/`:
1. Copia `.env.example` como `.env` y ajusta si tu MySQL tiene contraseña.
2. Instala dependencias:  `npm install`
3. Crea el usuario admin con contraseña real:  `npm run seed`
4. Arranca la API:  `npm start`
   → Debe decir: "API escuchando en http://localhost:3000"

Prueba rápida (en otra terminal o Postman):
    POST http://localhost:3000/api/login
    body JSON: { "username": "admin", "password": "admin123" }
    → te devuelve un token.

Pídeselo a Claude Code así si te trabas:
> "Instala las dependencias del backend, corre el seed y arranca el servidor.
>  Si algo falla al conectar con MySQL, diagnostícalo."

---

## PASO 3 — App Android (Kotlin, Android Studio)

La app YA está creada en la carpeta `android/`. Para ejecutarla:

1. Abre **Android Studio** → "Open" → selecciona la carpeta `android/`.
2. Espera a que sincronice Gradle (la primera vez baja dependencias).
3. Arranca el emulador (o conecta un celular con depuración USB).
4. Presiona ▶ (Run 'app').

Pantallas incluidas:
- **Login** con usuario/contraseña (guarda el token JWT).
- **Registro** de nuevos usuarios (nombres, apellidos, cédula, grado, unidad +
  pregunta de seguridad).
- **Olvidé mi contraseña** por pregunta de seguridad.
- **Calendario de consumo**: eliges fecha y marcas desayuno/almuerzo/merienda;
  calcula el costo del día y muestra el total del mes.
- **Historial por meses** con el total de cada mes y del año.
- **Liquidación del mes**: desglose (raciones × precio) y total a pagar.

### Conexión con el backend (MUY IMPORTANTE)

1. El backend debe estar corriendo (`npm start` en `backend/`).
2. Abre el puerto 3000 en el Firewall de Windows: en la raíz del proyecto,
   clic derecho sobre `abrir_firewall_puerto3000.bat` → "Ejecutar como
   administrador" (solo una vez).
3. La app usa la IP LAN de tu PC (configurada en
   `android/app/src/main/java/com/brigada/confronta/data/ApiClient.kt`,
   constante `BASE_URL`). Sirve para el emulador **y** para un celular físico
   en la misma red WiFi.
4. Si tu PC cambia de IP (WiFi/DHCP), actualiza `BASE_URL`. Para ver tu IP
   actual: abre CMD y escribe `ipconfig` (busca "Dirección IPv4").
   - Alternativa solo para el emulador: `http://10.0.2.2:3000/`.

Usuario de prueba ya creado: **dario / dario123** (o el admin: admin / admin123).

---

## PASO 4 — Cómo se conecta con las 6 asignaturas (tu integrador)

- **Aplicaciones móviles:** la app Android (Kotlin + Retrofit).
- **Seguridad informática:** login con contraseñas hasheadas (bcrypt), tokens
  JWT, roles (ADMIN/OPERADOR/CONSULTA) y tabla de auditoría. Documenta el
  principio CID (Confidencialidad, Integridad, Disponibilidad).
- **Gestión de la configuración de software:** usa Git desde el inicio. Crea un
  repo, ramas (main/develop), y versiona. Pídeselo a Claude Code:
  "Inicializa git, crea un .gitignore para Node y Android, y haz el primer commit."
- **Gobierno de TI:** documenta cómo el sistema alinea TI con los objetivos de
  la brigada (eficiencia, control, reportes). Puedes mapear a COBIT/ITIL.
- **Construcción de documentos científicos:** el documento de tus compañeros;
  tú aportas la sección de desarrollo/resultados del aplicativo.
- **Unidad de integración:** este proyecto ES la integración de todo lo anterior.

---

## Orden recomendado para no perderte

1. MySQL corriendo + base importada.
2. Backend arriba y probado con login.
3. App Android: primero solo login, luego lista de personal, luego confronta,
   luego reportes. UNA pantalla a la vez.
4. Git desde el día 1 (commits pequeños y frecuentes).
5. Documentar en paralelo (capturas, decisiones de seguridad).

Cuando te atores en cualquier paso, describe el error EXACTO a Claude Code y
deja que lo depure contigo. No intentes hacer las 4 pantallas de golpe.
