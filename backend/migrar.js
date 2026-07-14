// migrar.js — crea/actualiza el esquema de la base de datos de forma idempotente.
// Se puede correr las veces que quieras; solo aplica lo que falte.
//   node migrar.js
const mysql = require('mysql2/promise');
const { GRADOS, UNIDADES } = require('./catalogos');
require('dotenv').config();

const DB = process.env.DB_NAME;

(async () => {
  const conn = await mysql.createConnection({
    host: process.env.DB_HOST,
    port: process.env.DB_PORT,
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    multipleStatements: true,
  });

  const colExiste = async (tabla, col) => {
    const [r] = await conn.query(
      `SELECT COUNT(*) c FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND COLUMN_NAME=?`,
      [DB, tabla, col]);
    return r[0].c > 0;
  };
  const addCol = async (tabla, col, ddl) => {
    if (!(await colExiste(tabla, col))) {
      await conn.query(`ALTER TABLE \`${tabla}\` ADD COLUMN ${ddl}`);
      console.log(`  + columna ${tabla}.${col}`);
    }
  };
  const fkExiste = async (nombre) => {
    const [r] = await conn.query(
      `SELECT COUNT(*) c FROM information_schema.TABLE_CONSTRAINTS
       WHERE CONSTRAINT_SCHEMA=? AND CONSTRAINT_NAME=? AND CONSTRAINT_TYPE='FOREIGN KEY'`,
      [DB, nombre]);
    return r[0].c > 0;
  };
  const filas = async (tabla) => {
    const [r] = await conn.query(`SELECT COUNT(*) c FROM \`${tabla}\``);
    return r[0].c;
  };

  console.log(`Migrando base "${DB}"...`);

  await conn.query(
    `CREATE DATABASE IF NOT EXISTS \`${DB}\`
       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`);
  await conn.query(`USE \`${DB}\``);

  await conn.query(`
    CREATE TABLE IF NOT EXISTS grado (
      id_grado    INT AUTO_INCREMENT PRIMARY KEY,
      nombre      VARCHAR(60) NOT NULL,
      abreviatura VARCHAR(15) NOT NULL,
      UNIQUE(nombre)
    ) ENGINE=InnoDB`);

  await conn.query(`
    CREATE TABLE IF NOT EXISTS unidad (
      id_unidad   INT AUTO_INCREMENT PRIMARY KEY,
      nombre      VARCHAR(120) NOT NULL,
      siglas      VARCHAR(20),
      descripcion VARCHAR(255)
    ) ENGINE=InnoDB`);

  await conn.query(`
    CREATE TABLE IF NOT EXISTS personal (
      id_personal INT AUTO_INCREMENT PRIMARY KEY,
      cedula      VARCHAR(15) NOT NULL,
      nombres     VARCHAR(100) NOT NULL,
      apellidos   VARCHAR(100) NOT NULL,
      id_grado    INT NOT NULL,
      id_unidad   INT NOT NULL,
      activo      TINYINT(1) NOT NULL DEFAULT 1,
      UNIQUE(cedula),
      FOREIGN KEY (id_grado)  REFERENCES grado(id_grado),
      FOREIGN KEY (id_unidad) REFERENCES unidad(id_unidad)
    ) ENGINE=InnoDB`);

  await conn.query(`
    CREATE TABLE IF NOT EXISTS usuario (
      id_usuario    INT AUTO_INCREMENT PRIMARY KEY,
      username      VARCHAR(50)  NOT NULL,
      password_hash VARCHAR(255) NOT NULL,
      rol           ENUM('ADMIN','OPERADOR','CONSULTA','RANCHERO','TESORERO') NOT NULL DEFAULT 'OPERADOR',
      activo        TINYINT(1)   NOT NULL DEFAULT 1,
      id_personal   INT NULL,
      saldo         DECIMAL(8,2) NOT NULL DEFAULT 0,
      pregunta_seguridad VARCHAR(255) NULL,
      respuesta_hash     VARCHAR(255) NULL,
      intentos_fallidos  INT NOT NULL DEFAULT 0,
      bloqueado_hasta    DATETIME NULL,
      creado_en     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
      UNIQUE(username)
    ) ENGINE=InnoDB`);

  await conn.query(`
    CREATE TABLE IF NOT EXISTS confronta (
      id_confronta  INT AUTO_INCREMENT PRIMARY KEY,
      id_personal   INT NOT NULL,
      fecha         DATE NOT NULL,
      estado        ENUM('PRESENTE','PERMISO','COMISION','OPERACION','FRANCO')
                    NOT NULL DEFAULT 'PRESENTE',
      desayuno      TINYINT(1) NOT NULL DEFAULT 0,
      almuerzo      TINYINT(1) NOT NULL DEFAULT 0,
      merienda      TINYINT(1) NOT NULL DEFAULT 0,
      novedad       VARCHAR(255),
      id_usuario    INT NOT NULL,
      registrado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      UNIQUE(id_personal, fecha),
      FOREIGN KEY (id_personal) REFERENCES personal(id_personal),
      FOREIGN KEY (id_usuario)  REFERENCES usuario(id_usuario)
    ) ENGINE=InnoDB`);

  await conn.query(`
    CREATE TABLE IF NOT EXISTS auditoria (
      id_auditoria INT AUTO_INCREMENT PRIMARY KEY,
      id_usuario   INT,
      accion       VARCHAR(100) NOT NULL,
      detalle      VARCHAR(255),
      fecha_hora   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
    ) ENGINE=InnoDB`);

  await conn.query(`
    CREATE TABLE IF NOT EXISTS tarifa (
      id_tarifa     INT AUTO_INCREMENT PRIMARY KEY,
      desayuno      DECIMAL(6,2) NOT NULL,
      almuerzo      DECIMAL(6,2) NOT NULL,
      merienda      DECIMAL(6,2) NOT NULL,
      vigente_desde DATE NOT NULL,
      creado_en     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB`);

  // Recargas de saldo hechas por el tesorero (bitácora)
  await conn.query(`
    CREATE TABLE IF NOT EXISTS recarga (
      id_recarga  INT AUTO_INCREMENT PRIMARY KEY,
      id_usuario  INT NOT NULL,
      monto       DECIMAL(8,2) NOT NULL,
      id_tesorero INT NOT NULL,
      fecha_hora  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (id_usuario)  REFERENCES usuario(id_usuario),
      FOREIGN KEY (id_tesorero) REFERENCES usuario(id_usuario)
    ) ENGINE=InnoDB`);

  // Tickets QR de un solo uso (una comida reservada -> se canjea al comer)
  await conn.query(`
    CREATE TABLE IF NOT EXISTS ticket (
      id_ticket   INT AUTO_INCREMENT PRIMARY KEY,
      token       VARCHAR(64) NOT NULL,
      id_usuario  INT NOT NULL,
      fecha       DATE NOT NULL,
      comida      ENUM('DESAYUNO','ALMUERZO','MERIENDA') NOT NULL,
      precio      DECIMAL(6,2) NOT NULL,
      estado      ENUM('ACTIVO','CANJEADO','ANULADO') NOT NULL DEFAULT 'ACTIVO',
      creado_en   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      canjeado_en TIMESTAMP NULL,
      id_canjeador INT NULL,
      UNIQUE(token),
      FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
    ) ENGINE=InnoDB`);

  // --- columnas/ajustes idempotentes para bases ya existentes ---
  await addCol('usuario', 'id_personal', 'id_personal INT NULL');
  await addCol('usuario', 'pregunta_seguridad', 'pregunta_seguridad VARCHAR(255) NULL');
  await addCol('usuario', 'respuesta_hash', 'respuesta_hash VARCHAR(255) NULL');
  await addCol('unidad', 'siglas', 'siglas VARCHAR(20)');
  await addCol('usuario', 'saldo', 'saldo DECIMAL(8,2) NOT NULL DEFAULT 0');
  await addCol('usuario', 'intentos_fallidos', 'intentos_fallidos INT NOT NULL DEFAULT 0');
  await addCol('usuario', 'bloqueado_hasta', 'bloqueado_hasta DATETIME NULL');

  // Asegura que los roles nuevos existan en el enum (seguro de re-ejecutar)
  await conn.query(`
    ALTER TABLE usuario MODIFY COLUMN rol
      ENUM('ADMIN','OPERADOR','CONSULTA','RANCHERO','TESORERO') NOT NULL DEFAULT 'OPERADOR'`);

  if (!(await fkExiste('fk_usuario_personal'))) {
    try {
      await conn.query(`
        ALTER TABLE usuario ADD CONSTRAINT fk_usuario_personal
        FOREIGN KEY (id_personal) REFERENCES personal(id_personal)`);
      console.log('  + FK usuario.id_personal -> personal');
    } catch (e) {
      console.log('  (aviso) FK usuario.id_personal:', e.message);
    }
  }

  // --- semillas (solo si están vacías) ---
  if ((await filas('tarifa')) === 0) {
    await conn.query(
      `INSERT INTO tarifa (desayuno, almuerzo, merienda, vigente_desde)
       VALUES (1.90, 3.00, 1.75, '2020-01-01')`);
    console.log('  + tarifa inicial 1.90 / 3.00 / 1.75');
  }
  if ((await filas('grado')) === 0) {
    await conn.query('INSERT INTO grado (nombre, abreviatura) VALUES ?',
      [GRADOS.map(([n, a]) => [n, a])]);
    console.log(`  + ${GRADOS.length} grados`);
  }
  if ((await filas('unidad')) === 0) {
    await conn.query('INSERT INTO unidad (nombre, siglas) VALUES ?',
      [UNIDADES.map(([n, s]) => [n, s])]);
    console.log(`  + ${UNIDADES.length} unidades`);
  }

  console.log('Migración completa.');
  await conn.end();
  process.exit(0);
})().catch((e) => {
  console.error('Error en la migración:', e.message);
  process.exit(1);
});
