// auth.js — middleware de seguridad (verifica el token JWT)
const jwt = require('jsonwebtoken');
require('dotenv').config();

// ===============================================================
// LISTA NEGRA (BLACKLIST) DE TOKENS EN MEMORIA
// Al hacer logout se guarda el identificador (jti) del token junto
// con su expiración. Cualquier petición posterior con ese token se
// rechaza. Se mantiene solo en RAM: al reiniciar el servidor se
// limpia (los tokens caducan solos en 15 minutos).
// ===============================================================
const tokensRevocados = new Map(); // jti -> exp (epoch en segundos)

// Elimina de la lista los tokens que ya caducaron (limpieza periódica).
function purgarRevocados() {
  const ahora = Math.floor(Date.now() / 1000);
  for (const [jti, exp] of tokensRevocados) {
    if (!exp || exp <= ahora) tokensRevocados.delete(jti);
  }
}
setInterval(purgarRevocados, 5 * 60 * 1000).unref();

// Revoca el token del usuario autenticado (se llama desde /api/logout).
function revocarToken(payload) {
  if (payload && payload.jti && payload.exp) {
    tokensRevocados.set(payload.jti, payload.exp);
  }
}

// Verifica el token JWT del encabezado Authorization: "Bearer <token>".
function verificarToken(req, res, next) {
  const header = req.headers['authorization'] || '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : null;
  if (!token) return res.status(401).json({ error: 'Token no proporcionado' });

  jwt.verify(token, process.env.JWT_SECRET, (err, usuario) => {
    if (err) return res.status(403).json({ error: 'Token inválido o expirado' });
    if (usuario.jti && tokensRevocados.has(usuario.jti)) {
      return res.status(401).json({ error: 'Sesión finalizada. Inicia sesión de nuevo.' });
    }
    req.usuario = usuario; // { id_usuario, username, rol, id_personal, jti, iat, exp }
    next();
  });
}

// Restringe una ruta a ciertos roles, ej: soloRol('ADMIN')
function soloRol(...roles) {
  return (req, res, next) => {
    if (!roles.includes(req.usuario.rol)) {
      return res.status(403).json({ error: 'No tienes permisos para esta acción' });
    }
    next();
  };
}

module.exports = { verificarToken, soloRol, revocarToken, tokensRevocados };
