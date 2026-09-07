-- =====================================================================
--  Recuperacion de cuentas: reinicio por el administrador
--
--  Reemplaza los codigos de respaldo. Antes, quien perdia el telefono
--  entraba con un codigo de papel; ahora acude al administrador, que es
--  como funciona la cadena de mando en la unidad y evita que la unica
--  proteccion de una cuenta sea un papel guardado en cualquier parte.
--
--  Flujo:
--    1. El admin busca al usuario por cedula y reinicia su contrasena.
--       La temporal es la propia cedula, y se apaga su segundo factor.
--    2. El usuario entra con cedula / cedula. El servidor no le deja
--       hacer nada hasta que cambie la contrasena (debe_cambiar_password).
--    3. Cambiada la contrasena, se le exige inscribir de nuevo el OTP.
--
--  `password_temporal_hasta` acota el riesgo: la cedula es un dato
--  publico, asi que una contrasena igual a la cedula es adivinable. La
--  temporal solo sirve 24 horas; pasadas, el admin la reinicia otra vez.
--
--  La pregunta de seguridad NO entra aqui: sirve para quien olvido la
--  contrasena pero conserva el telefono, y por eso no toca el OTP.
--
--  Ejecutar:
--    C:\xampp\mysql\bin\mysql.exe -u root < db\migracion_recuperacion.sql
-- =====================================================================
USE confronta_brigada;

ALTER TABLE usuario
  ADD COLUMN debe_cambiar_password  TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN password_temporal_hasta DATETIME DEFAULT NULL
    COMMENT 'Vencimiento de la contrasena temporal puesta por el admin';

-- Los codigos de respaldo se retiran: la recuperacion pasa por el admin.
DROP TABLE IF EXISTS codigo_respaldo;

SELECT 'Recuperacion por el administrador lista' AS resultado;
