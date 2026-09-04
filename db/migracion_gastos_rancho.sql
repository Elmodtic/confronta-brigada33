-- =====================================================================
--  Gastos del fondo del rancho
--
--  El ranchero recibe fondos del tesorero (tabla transferencia) y los
--  gasta comprando viveres. Sin registrar esas compras el fondo nunca
--  bajaria y el dinero pareceria infinito.
--
--    Fondo disponible = entregas CONFIRMADAS - gastos registrados
--
--  Ejecutar:
--    C:\xampp\mysql\bin\mysql.exe -u root < db\migracion_gastos_rancho.sql
-- =====================================================================
USE confronta_brigada;

CREATE TABLE IF NOT EXISTS gasto_rancho (
  id_gasto     INT AUTO_INCREMENT PRIMARY KEY,
  id_ranchero  INT NOT NULL,
  monto        DECIMAL(10,2) NOT NULL,
  categoria    VARCHAR(30) NOT NULL DEFAULT 'OTROS',
  detalle      VARCHAR(255) DEFAULT NULL,
  fecha_hora   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_gasto_ranchero FOREIGN KEY (id_ranchero) REFERENCES usuario(id_usuario),
  INDEX idx_gasto_fecha (fecha_hora),
  INDEX idx_gasto_ranchero (id_ranchero)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT 'Tabla gasto_rancho lista' AS resultado;
DESCRIBE gasto_rancho;
