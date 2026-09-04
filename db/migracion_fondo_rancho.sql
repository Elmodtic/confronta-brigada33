-- =====================================================================
--  Fondo rotativo del rancho
--  El TESORERO recauda de los comensales (tabla recarga) y entrega parte
--  de ese dinero al RANCHERO para la compra de víveres. Cada entrega es
--  una transferencia de fondos y queda registrada aquí.
--
--    Caja del tesorero = SUM(recarga.monto) - SUM(transferencia.monto)
--    Fondo del rancho  = SUM(transferencia.monto) recibido por el ranchero
--
--  Ejecutar:  C:\xampp\mysql\bin\mysql.exe -u root < db\migracion_fondo_rancho.sql
-- =====================================================================
USE confronta_brigada;

CREATE TABLE IF NOT EXISTS transferencia (
  id_transferencia INT AUTO_INCREMENT PRIMARY KEY,
  id_tesorero      INT NOT NULL,
  id_ranchero      INT NOT NULL,
  monto            DECIMAL(10,2) NOT NULL,
  concepto         VARCHAR(255) DEFAULT NULL,
  fecha_hora       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_transf_tesorero FOREIGN KEY (id_tesorero) REFERENCES usuario(id_usuario),
  CONSTRAINT fk_transf_ranchero FOREIGN KEY (id_ranchero) REFERENCES usuario(id_usuario),
  INDEX idx_transf_fecha (fecha_hora),
  INDEX idx_transf_ranchero (id_ranchero)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT 'Tabla transferencia lista' AS resultado;
DESCRIBE transferencia;
