# Publicar el servidor fuera de la red de la unidad

El backend corre en una máquina de la brigada, pero la aplicación tiene
que funcionar desde cualquier teléfono, en cualquier red. Este documento
explica cómo se logra y cómo dejarlo andando en una máquina nueva.

## Por qué un túnel y no abrir un puerto

Abrir un puerto en el router no era una opción: la red de la unidad la
administra el departamento de TI, y por encima hay un CGNAT del
proveedor, de modo que la máquina no tiene una dirección pública propia.

La solución es un **túnel de Cloudflare**: `cloudflared` abre una
conexión *saliente* desde la máquina hacia Cloudflare, y Cloudflare
publica una dirección HTTPS que reenvía el tráfico por esa conexión ya
establecida. No hay puertos abiertos hacia adentro, no hace falta tocar
el router, y el certificado TLS lo pone Cloudflare.

```
Teléfono  ──HTTPS──>  Cloudflare  ──túnel saliente──>  Nginx  ──>  Node  ──>  MySQL
                                                       :443       :3000      :3306
```

Nginx está en medio para terminar TLS en la propia máquina, agregar las
cabeceras de seguridad y dejar el backend escuchando solo en local.

## Requisitos en la máquina servidora

| Componente | Dónde se espera |
|---|---|
| MySQL / MariaDB (XAMPP) | `C:\xampp\mysql\bin\mysqld.exe` |
| Node.js 18 o superior | en el `PATH` |
| Nginx | `C:\nginx\nginx.exe` |
| cloudflared | `C:\cloudflared\cloudflared.exe` |

Las rutas se pueden cambiar en `arrancar_todo.ps1`.

## Puesta en marcha, paso a paso

### 1. Base de datos

La migración crea el esquema completo y es segura de repetir: solo
aplica lo que falte.

```bash
cd backend
npm install
node migrar.js
```

**Se ejecuta con un usuario que pueda crear tablas** (`root` en una
instalación de XAMPP). El usuario con el que después corre la
aplicación no puede hacerlo, y eso es deliberado.

Luego se crea ese usuario restringido, que solo tiene permisos de
consulta, inserción, actualización y borrado sobre la base del proyecto:

```bash
C:\xampp\mysql\bin\mysql.exe -u root < db/seguridad_db.sql
```

Antes de ejecutarlo hay que reemplazar `CAMBIA_ESTA_CONTRASENA` por una
contraseña real. **Evita el carácter `#`**: `dotenv` lo interpreta como
el comienzo de un comentario y la conexión falla con un mensaje que no
apunta a la causa.

### 2. Variables de entorno

Copia `backend/.env.example` a `backend/.env` y complétalo. Dos valores
se generan, no se inventan:

```bash
node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"   # JWT_SECRET
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"   # TOTP_LLAVE
```

El servidor **no arranca** si `JWT_SECRET` está vacío, es un valor de
plantilla o mide menos de 32 caracteres, ni si `TOTP_LLAVE` no son 64
caracteres hexadecimales. Es a propósito: una configuración a medias se
descubre en el arranque y no en producción.

`TOTP_LLAVE` cifra los secretos de la verificación en dos pasos. Si se
pierde, nadie podrá validar códigos y habrá que reinscribir a todos los
usuarios desde la cuenta de administrador.

### 3. Certificado y Nginx

El repositorio incluye `certs/confronta.crt`, pero **no la llave
privada** (`certs/*.key` está en `.gitignore`, como debe ser). En una
máquina nueva se genera el par:

```bash
openssl req -x509 -nodes -days 825 -newkey rsa:2048 \
  -keyout certs/confronta.key -out certs/confronta.crt \
  -subj "/C=EC/ST=Tungurahua/L=Ambato/O=Brigada 33/CN=confronta.local" \
  -addext "subjectAltName=DNS:confronta.local,DNS:localhost,IP:127.0.0.1"
```

Copia `nginx.conf` a `C:\nginx\conf\nginx.conf` y ajusta las rutas
absolutas de `ssl_certificate` y `ssl_certificate_key`, que apuntan a la
carpeta del proyecto.

### 4. Firewall

`bloquear_puertos.bat` (como administrador) cierra hacia la red los
puertos que no deben exponerse: MySQL (3306), el backend directo (3000)
y Apache (80), que queda accesible solo desde la propia máquina para
entrar a phpMyAdmin. El único camino de entrada queda siendo el túnel.

Ojo: si el perfil de red activo es "Público" y ese perfil está
deshabilitado en el cortafuegos, las reglas no se aplican aunque el
comando diga que las creó. El `.bat` lo habilita antes de crearlas.

### 5. Arrancar todo

```bash
arrancar_todo.bat
```

Levanta MySQL, el backend, Nginx y el túnel en ese orden, esperando a
que cada uno responda antes de seguir. Al final imprime la dirección
pública y la guarda en `url_tunel.txt`.

Para levantar solo el túnel: `iniciar_tunel.bat`.

## Cómo encuentra la app al servidor

El túnel gratuito **asigna una dirección nueva cada vez que se
reinicia**. Avisarle a mano a cada usuario no escala, así que:

1. `iniciar_tunel.ps1` escribe la dirección vigente en `servidor.json` y
   lo sube al repositorio público.
2. La aplicación consulta ese archivo al abrirse y se reconfigura sola.
3. Si el ingreso falla por conexión, vuelve a consultarlo y reintenta
   una vez.

Por eso un reinicio del túnel dejó de ser un problema para los usuarios.
Si el push falla (sin internet, sin credenciales), el túnel igual queda
arriba y queda la vía manual: en la pantalla de ingreso, **mantener
pulsado el escudo** abre el campo de la dirección del servidor.

## Cuando algo no responde

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| La dirección da **530** | El túnel perdió la conexión con Cloudflare o Nginx se colgó | Reiniciar Nginx y después `cloudflared` |
| Responde en local pero no por el túnel | Nginx vivo pero sin atender | `Stop-Process -Name nginx` y volver a arrancarlo |
| `cloudflared` corriendo pero sin registrar nada en `tunel.log` | Proceso zombi | Reiniciarlo; la dirección cambiará |
| "No se pudo conectar" solo dentro de la red de la brigada | El DNS de la unidad (`10.20.4.251`) no resuelve los dominios `trycloudflare.com` | Activar DNS privado `dns.google` en Ajustes → Red del teléfono |
| El servidor no arranca y se cierra | `JWT_SECRET` o `TOTP_LLAVE` mal configurados | Leer el mensaje: dice cuál falta |

## Advertencia pendiente

Mientras el túnel esté activo, **la interfaz de programación queda
accesible desde internet**. Con datos de prueba es aceptable. Antes de
cargar cédulas reales del personal hay que cerrar el acceso: una red
privada tipo Tailscale, o Cloudflare Access con autenticación previa al
túnel. Está anotado como recomendación en el informe.
