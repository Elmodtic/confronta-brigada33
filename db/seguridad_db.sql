-- =====================================================================
--  Confronta Diaria - Usuario de base de datos con privilegios minimos
--
--  Ejecutar como root UNA vez:
--    C:\xampp\mysql\bin\mysql.exe -u root < db\seguridad_db.sql
--
--  IMPORTANTE: sustituye CAMBIA_ESTA_CONTRASENA por una contrasena
--  propia ANTES de ejecutar el script, y usa esa misma en backend/.env.
--  Genera una con:
--    node -e "console.log(require('crypto').randomBytes(24).toString('base64url'))"
--
--  Luego en backend/.env:
--    DB_USER=confronta_app
--    DB_PASSWORD=CAMBIA_ESTA_CONTRASENA
--
--  La base real del proyecto es "confronta_brigada" (ver db/schema.sql).
--
--  NOTA MariaDB: no se usa REVOKE + FLUSH PRIVILEGES despues del GRANT
--  porque recarga las tablas de permisos y descarta la concesion recien
--  hecha. DROP USER + CREATE USER ya deja al usuario con cero privilegios
--  (solo USAGE), que es la revocacion total de cualquier acceso previo.
-- =====================================================================

-- 1) Borra el usuario si existia: elimina de raiz TODO privilegio previo
--    (incluidos los de administracion) y permite re-ejecutar el script.
DROP USER IF EXISTS 'confronta_app'@'localhost';
DROP USER IF EXISTS 'confronta_app'@'127.0.0.1';

-- 2) Crea el usuario dedicado, accesible SOLO desde la propia maquina.
--    Un usuario recien creado nace unicamente con USAGE (sin privilegios).
CREATE USER 'confronta_app'@'localhost'
  IDENTIFIED BY 'CAMBIA_ESTA_CONTRASENA';
CREATE USER 'confronta_app'@'127.0.0.1'
  IDENTIFIED BY 'CAMBIA_ESTA_CONTRASENA';

-- 3) Concede EXCLUSIVAMENTE operaciones CRUD sobre la base del proyecto.
--    Sin GRANT OPTION, sin privilegios globales (*.*), sin DROP/ALTER/CREATE,
--    sin FILE, PROCESS, SUPER, RELOAD ni SHUTDOWN.
GRANT SELECT, INSERT, UPDATE, DELETE
  ON `confronta_brigada`.*
  TO 'confronta_app'@'localhost';

GRANT SELECT, INSERT, UPDATE, DELETE
  ON `confronta_brigada`.*
  TO 'confronta_app'@'127.0.0.1';

-- 4) Verificacion: debe mostrar exactamente dos lineas por usuario:
--       GRANT USAGE ON *.* ...                        (sin privilegios globales)
--       GRANT SELECT, INSERT, UPDATE, DELETE ON `confronta_brigada`.* ...
SHOW GRANTS FOR 'confronta_app'@'localhost';
SHOW GRANTS FOR 'confronta_app'@'127.0.0.1';
