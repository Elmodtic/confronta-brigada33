// resetcatalogos.js — DESTRUCTIVO: reemplaza grados y unidades por los oficiales.
// Borra datos de prueba dependientes (confronta, personal, auditoría y usuarios
// que no sean ADMIN). Conserva la(s) cuenta(s) ADMIN/root.
// Ejecuta:  node resetcatalogos.js
const mysql = require('mysql2/promise');
const { GRADOS, UNIDADES } = require('./catalogos');
require('dotenv').config();

(async () => {
  const conn = await mysql.createConnection({
    host: process.env.DB_HOST,
    port: process.env.DB_PORT,
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME,
    multipleStatements: true,
  });

  console.log('Reiniciando catálogos oficiales...');
  await conn.query('SET FOREIGN_KEY_CHECKS=0');

  await conn.query('DELETE FROM ticket').catch(() => {});
  await conn.query('DELETE FROM recarga').catch(() => {});
  await conn.query('DELETE FROM confronta');
  await conn.query('DELETE FROM auditoria');
  await conn.query('UPDATE usuario SET id_personal = NULL, saldo = 0');
  await conn.query('DELETE FROM personal');
  await conn.query("DELETE FROM usuario WHERE rol <> 'ADMIN'");
  await conn.query('DELETE FROM grado');
  await conn.query('DELETE FROM unidad');
  await conn.query('ALTER TABLE grado AUTO_INCREMENT = 1');
  await conn.query('ALTER TABLE unidad AUTO_INCREMENT = 1');
  await conn.query('ALTER TABLE personal AUTO_INCREMENT = 1');

  await conn.query('INSERT INTO grado (nombre, abreviatura) VALUES ?',
    [GRADOS.map(([n, a]) => [n, a])]);
  await conn.query('INSERT INTO unidad (nombre, siglas) VALUES ?',
    [UNIDADES.map(([n, s]) => [n, s])]);

  await conn.query('SET FOREIGN_KEY_CHECKS=1');

  console.log(`  ${GRADOS.length} grados y ${UNIDADES.length} unidades cargados.`);
  console.log('Listo. (Se conservaron las cuentas ADMIN/root.)');
  await conn.end();
  process.exit(0);
})().catch((e) => {
  console.error('Error:', e.message);
  process.exit(1);
});
