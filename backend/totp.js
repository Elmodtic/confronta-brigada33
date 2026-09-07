// totp.js — verificación en dos pasos (TOTP, RFC 6238)
//
// El usuario inscribe su cuenta en Google Authenticator escaneando un QR
// y desde ahí el ingreso pide contraseña MÁS un código de seis dígitos
// que cambia cada 30 segundos.
//
// Este archivo concentra lo criptográfico: cifrado del secreto, la
// verificación del código y los códigos de respaldo. Los endpoints
// viven en server.js.
const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const otp = require('otplib');
require('dotenv').config();

// --- Llave de cifrado -------------------------------------------------
// El secreto TOTP es una credencial permanente: quien lo lee genera
// códigos válidos para siempre. Por eso NO se guarda en claro, ni
// siquiera confiando en que la base solo la abre el usuario de la app.
const LLAVE_HEX = String(process.env.TOTP_LLAVE || '').trim();

function llave() {
  if (!/^[0-9a-fA-F]{64}$/.test(LLAVE_HEX)) {
    throw new Error(
      'TOTP_LLAVE debe ser de 64 caracteres hexadecimales (32 bytes). ' +
      'Genérala con: node -e "console.log(require(\'crypto\').randomBytes(32).toString(\'hex\'))"');
  }
  return Buffer.from(LLAVE_HEX, 'hex');
}

// Se valida al arrancar para no descubrir el problema recién cuando
// alguien intente inscribirse.
function comprobarConfiguracion() {
  llave();
}

// --- Cifrado del secreto (AES-256-GCM) --------------------------------
// GCM y no CBC porque además de cifrar autentica: si alguien altera la
// columna en la base, el descifrado falla en vez de devolver basura.
// Formato guardado: iv.tagDeAutenticacion.textoCifrado (todo en hex).
function cifrar(texto) {
  const iv = crypto.randomBytes(12);
  const c = crypto.createCipheriv('aes-256-gcm', llave(), iv);
  const cifrado = Buffer.concat([c.update(texto, 'utf8'), c.final()]);
  return [iv.toString('hex'), c.getAuthTag().toString('hex'), cifrado.toString('hex')].join('.');
}

function descifrar(guardado) {
  const [ivHex, tagHex, datosHex] = String(guardado).split('.');
  if (!ivHex || !tagHex || !datosHex) throw new Error('Secreto TOTP con formato inválido');
  const d = crypto.createDecipheriv('aes-256-gcm', llave(), Buffer.from(ivHex, 'hex'));
  d.setAuthTag(Buffer.from(tagHex, 'hex'));
  return Buffer.concat([d.update(Buffer.from(datosHex, 'hex')), d.final()]).toString('utf8');
}

// --- Inscripción ------------------------------------------------------
const EMISOR = 'Confronta B-33';

async function nuevoSecreto() {
  return otp.generateSecret();
}

// URI otpauth:// que la app de autenticación entiende. Es lo que se
// pinta como QR en el teléfono.
async function uriDeInscripcion(secreto, username) {
  return otp.generateURI({ secret: secreto, label: username, issuer: EMISOR });
}

// --- Verificación -----------------------------------------------------
// `epochTolerance` da 30 segundos de margen hacia atrás y hacia adelante,
// porque el reloj del teléfono nunca coincide exactamente con el del
// servidor. `afterTimeStep` rechaza un código ya usado: sin eso, quien
// vea el código por encima del hombro puede reusarlo dentro de su
// ventana de 30 segundos.
const TOLERANCIA_SEGUNDOS = 30;

async function verificarCodigo(secretoCifrado, codigo, ultimoPaso) {
  const limpio = String(codigo || '').replace(/\s/g, '');
  if (!/^\d{6}$/.test(limpio)) return { valido: false };

  const r = await otp.verify({
    secret: descifrar(secretoCifrado),
    token: limpio,
    epochTolerance: TOLERANCIA_SEGUNDOS,
    ...(ultimoPaso ? { afterTimeStep: Number(ultimoPaso) } : {}),
  });
  return { valido: !!r.valid, paso: r.timeStep };
}

// --- Códigos de respaldo ----------------------------------------------
// Si el usuario pierde el teléfono, estos son su única forma de entrar
// sin molestar al administrador. Son de un solo uso y se guardan
// hasheados, igual que las contraseñas.
const CANTIDAD_RESPALDO = 10;

// Formato XXXX-XXXX sin caracteres ambiguos (0/O, 1/I) para que se
// puedan copiar a mano de un papel sin equivocarse.
const ALFABETO = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

function nuevoCodigoRespaldo() {
  const bytes = crypto.randomBytes(8);
  const letras = [...bytes].map((b) => ALFABETO[b % ALFABETO.length]).join('');
  return `${letras.slice(0, 4)}-${letras.slice(4)}`;
}

// Devuelve { codigos, hashes }: los primeros se le muestran al usuario
// UNA sola vez y los segundos son los que se guardan.
async function generarCodigosRespaldo() {
  const codigos = Array.from({ length: CANTIDAD_RESPALDO }, nuevoCodigoRespaldo);
  const hashes = await Promise.all(codigos.map((c) => bcrypt.hash(c, 10)));
  return { codigos, hashes };
}

function pareceCodigoRespaldo(valor) {
  return /^[A-Z0-9]{4}-?[A-Z0-9]{4}$/i.test(String(valor || '').trim());
}

function normalizarRespaldo(valor) {
  const limpio = String(valor || '').trim().toUpperCase().replace(/-/g, '');
  return `${limpio.slice(0, 4)}-${limpio.slice(4, 8)}`;
}

module.exports = {
  comprobarConfiguracion,
  cifrar,
  descifrar,
  nuevoSecreto,
  uriDeInscripcion,
  verificarCodigo,
  generarCodigosRespaldo,
  pareceCodigoRespaldo,
  normalizarRespaldo,
  CANTIDAD_RESPALDO,
};
