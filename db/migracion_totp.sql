-- =====================================================================
--  Verificacion en dos pasos (TOTP, RFC 6238)
--
--  El usuario inscribe su cuenta en Google Authenticator (o cualquier
--  app compatible) escaneando un QR, y desde ahi el ingreso pide
--  contrasena MAS un codigo de 6 digitos que cambia cada 30 segundos.
--
--  Notas de diseno:
--
--  * `totp_secreto` NO se guarda en claro. Se cifra con AES-256-GCM
--    usando TOTP_LLAVE del .env, porque quien lea la tabla con el
--    secreto en claro puede generar codigos validos para siempre.
--
--  * `totp_ultimo_paso` guarda el "time step" del ultimo codigo
--    aceptado. Sin esto, un codigo interceptado se puede reusar
--    durante su ventana de 30 segundos; con esto, cada codigo sirve
--    una sola vez (igual que los QR de comida).
--
--  * Los codigos de respaldo se guardan hasheados con bcrypt, como las
--    contrasenas: son credenciales de un solo uso y no deben poder
--    leerse desde la base.
--
--  Ejecutar:
--    C:\xampp\mysql\bin\mysql.exe -u root < db\migracion_totp.sql
-- =====================================================================
USE confronta_brigada;

ALTER TABLE usuario
  ADD COLUMN totp_secreto      VARCHAR(255) DEFAULT NULL COMMENT 'Base32 cifrado en AES-256-GCM',
  ADD COLUMN totp_activado     TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN totp_activado_en  DATETIME DEFAULT NULL,
  ADD COLUMN totp_ultimo_paso  BIGINT DEFAULT NULL COMMENT 'Anti-replay: time step del ultimo codigo usado';

CREATE TABLE IF NOT EXISTS codigo_respaldo (
  id_codigo   INT AUTO_INCREMENT PRIMARY KEY,
  id_usuario  INT NOT NULL,
  codigo_hash VARCHAR(255) NOT NULL,
  usado_en    DATETIME DEFAULT NULL,
  creado_en   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_respaldo_usuario FOREIGN KEY (id_usuario)
    REFERENCES usuario(id_usuario) ON DELETE CASCADE,
  INDEX idx_respaldo_usuario (id_usuario, usado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT 'TOTP listo' AS resultado;
