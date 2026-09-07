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
    // El token parcial que se entrega entre la contraseña y el código de
    // seis dígitos lleva `paso` y NO vale como sesión: sin esto, quien
    // sepa la contraseña se saltaría el segundo factor simplemente
    // usando ese token contra el resto de la API.
    if (usuario.paso) {
      return res.status(401).json({ error: 'Falta completar la verificación en dos pasos' });
    }

    // La inscripción en dos pasos es obligatoria salvo para el ADMIN. Se
    // exige aquí, en el único punto por el que pasan TODAS las rutas
    // protegidas, y no en cada una: una ruta nueva queda cubierta sola.
    // Imponerlo solo en la app no serviría de nada, porque cualquiera
    // puede llamar la API por su cuenta.
    if (usuario.rol !== 'ADMIN' && !usuario.totp && !RUTAS_SIN_SEGUNDO_FACTOR.has(req.path)) {
      return res.status(403).json({
        error: 'Debes activar la verificación en dos pasos para usar la aplicación.',
        requiere_inscripcion_totp: true,
      });
    }

    req.usuario = usuario; // { id_usuario, username, rol, id_personal, totp, jti, iat, exp }
    next();
  });
}

// Lo mínimo para poder inscribirse (o salir) sin haberlo hecho todavía.
// Cualquier otra ruta queda bloqueada hasta que active el segundo factor.
const RUTAS_SIN_SEGUNDO_FACTOR = new Set([
  '/api/logout',
  '/api/mi/perfil',
  '/api/mi/totp',
  '/api/mi/totp/iniciar',
  '/api/mi/totp/activar',
]);

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
