-- =====================================================================
--  Confirmación de entregas por QR (tesorero -> ranchero)
--
--  La entrega deja de ser un asiento inmediato y pasa a tener estados:
--
--    PENDIENTE  el tesorero registró la entrega y generó un QR.
--               El dinero sale de la caja disponible (queda EN TRÁNSITO)
--               pero todavía NO acredita el fondo del rancho.
--    CONFIRMADA el ranchero escaneó el QR. Recién ahí se acredita el
--               fondo. Es la prueba de que efectivamente recibió.
--    ANULADA    el tesorero canceló una entrega que nadie confirmó.
--               El dinero regresa a la caja.
--
--  Así el ranchero verifica lo que recibe y el tesorero no puede dar por
--  entregado algo que el otro nunca aceptó.
--
--  Ejecutar:  C:\xampp\mysql\bin\mysql.exe -u root < db\migracion_transferencia_qr.sql
-- =====================================================================
USE confronta_brigada;

ALTER TABLE transferencia
  ADD COLUMN token          CHAR(32)    NULL AFTER concepto,
  ADD COLUMN estado         ENUM('PENDIENTE','CONFIRMADA','ANULADA') NOT NULL DEFAULT 'PENDIENTE' AFTER token,
  ADD COLUMN confirmado_en  DATETIME    NULL AFTER estado,
  ADD COLUMN anulado_en     DATETIME    NULL AFTER confirmado_en;

-- El token es de un solo uso: no puede repetirse.
ALTER TABLE transferencia
  ADD UNIQUE KEY uq_transferencia_token (token);

ALTER TABLE transferencia
  ADD INDEX idx_transf_estado (estado);

SELECT 'Tabla transferencia migrada' AS resultado;
DESCRIBE transferencia;
