// seed.js — crea/actualiza el usuario admin con contraseña hasheada real
// y su pregunta de seguridad. Ejecuta:  node seed.js
const bcrypt = require('bcryptjs');
const pool = require('./db');

const normalizar = (t) => String(t || '').trim().toLowerCase()
  .normalize('NFD').replace(/[̀-ͯ]/g, '');

(async () => {
  const username = 'admin';
  const passwordPlano = 'admin123';        // CÁMBIALA luego
  const preguntaSeg = '¿Nombre de tu primera mascota?';
  const respuestaSeg = 'firulais';          // respuesta de ejemplo

  const passHash = await bcrypt.hash(passwordPlano, 10);
  const respHash = await bcrypt.hash(normalizar(respuestaSeg), 10);

  await pool.query(`
    INSERT INTO usuario (username, password_hash, rol, pregunta_seguridad, respuesta_hash)
    VALUES (?,?, 'ADMIN', ?, ?)
    ON DUPLICATE KEY UPDATE
      password_hash = VALUES(password_hash),
      pregunta_seguridad = VALUES(pregunta_seguridad),
      respuesta_hash = VALUES(respuesta_hash)`,
    [username, passHash, preguntaSeg, respHash]);

  console.log(`Usuario "${username}" listo.`);
  console.log(`  Contraseña: ${passwordPlano}`);
  console.log(`  Pregunta de seguridad: ${preguntaSeg}  (respuesta: ${respuestaSeg})`);
  process.exit(0);
})();
