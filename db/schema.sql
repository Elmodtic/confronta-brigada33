-- =====================================================================
-- BASE DE DATOS: Sistema de Confronta Diaria
-- Brigada de Comunicaciones N.º 33 "Rumiñahui"
-- Motor: MySQL (XAMPP)
-- =====================================================================

CREATE DATABASE IF NOT EXISTS confronta_brigada
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE confronta_brigada;

-- ---------------------------------------------------------------------
-- Grados militares (catálogo)
-- ---------------------------------------------------------------------
CREATE TABLE grado (
  id_grado      INT AUTO_INCREMENT PRIMARY KEY,
  nombre        VARCHAR(60) NOT NULL,
  abreviatura   VARCHAR(15) NOT NULL,
  UNIQUE(nombre)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Unidades / compañías dentro de la brigada
-- ---------------------------------------------------------------------
CREATE TABLE unidad (
  id_unidad     INT AUTO_INCREMENT PRIMARY KEY,
  nombre        VARCHAR(120) NOT NULL,
  siglas        VARCHAR(20),
  descripcion   VARCHAR(255)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Usuarios del sistema (login) — SEGURIDAD INFORMÁTICA
-- La contraseña se guarda SIEMPRE como hash (bcrypt), nunca en texto.
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
  id_usuario    INT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  rol           ENUM('ADMIN','OPERADOR','CONSULTA','RANCHERO','TESORERO') NOT NULL DEFAULT 'OPERADOR',
  activo        TINYINT(1)   NOT NULL DEFAULT 1,
  -- Vínculo opcional con la ficha de personal (una cuenta = una persona)
  id_personal   INT NULL,
  -- Saldo prepago (lo recarga el tesorero; se descuenta al comer)
  saldo         DECIMAL(8,2) NOT NULL DEFAULT 0,
  -- Recuperación de contraseña por pregunta de seguridad (sin correo)
  pregunta_seguridad VARCHAR(255) NULL,
  respuesta_hash     VARCHAR(255) NULL,   -- hash bcrypt de la respuesta secreta
  -- Bloqueo por intentos fallidos de inicio de sesión
  intentos_fallidos  INT NOT NULL DEFAULT 0,
  bloqueado_hasta    DATETIME NULL,
  creado_en     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(username)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Personal militar
-- ---------------------------------------------------------------------
CREATE TABLE personal (
  id_personal   INT AUTO_INCREMENT PRIMARY KEY,
  cedula        VARCHAR(15) NOT NULL,
  nombres       VARCHAR(100) NOT NULL,
  apellidos     VARCHAR(100) NOT NULL,
  id_grado      INT NOT NULL,
  id_unidad     INT NOT NULL,
  activo        TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE(cedula),
  FOREIGN KEY (id_grado)  REFERENCES grado(id_grado),
  FOREIGN KEY (id_unidad) REFERENCES unidad(id_unidad)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Confronta diaria (el corazón del sistema)
-- Estado de cada persona en una fecha: presente / permiso / comisión /
-- operación, y qué raciones consume.
-- ---------------------------------------------------------------------
CREATE TABLE confronta (
  id_confronta  INT AUTO_INCREMENT PRIMARY KEY,
  id_personal   INT NOT NULL,
  fecha         DATE NOT NULL,
  estado        ENUM('PRESENTE','PERMISO','COMISION','OPERACION','FRANCO')
                NOT NULL DEFAULT 'PRESENTE',
  desayuno      TINYINT(1) NOT NULL DEFAULT 0,
  almuerzo      TINYINT(1) NOT NULL DEFAULT 0,
  merienda      TINYINT(1) NOT NULL DEFAULT 0,
  novedad       VARCHAR(255),
  id_usuario    INT NOT NULL,          -- quién registró (trazabilidad)
  registrado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(id_personal, fecha),          -- una confronta por persona por día
  FOREIGN KEY (id_personal) REFERENCES personal(id_personal),
  FOREIGN KEY (id_usuario)  REFERENCES usuario(id_usuario)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Auditoría — SEGURIDAD INFORMÁTICA (trazabilidad de acciones)
-- ---------------------------------------------------------------------
CREATE TABLE auditoria (
  id_auditoria  INT AUTO_INCREMENT PRIMARY KEY,
  id_usuario    INT,
  accion        VARCHAR(100) NOT NULL,
  detalle       VARCHAR(255),
  fecha_hora    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Tarifas — precio de cada ración (permite historial si cambian)
-- El costo de una confronta se calcula con la tarifa vigente a su fecha.
-- ---------------------------------------------------------------------
CREATE TABLE tarifa (
  id_tarifa     INT AUTO_INCREMENT PRIMARY KEY,
  desayuno      DECIMAL(6,2) NOT NULL,
  almuerzo      DECIMAL(6,2) NOT NULL,
  merienda      DECIMAL(6,2) NOT NULL,
  vigente_desde DATE NOT NULL,
  creado_en     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Vínculo cuenta de usuario -> ficha de personal (se agrega aquí porque
-- la tabla personal se crea después que usuario).
ALTER TABLE usuario
  ADD CONSTRAINT fk_usuario_personal
  FOREIGN KEY (id_personal) REFERENCES personal(id_personal);

-- ---------------------------------------------------------------------
-- Recargas de saldo (bitácora de lo que carga el tesorero)
-- ---------------------------------------------------------------------
CREATE TABLE recarga (
  id_recarga  INT AUTO_INCREMENT PRIMARY KEY,
  id_usuario  INT NOT NULL,
  monto       DECIMAL(8,2) NOT NULL,
  id_tesorero INT NOT NULL,
  fecha_hora  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (id_usuario)  REFERENCES usuario(id_usuario),
  FOREIGN KEY (id_tesorero) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Tickets QR de un solo uso: cada comida reservada genera un QR que, al
-- canjearse en el comedor, descuenta el saldo del usuario.
-- ---------------------------------------------------------------------
CREATE TABLE ticket (
  id_ticket    INT AUTO_INCREMENT PRIMARY KEY,
  token        VARCHAR(64) NOT NULL,
  id_usuario   INT NOT NULL,
  fecha        DATE NOT NULL,
  comida       ENUM('DESAYUNO','ALMUERZO','MERIENDA') NOT NULL,
  precio       DECIMAL(6,2) NOT NULL,
  estado       ENUM('ACTIVO','CANJEADO','ANULADO') NOT NULL DEFAULT 'ACTIVO',
  creado_en    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  canjeado_en  TIMESTAMP NULL,
  id_canjeador INT NULL,
  UNIQUE(token),
  FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB;

-- =====================================================================
-- DATOS SEMILLA (para probar de inmediato)
-- =====================================================================
-- Grados de la Fuerza Terrestre (del más alto al más bajo)
INSERT INTO grado (nombre, abreviatura) VALUES
  ('General de Ejército','GRAE'), ('General de División','GRAD'),
  ('General de Brigada','GRAB'), ('Coronel','CRNL'),
  ('Teniente Coronel','TCRN'), ('Mayor','MAYO'), ('Capitán','CAPT'),
  ('Teniente','TNTE'), ('Subteniente','SUBT'), ('Suboficial Mayor','SUBM'),
  ('Suboficial Primero','SUBP'), ('Suboficial Segundo','SUBS'),
  ('Sargento Primero','SGTP'), ('Sargento Segundo','SGTS'),
  ('Cabo Primero','CBOP'), ('Cabo Segundo','CBOS'), ('Soldado','SLDO');

-- Unidades de la Brigada
INSERT INTO unidad (nombre, siglas) VALUES
  ('COMANDO DE APOYO LOGÍSTICO ELECTRÓNICO NRO. 33','CALE33'),
  ('BATALLÓN DE COMUNICACIONES NRO. 98','BC98'),
  ('COMANDO Y ESTADO MAYOR NRO. 33','CEM33'),
  ('CENTRO DE METROLOGÍA DE LA FUERZA TERRESTRE','C.MET.F.T'),
  ('GRUPO DE CIBERDEFENSA Y GUERRA ELECTRÓNICA NRO. 97','GRUCIGE97'),
  ('COMPAÑÍA POLICÍA MILITAR NRO. 33','CPM33'),
  ('POLICLÍNICO NRO. 33','POL33'),
  ('COMPAÑÍA LOGÍSTICA NRO. 33','CLOG33'),
  ('BATALLÓN DE INFORMÁTICA NRO. 99','BINFO99');

-- Usuario admin de prueba. La contraseña real se define desde el backend
-- (script de siembra) usando bcrypt; este hash es de ejemplo: "admin123"
INSERT INTO usuario (username, password_hash, rol) VALUES
  ('admin', '$2b$10$REEMPLAZAR_CON_HASH_REAL', 'ADMIN');

-- (El personal se crea al registrar usuarios desde la app.)

-- Tarifa inicial de las raciones (en dólares)
INSERT INTO tarifa (desayuno, almuerzo, merienda, vigente_desde) VALUES
  (1.90, 3.00, 1.75, '2020-01-01');
