-- =====================================================================
--  Relevo de cargos (acta de entrega-recepcion)
--
--  Solo puede haber UN tesorero y UN ranchero activos a la vez. Cuando
--  se cambia al titular, el cargo se releva: el saliente entrega y el
--  entrante recibe, y queda constancia de cuanto dinero se traspaso.
--
--  Que se traspasa y que NO:
--    SI  el fondo operativo del rancho (dinero para comprar viveres).
--    NO  el saldo de comensal, que es plata personal de cada quien para
--        su propia comida y sigue con la persona.
--
--  El fondo del ranchero pasa a calcularse asi:
--    entregas confirmadas - gastos + relevos recibidos - relevos entregados
--
--  Para el tesorero la caja es institucional (recaudado - entregado), no
--  personal, asi que no cambia de dueno; el monto se guarda solo como
--  constancia del efectivo que se paso de mano.
--
--  Ejecutar:
--    C:\xampp\mysql\bin\mysql.exe -u root < db\migracion_relevo.sql
-- =====================================================================
USE confronta_brigada;

CREATE TABLE IF NOT EXISTS relevo (
  id_relevo    INT AUTO_INCREMENT PRIMARY KEY,
  rol          ENUM('TESORERO','RANCHERO') NOT NULL,
  id_saliente  INT DEFAULT NULL,          -- NULL si el cargo estaba vacante
  id_entrante  INT NOT NULL,
  monto        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  observacion  VARCHAR(255) DEFAULT NULL,
  id_admin     INT NOT NULL,              -- quien autorizo el relevo
  fecha_hora   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_relevo_saliente FOREIGN KEY (id_saliente) REFERENCES usuario(id_usuario),
  CONSTRAINT fk_relevo_entrante FOREIGN KEY (id_entrante) REFERENCES usuario(id_usuario),
  CONSTRAINT fk_relevo_admin    FOREIGN KEY (id_admin)    REFERENCES usuario(id_usuario),
  INDEX idx_relevo_rol (rol),
  INDEX idx_relevo_fecha (fecha_hora)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT 'Tabla relevo lista' AS resultado;
DESCRIBE relevo;
