// server.js — API del sistema de confronta diaria
// Brigada de Comunicaciones N.º 33 "Rumiñahui"
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const ExcelJS = require('exceljs');
require('dotenv').config();

const pool = require('./db');
const { verificarToken, soloRol } = require('./auth');

const app = express();
app.use(helmet());              // cabeceras de seguridad HTTP
// CORS restringido: solo orígenes permitidos (configurable con CORS_ORIGINS).
// Las apps móviles nativas no envían origin, así que se permiten (origin null).
const origenesPermitidos = (process.env.CORS_ORIGINS || 'http://localhost:3000')
  .split(',').map((s) => s.trim());
app.use(cors({
  origin: (origin, cb) => {
    if (!origin || origenesPermitidos.includes(origin)) cb(null, true);
    else cb(new Error('Origen no permitido por CORS'));
  },
}));
app.use(express.json());

// Política de contraseñas: mín. 8, con minúscula, mayúscula, número y especial.
function validarPassword(pwd) {
  const p = String(pwd || '');
  if (p.length < 8) return 'La contraseña debe tener al menos 8 caracteres';
  if (!/[a-z]/.test(p)) return 'La contraseña debe incluir una letra minúscula';
  if (!/[A-Z]/.test(p)) return 'La contraseña debe incluir una letra mayúscula';
  if (!/[0-9]/.test(p)) return 'La contraseña debe incluir un número';
  if (!/[^A-Za-z0-9]/.test(p)) return 'La contraseña debe incluir un carácter especial';
  return null;
}

const INTENTOS_MAX = 3;
const BLOQUEO_MINUTOS = 10;

// ===============================================================
// Helpers
// ===============================================================

// Registrar en auditoría (trazabilidad — seguridad informática)
async function auditar(id_usuario, accion, detalle) {
  try {
    await pool.query(
      'INSERT INTO auditoria (id_usuario, accion, detalle) VALUES (?,?,?)',
      [id_usuario, accion, detalle]);
  } catch (e) { console.error('Error auditoría:', e.message); }
}

// Normaliza la respuesta de seguridad (sin espacios, minúsculas y sin tildes)
function normalizarRespuesta(txt) {
  return String(txt || '')
    .trim().toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, ''); // quita tildes
}

// Tarifa vigente a una fecha dada ('YYYY-MM-DD'). Si no hay una anterior,
// devuelve la más antigua registrada.
async function tarifaVigente(fecha) {
  let [rows] = await pool.query(
    `SELECT desayuno, almuerzo, merienda FROM tarifa
     WHERE vigente_desde <= ? ORDER BY vigente_desde DESC LIMIT 1`, [fecha]);
  if (rows.length === 0) {
    [rows] = await pool.query(
      `SELECT desayuno, almuerzo, merienda FROM tarifa
       ORDER BY vigente_desde ASC LIMIT 1`);
  }
  const t = rows[0] || { desayuno: 0, almuerzo: 0, merienda: 0 };
  return {
    desayuno: Number(t.desayuno),
    almuerzo: Number(t.almuerzo),
    merienda: Number(t.merienda),
  };
}

// Costo de un día según las raciones marcadas y una tarifa
function costoDia(fila, tarifa) {
  return (fila.desayuno ? tarifa.desayuno : 0)
       + (fila.almuerzo ? tarifa.almuerzo : 0)
       + (fila.merienda ? tarifa.merienda : 0);
}

const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio',
  'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];

const redondear = (n) => Math.round(n * 100) / 100;

// Precio de una comida ('DESAYUNO'|'ALMUERZO'|'MERIENDA') según la tarifa
function precioComida(tarifa, comida) {
  if (comida === 'DESAYUNO') return tarifa.desayuno;
  if (comida === 'ALMUERZO') return tarifa.almuerzo;
  if (comida === 'MERIENDA') return tarifa.merienda;
  return 0;
}

// Columna de confronta correspondiente a una comida
function columnaComida(comida) {
  return { DESAYUNO: 'desayuno', ALMUERZO: 'almuerzo', MERIENDA: 'merienda' }[comida];
}

// ¿Se puede reservar para 'fechaStr' (YYYY-MM-DD)? El cupo cierra a las 17:00
// del día anterior. Devuelve {ok, limite}.
function reservaAbierta(fechaStr) {
  const [y, m, d] = fechaStr.split('-').map(Number);
  const limite = new Date(y, m - 1, d);      // fecha de la comida (00:00 local)
  limite.setDate(limite.getDate() - 1);      // día anterior
  limite.setHours(17, 0, 0, 0);              // 17:00 del día anterior
  return { ok: new Date() <= limite, limite };
}

// Resuelve el id_personal objetivo de una operación de consumo.
// - Autoservicio: cualquier usuario registra el suyo (usa el del token).
// - Operador/Admin: pueden registrar/consultar el de cualquiera (por id_personal).
function idPersonalObjetivo(req, idPersonalPedido) {
  const esGestor = req.usuario.rol === 'ADMIN' || req.usuario.rol === 'OPERADOR';
  if (idPersonalPedido && esGestor) return Number(idPersonalPedido);
  if (idPersonalPedido && Number(idPersonalPedido) === req.usuario.id_personal)
    return req.usuario.id_personal;
  return req.usuario.id_personal || null;
}

// ===============================================================
// AUTENTICACIÓN Y CUENTAS
// ===============================================================

// --- Login (con bloqueo por intentos y saludo con grado/nombre) ---
app.post('/api/login', async (req, res) => {
  const { username, password } = req.body;
  try {
    const [rows] = await pool.query(`
      SELECT u.*, p.nombres, p.apellidos, g.abreviatura AS grado
      FROM usuario u
      LEFT JOIN personal p ON u.id_personal = p.id_personal
      LEFT JOIN grado g    ON p.id_grado    = g.id_grado
      WHERE u.username = ? AND u.activo = 1`, [username]);
    if (rows.length === 0)
      return res.status(401).json({ error: 'Credenciales inválidas' });

    const usuario = rows[0];
    const ahora = new Date();

    // ¿Cuenta bloqueada temporalmente?
    if (usuario.bloqueado_hasta && new Date(usuario.bloqueado_hasta) > ahora) {
      const min = Math.ceil((new Date(usuario.bloqueado_hasta) - ahora) / 60000);
      return res.status(423).json({
        error: `Cuenta bloqueada por intentos fallidos. Intenta de nuevo en ${min} min.`,
      });
    }

    const ok = await bcrypt.compare(password, usuario.password_hash);
    if (!ok) {
      const intentos = (usuario.intentos_fallidos || 0) + 1;
      if (intentos >= INTENTOS_MAX) {
        // Bloquea 10 min y reinicia el contador (al desbloquear tendrá 3 intentos)
        const hasta = new Date(ahora.getTime() + BLOQUEO_MINUTOS * 60000);
        await pool.query(
          'UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = ? WHERE id_usuario = ?',
          [hasta, usuario.id_usuario]);
        await auditar(usuario.id_usuario, 'BLOQUEO', `Cuenta ${username} bloqueada ${BLOQUEO_MINUTOS} min`);
        return res.status(423).json({
          error: `Demasiados intentos. Cuenta bloqueada por ${BLOQUEO_MINUTOS} minutos.`,
        });
      }
      await pool.query('UPDATE usuario SET intentos_fallidos = ? WHERE id_usuario = ?',
        [intentos, usuario.id_usuario]);
      return res.status(401).json({
        error: `Credenciales inválidas. Te quedan ${INTENTOS_MAX - intentos} intento(s).`,
      });
    }

    // Login correcto: limpia intentos/bloqueo
    await pool.query(
      'UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id_usuario = ?',
      [usuario.id_usuario]);

    const token = jwt.sign(
      {
        id_usuario: usuario.id_usuario,
        username: usuario.username,
        rol: usuario.rol,
        id_personal: usuario.id_personal || null,
      },
      process.env.JWT_SECRET,
      { expiresIn: process.env.JWT_EXPIRES });

    await auditar(usuario.id_usuario, 'LOGIN', `Ingreso de ${username}`);
    res.json({
      token,
      rol: usuario.rol,
      username: usuario.username,
      id_usuario: usuario.id_usuario,
      id_personal: usuario.id_personal || null,
      grado: usuario.grado || null,
      nombres: usuario.nombres || null,
      apellidos: usuario.apellidos || null,
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

// --- Registro de un nuevo usuario (crea ficha de personal + cuenta) ---
app.post('/api/registro', async (req, res) => {
  const {
    password, pregunta_seguridad, respuesta_seguridad,
    cedula, nombres, apellidos, id_grado, id_unidad,
  } = req.body;

  // El nombre de usuario ES la cédula (todos entran con su cédula).
  const cedulaLimpia = (cedula || '').trim();
  if (!cedulaLimpia || !password || !nombres || !apellidos || !id_grado || !id_unidad)
    return res.status(400).json({ error: 'Faltan datos obligatorios (incluida la cédula)' });
  if (!pregunta_seguridad || !respuesta_seguridad)
    return res.status(400).json({ error: 'Debe definir la pregunta y respuesta de seguridad' });
  const errPwd = validarPassword(password);
  if (errPwd) return res.status(400).json({ error: errPwd });

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    // ¿ya existe una cuenta con esa cédula?
    const [u] = await conn.query('SELECT id_usuario FROM usuario WHERE username = ?', [cedulaLimpia]);
    if (u.length > 0) {
      await conn.rollback();
      return res.status(409).json({ error: 'Ya existe una cuenta con esa cédula' });
    }

    // Ficha de personal: reutiliza si la cédula ya existe, si no la crea
    let idPersonal = null;
    const [p] = await conn.query('SELECT id_personal FROM personal WHERE cedula = ?', [cedulaLimpia]);
    if (p.length > 0) idPersonal = p[0].id_personal;
    if (!idPersonal) {
      const [ins] = await conn.query(
        `INSERT INTO personal (cedula, nombres, apellidos, id_grado, id_unidad)
         VALUES (?,?,?,?,?)`,
        [cedulaLimpia, nombres.trim(), apellidos.trim(), id_grado, id_unidad]);
      idPersonal = ins.insertId;
    }

    const passHash = await bcrypt.hash(password, 10);
    const respHash = await bcrypt.hash(normalizarRespuesta(respuesta_seguridad), 10);

    const [insU] = await conn.query(
      `INSERT INTO usuario
         (username, password_hash, rol, id_personal, pregunta_seguridad, respuesta_hash)
       VALUES (?,?,?,?,?,?)`,
      [cedulaLimpia, passHash, 'OPERADOR', idPersonal, pregunta_seguridad, respHash]);

    await conn.commit();
    await auditar(insU.insertId, 'REGISTRO', `Nuevo usuario cédula ${cedulaLimpia}`);
    res.status(201).json({ ok: true, id_usuario: insU.insertId, id_personal: idPersonal });
  } catch (e) {
    await conn.rollback();
    console.error(e);
    if (e.code === 'ER_DUP_ENTRY')
      return res.status(409).json({ error: 'Usuario o cédula ya registrados' });
    res.status(500).json({ error: 'Error al registrar' });
  } finally {
    conn.release();
  }
});

// --- Olvidé mi contraseña: paso 1, obtener la pregunta de seguridad ---
app.get('/api/olvido/pregunta', async (req, res) => {
  const { username } = req.query;
  try {
    const [rows] = await pool.query(
      'SELECT pregunta_seguridad FROM usuario WHERE username = ? AND activo = 1', [username]);
    if (rows.length === 0 || !rows[0].pregunta_seguridad)
      return res.status(404).json({ error: 'No hay pregunta de seguridad para ese usuario' });
    res.json({ pregunta_seguridad: rows[0].pregunta_seguridad });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

// --- Olvidé mi contraseña: paso 2, verificar respuesta y cambiar clave ---
app.post('/api/olvido/reset', async (req, res) => {
  const { username, respuesta_seguridad, nueva_password } = req.body;
  if (!username || !respuesta_seguridad || !nueva_password)
    return res.status(400).json({ error: 'Faltan datos' });
  const errReset = validarPassword(nueva_password);
  if (errReset) return res.status(400).json({ error: errReset });
  try {
    const [rows] = await pool.query(
      'SELECT id_usuario, respuesta_hash FROM usuario WHERE username = ? AND activo = 1', [username]);
    if (rows.length === 0 || !rows[0].respuesta_hash)
      return res.status(404).json({ error: 'Usuario no encontrado' });

    const ok = await bcrypt.compare(normalizarRespuesta(respuesta_seguridad), rows[0].respuesta_hash);
    if (!ok) return res.status(401).json({ error: 'Respuesta de seguridad incorrecta' });

    const passHash = await bcrypt.hash(nueva_password, 10);
    await pool.query('UPDATE usuario SET password_hash = ? WHERE id_usuario = ?',
      [passHash, rows[0].id_usuario]);
    await auditar(rows[0].id_usuario, 'RESET_PASSWORD', `Cambio de clave de ${username}`);
    res.json({ ok: true });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

// ===============================================================
// CATÁLOGOS (para poblar los combos del registro)
// ===============================================================
app.get('/api/grados', async (_req, res) => {
  const [rows] = await pool.query(
    'SELECT id_grado, nombre, abreviatura FROM grado ORDER BY id_grado');
  res.json(rows);
});

app.get('/api/unidades', async (_req, res) => {
  const [rows] = await pool.query(
    'SELECT id_unidad, nombre, siglas, descripcion FROM unidad ORDER BY nombre');
  res.json(rows);
});

// ===============================================================
// TARIFAS (precios de las raciones)
// ===============================================================
app.get('/api/tarifa', verificarToken, async (_req, res) => {
  const hoy = new Date().toISOString().slice(0, 10);
  const t = await tarifaVigente(hoy);
  res.json(t);
});

// Solo ADMIN puede cambiar los precios (crea una nueva tarifa vigente hoy)
app.put('/api/tarifa', verificarToken, soloRol('ADMIN'), async (req, res) => {
  const { desayuno, almuerzo, merienda } = req.body;
  if ([desayuno, almuerzo, merienda].some((v) => v == null || isNaN(v)))
    return res.status(400).json({ error: 'Precios inválidos' });
  try {
    const hoy = new Date().toISOString().slice(0, 10);
    await pool.query(
      `INSERT INTO tarifa (desayuno, almuerzo, merienda, vigente_desde)
       VALUES (?,?,?,?)`, [desayuno, almuerzo, merienda, hoy]);
    await auditar(req.usuario.id_usuario, 'TARIFA',
      `Nueva tarifa D:${desayuno} A:${almuerzo} M:${merienda}`);
    res.json({ ok: true, desayuno: Number(desayuno), almuerzo: Number(almuerzo), merienda: Number(merienda) });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error al actualizar la tarifa' });
  }
});

// ===============================================================
// PERSONAL
// ===============================================================
app.get('/api/personal', verificarToken, async (_req, res) => {
  const [rows] = await pool.query(`
    SELECT p.id_personal, p.cedula, p.nombres, p.apellidos,
           g.abreviatura AS grado, u.nombre AS unidad
    FROM personal p
    JOIN grado g  ON p.id_grado  = g.id_grado
    JOIN unidad u ON p.id_unidad = u.id_unidad
    WHERE p.activo = 1
    ORDER BY p.apellidos`);
  res.json(rows);
});

// Mis datos (persona vinculada a la cuenta)
app.get('/api/mi/perfil', verificarToken, async (req, res) => {
  if (!req.usuario.id_personal)
    return res.json({ id_personal: null, username: req.usuario.username, rol: req.usuario.rol });
  const [rows] = await pool.query(`
    SELECT p.id_personal, p.cedula, p.nombres, p.apellidos,
           g.abreviatura AS grado, g.nombre AS grado_nombre, u.nombre AS unidad
    FROM personal p
    JOIN grado g  ON p.id_grado  = g.id_grado
    JOIN unidad u ON p.id_unidad = u.id_unidad
    WHERE p.id_personal = ?`, [req.usuario.id_personal]);
  const perfil = rows[0] || {};
  res.json({ ...perfil, username: req.usuario.username, rol: req.usuario.rol });
});

// ===============================================================
// CONFRONTA / CONSUMO
// ===============================================================

// Registrar / actualizar la confronta de una persona en una fecha.
// Autoservicio (propio) o gestor (por cualquiera con id_personal).
app.post('/api/confronta', verificarToken, async (req, res) => {
  const { id_personal, fecha, estado, desayuno, almuerzo, merienda, novedad } = req.body;
  const objetivo = idPersonalObjetivo(req, id_personal);

  if (!objetivo)
    return res.status(400).json({ error: 'Tu cuenta no está vinculada a una ficha de personal' });
  if (id_personal && Number(id_personal) !== objetivo &&
      !(req.usuario.rol === 'ADMIN' || req.usuario.rol === 'OPERADOR'))
    return res.status(403).json({ error: 'Solo puedes registrar tu propio consumo' });
  if (!fecha) return res.status(400).json({ error: 'Falta la fecha' });

  try {
    await pool.query(`
      INSERT INTO confronta
        (id_personal, fecha, estado, desayuno, almuerzo, merienda, novedad, id_usuario)
      VALUES (?,?,?,?,?,?,?,?)
      ON DUPLICATE KEY UPDATE
        estado=VALUES(estado), desayuno=VALUES(desayuno),
        almuerzo=VALUES(almuerzo), merienda=VALUES(merienda),
        novedad=VALUES(novedad), id_usuario=VALUES(id_usuario)`,
      [objetivo, fecha, estado || 'PRESENTE',
       desayuno ? 1 : 0, almuerzo ? 1 : 0, merienda ? 1 : 0,
       novedad || null, req.usuario.id_usuario]);

    await auditar(req.usuario.id_usuario, 'CONFRONTA',
      `Confronta personal ${objetivo} fecha ${fecha}`);

    const tarifa = await tarifaVigente(fecha);
    const costo = costoDia(
      { desayuno: desayuno ? 1 : 0, almuerzo: almuerzo ? 1 : 0, merienda: merienda ? 1 : 0 },
      tarifa);
    res.json({ ok: true, id_personal: objetivo, fecha, costo: redondear(costo) });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error al registrar la confronta' });
  }
});

// Consultar confrontas de una fecha (gestores)
app.get('/api/confronta/:fecha', verificarToken, soloRol('ADMIN', 'OPERADOR'), async (req, res) => {
  const tarifa = await tarifaVigente(req.params.fecha);
  const [rows] = await pool.query(`
    SELECT c.*, p.nombres, p.apellidos, g.abreviatura AS grado
    FROM confronta c
    JOIN personal p ON c.id_personal = p.id_personal
    JOIN grado g    ON p.id_grado    = g.id_grado
    WHERE c.fecha = ?
    ORDER BY p.apellidos`, [req.params.fecha]);
  res.json(rows.map((r) => ({ ...r, costo: redondear(costoDia(r, tarifa)) })));
});

// ---- Consumo mensual (para el calendario) --------------------
// Devuelve, para el mes pedido, cada día registrado con su costo y el total.
async function consumoMes(idPersonal, anio, mes) {
  const [rows] = await pool.query(`
    SELECT fecha, estado, desayuno, almuerzo, merienda, novedad
    FROM confronta
    WHERE id_personal = ? AND YEAR(fecha) = ? AND MONTH(fecha) = ?
    ORDER BY fecha`, [idPersonal, anio, mes]);

  // Tarifa vigente al final del mes (para ese periodo)
  const ref = `${anio}-${String(mes).padStart(2, '0')}-28`;
  const tarifa = await tarifaVigente(ref);

  let totD = 0, totA = 0, totM = 0, total = 0;
  const dias = rows.map((r) => {
    totD += r.desayuno; totA += r.almuerzo; totM += r.merienda;
    const costo = costoDia(r, tarifa);
    total += costo;
    // fecha como 'YYYY-MM-DD' local (evita corrimiento por zona horaria)
    const f = new Date(r.fecha);
    const fechaStr = `${f.getFullYear()}-${String(f.getMonth() + 1).padStart(2, '0')}-${String(f.getDate()).padStart(2, '0')}`;
    return {
      fecha: fechaStr, estado: r.estado,
      desayuno: !!r.desayuno, almuerzo: !!r.almuerzo, merienda: !!r.merienda,
      novedad: r.novedad, costo: redondear(costo),
    };
  });

  return {
    anio: Number(anio), mes: Number(mes), mes_nombre: MESES[mes - 1],
    tarifa,
    dias,
    totales: {
      desayunos: totD, almuerzos: totA, meriendas: totM,
      dias_con_consumo: dias.filter((d) => d.desayuno || d.almuerzo || d.merienda).length,
      total: redondear(total),
    },
  };
}

// Mi consumo del mes (autoservicio)
app.get('/api/mi/consumo/:anio/:mes', verificarToken, async (req, res) => {
  if (!req.usuario.id_personal)
    return res.status(400).json({ error: 'Tu cuenta no está vinculada a una ficha de personal' });
  res.json(await consumoMes(req.usuario.id_personal, req.params.anio, req.params.mes));
});

// Consumo del mes de cualquier persona (gestores)
app.get('/api/consumo/:idPersonal/:anio/:mes', verificarToken, soloRol('ADMIN', 'OPERADOR'), async (req, res) => {
  res.json(await consumoMes(req.params.idPersonal, req.params.anio, req.params.mes));
});

// ---- Historial por meses (todo el año) -----------------------
async function historialAnio(idPersonal, anio) {
  const [rows] = await pool.query(`
    SELECT MONTH(fecha) AS mes,
           SUM(desayuno) AS desayunos,
           SUM(almuerzo) AS almuerzos,
           SUM(merienda) AS meriendas
    FROM confronta
    WHERE id_personal = ? AND YEAR(fecha) = ?
    GROUP BY MONTH(fecha)`, [idPersonal, anio]);

  const porMes = {};
  rows.forEach((r) => { porMes[r.mes] = r; });

  let totalAnio = 0;
  const meses = [];
  for (let m = 1; m <= 12; m++) {
    const ref = `${anio}-${String(m).padStart(2, '0')}-28`;
    const tarifa = await tarifaVigente(ref);
    const r = porMes[m] || { desayunos: 0, almuerzos: 0, meriendas: 0 };
    const d = Number(r.desayunos), a = Number(r.almuerzos), me = Number(r.meriendas);
    const total = redondear(d * tarifa.desayuno + a * tarifa.almuerzo + me * tarifa.merienda);
    totalAnio += total;
    meses.push({
      mes: m, mes_nombre: MESES[m - 1],
      desayunos: d, almuerzos: a, meriendas: me, total,
    });
  }
  return { anio: Number(anio), meses, total_anio: redondear(totalAnio) };
}

// Mi historial por meses (autoservicio)
app.get('/api/mi/historial/:anio', verificarToken, async (req, res) => {
  if (!req.usuario.id_personal)
    return res.status(400).json({ error: 'Tu cuenta no está vinculada a una ficha de personal' });
  res.json(await historialAnio(req.usuario.id_personal, req.params.anio));
});

// Historial por meses de cualquier persona (gestores)
app.get('/api/historial/:idPersonal/:anio', verificarToken, soloRol('ADMIN', 'OPERADOR'), async (req, res) => {
  res.json(await historialAnio(req.params.idPersonal, req.params.anio));
});

// ---- Liquidación individual del mes --------------------------
async function liquidacion(idPersonal, anio, mes) {
  const [pRows] = await pool.query(`
    SELECT p.id_personal, p.cedula, p.nombres, p.apellidos,
           g.abreviatura AS grado, u.nombre AS unidad
    FROM personal p
    JOIN grado g  ON p.id_grado  = g.id_grado
    JOIN unidad u ON p.id_unidad = u.id_unidad
    WHERE p.id_personal = ?`, [idPersonal]);

  const [cRows] = await pool.query(`
    SELECT SUM(desayuno) AS desayunos, SUM(almuerzo) AS almuerzos, SUM(merienda) AS meriendas
    FROM confronta
    WHERE id_personal = ? AND YEAR(fecha) = ? AND MONTH(fecha) = ?`, [idPersonal, anio, mes]);

  const ref = `${anio}-${String(mes).padStart(2, '0')}-28`;
  const tarifa = await tarifaVigente(ref);
  const d = Number(cRows[0].desayunos || 0);
  const a = Number(cRows[0].almuerzos || 0);
  const me = Number(cRows[0].meriendas || 0);
  const subD = redondear(d * tarifa.desayuno);
  const subA = redondear(a * tarifa.almuerzo);
  const subM = redondear(me * tarifa.merienda);

  return {
    persona: pRows[0] || null,
    anio: Number(anio), mes: Number(mes), mes_nombre: MESES[mes - 1],
    tarifa,
    desayunos: d, almuerzos: a, meriendas: me,
    subtotal_desayuno: subD, subtotal_almuerzo: subA, subtotal_merienda: subM,
    total: redondear(subD + subA + subM),
  };
}

// Mi liquidación del mes (autoservicio)
app.get('/api/mi/liquidacion/:anio/:mes', verificarToken, async (req, res) => {
  if (!req.usuario.id_personal)
    return res.status(400).json({ error: 'Tu cuenta no está vinculada a una ficha de personal' });
  res.json(await liquidacion(req.usuario.id_personal, req.params.anio, req.params.mes));
});

// Liquidación del mes de cualquier persona (gestores)
app.get('/api/liquidacion/:idPersonal/:anio/:mes', verificarToken, soloRol('ADMIN', 'OPERADOR'), async (req, res) => {
  res.json(await liquidacion(req.params.idPersonal, req.params.anio, req.params.mes));
});

// ===============================================================
// SALDO Y MOVIMIENTOS (todos)
// ===============================================================
app.get('/api/mi/estado', verificarToken, async (req, res) => {
  const [u] = await pool.query('SELECT saldo FROM usuario WHERE id_usuario = ?', [req.usuario.id_usuario]);
  const [mov] = await pool.query(`
    SELECT tipo, monto, fecha_hora, comida FROM (
      SELECT 'RECARGA' AS tipo, monto AS monto, fecha_hora AS fecha_hora, NULL AS comida
        FROM recarga WHERE id_usuario = ?
      UNION ALL
      SELECT 'CONSUMO' AS tipo, (-precio) AS monto, creado_en AS fecha_hora, comida AS comida
        FROM ticket WHERE id_usuario = ? AND estado IN ('ACTIVO','CANJEADO')
    ) t ORDER BY fecha_hora DESC LIMIT 100`,
    [req.usuario.id_usuario, req.usuario.id_usuario]);
  res.json({
    saldo: Number(u[0]?.saldo || 0),
    movimientos: mov.map((m) => ({
      tipo: m.tipo, monto: Number(m.monto), fecha_hora: m.fecha_hora, comida: m.comida,
    })),
  });
});

// ===============================================================
// TESORERÍA (TESORERO / ADMIN): recargar saldo buscando por cédula
// ===============================================================
app.get('/api/tesoreria/buscar', verificarToken, soloRol('TESORERO', 'ADMIN'), async (req, res) => {
  const cedula = (req.query.cedula || '').trim();
  if (!cedula) return res.status(400).json({ error: 'Indica la cédula' });
  const [rows] = await pool.query(`
    SELECT us.id_usuario, us.saldo, p.cedula, p.nombres, p.apellidos,
           g.abreviatura AS grado, u.siglas AS unidad
    FROM usuario us
    JOIN personal p ON us.id_personal = p.id_personal
    JOIN grado g    ON p.id_grado     = g.id_grado
    JOIN unidad u   ON p.id_unidad    = u.id_unidad
    WHERE p.cedula = ?`, [cedula]);
  if (rows.length === 0)
    return res.status(404).json({ error: 'No hay personal registrado con esa cédula' });
  res.json({ ...rows[0], saldo: Number(rows[0].saldo) });
});

app.post('/api/recargas', verificarToken, soloRol('TESORERO', 'ADMIN'), async (req, res) => {
  const cedula = (req.body.cedula || '').trim();
  const monto = Number(req.body.monto);
  if (!cedula || isNaN(monto) || monto <= 0)
    return res.status(400).json({ error: 'Cédula y monto válido son obligatorios' });

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    const [rows] = await conn.query(`
      SELECT us.id_usuario FROM usuario us
      JOIN personal p ON us.id_personal = p.id_personal
      WHERE p.cedula = ?`, [cedula]);
    if (rows.length === 0) {
      await conn.rollback();
      return res.status(404).json({ error: 'No hay personal registrado con esa cédula' });
    }
    const idUsuario = rows[0].id_usuario;
    await conn.query('UPDATE usuario SET saldo = saldo + ? WHERE id_usuario = ?', [monto, idUsuario]);
    await conn.query('INSERT INTO recarga (id_usuario, monto, id_tesorero) VALUES (?,?,?)',
      [idUsuario, monto, req.usuario.id_usuario]);
    const [u] = await conn.query('SELECT saldo FROM usuario WHERE id_usuario = ?', [idUsuario]);
    await conn.commit();
    await auditar(req.usuario.id_usuario, 'RECARGA', `Recarga $${monto} a cédula ${cedula}`);
    res.json({ ok: true, saldo: Number(u[0].saldo) });
  } catch (e) {
    await conn.rollback();
    console.error(e);
    res.status(500).json({ error: 'Error al recargar' });
  } finally {
    conn.release();
  }
});

// ===============================================================
// RESERVA = COMPRA (se cobra el saldo al reservar; cupo hasta las 17:00
// del día anterior). Cada comida reservada genera su ticket (QR de entrada).
// Editar la reserva antes del cierre ajusta: cobra lo que agregas, devuelve
// lo que quitas. Si no vas a comer, la comida se pierde pero YA está pagada.
// ===============================================================
const COMIDAS = ['DESAYUNO', 'ALMUERZO', 'MERIENDA'];

app.post('/api/reserva', verificarToken, async (req, res) => {
  const { fecha, estado, desayuno, almuerzo, merienda, novedad } = req.body;
  const idUsuario = req.usuario.id_usuario;
  const idPersonal = req.usuario.id_personal;
  if (!idPersonal)
    return res.status(400).json({ error: 'Tu cuenta no está vinculada a una ficha de personal' });
  if (!fecha) return res.status(400).json({ error: 'Falta la fecha' });

  const { ok, limite } = reservaAbierta(fecha);
  if (!ok) {
    const lim = `${String(limite.getDate()).padStart(2, '0')}/${String(limite.getMonth() + 1).padStart(2, '0')} 17:00`;
    return res.status(400).json({
      error: `La reserva para esa fecha ya cerró (era hasta el ${lim}, día anterior).`,
    });
  }

  const tarifa = await tarifaVigente(fecha);
  const precio = { DESAYUNO: tarifa.desayuno, ALMUERZO: tarifa.almuerzo, MERIENDA: tarifa.merienda };
  const deseado = { DESAYUNO: !!desayuno, ALMUERZO: !!almuerzo, MERIENDA: !!merienda };

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    const [uRows] = await conn.query('SELECT saldo FROM usuario WHERE id_usuario = ? FOR UPDATE', [idUsuario]);
    const saldo = Number(uRows[0].saldo);

    // Tickets vigentes (no anulados) de esa fecha
    const [tk] = await conn.query(
      `SELECT id_ticket, comida, precio, estado FROM ticket
       WHERE id_usuario = ? AND fecha = ? AND estado <> 'ANULADO'`, [idUsuario, fecha]);
    const existente = {};
    tk.forEach((t) => { existente[t.comida] = t; });

    // Calcular cobro (comidas nuevas) y reembolso (comidas quitadas, aún no consumidas)
    let cobro = 0, reembolso = 0;
    for (const c of COMIDAS) {
      const ex = existente[c];
      if (deseado[c] && !ex) cobro += Number(precio[c]);
      else if (!deseado[c] && ex && ex.estado === 'ACTIVO') reembolso += Number(ex.precio);
    }

    if (saldo - cobro + reembolso < 0) {
      await conn.rollback();
      return res.status(400).json({
        error: `Saldo insuficiente. La reserva cuesta ${redondear(cobro)} y tu saldo es ${redondear(saldo)}. Pide una recarga al tesorero.`,
      });
    }

    // Aplicar cambios en los tickets
    for (const c of COMIDAS) {
      const ex = existente[c];
      if (deseado[c] && !ex) {
        const token = crypto.randomBytes(16).toString('hex');
        await conn.query(
          'INSERT INTO ticket (token, id_usuario, fecha, comida, precio) VALUES (?,?,?,?,?)',
          [token, idUsuario, fecha, c, precio[c]]);
      } else if (!deseado[c] && ex && ex.estado === 'ACTIVO') {
        await conn.query("UPDATE ticket SET estado='ANULADO' WHERE id_ticket = ?", [ex.id_ticket]);
      }
    }

    const nuevoSaldo = redondear(saldo - cobro + reembolso);
    await conn.query('UPDATE usuario SET saldo = ? WHERE id_usuario = ?', [nuevoSaldo, idUsuario]);

    // Reflejar en confronta (para el forecast del ranchero). Una comida ya
    // consumida (CANJEADO) queda como reservada aunque se intente quitar.
    const flag = {};
    for (const c of COMIDAS) {
      const ex = existente[c];
      flag[c] = (deseado[c] || (ex && ex.estado === 'CANJEADO')) ? 1 : 0;
    }
    await conn.query(`
      INSERT INTO confronta
        (id_personal, fecha, estado, desayuno, almuerzo, merienda, novedad, id_usuario)
      VALUES (?,?,?,?,?,?,?,?)
      ON DUPLICATE KEY UPDATE
        estado=VALUES(estado), desayuno=VALUES(desayuno),
        almuerzo=VALUES(almuerzo), merienda=VALUES(merienda),
        novedad=VALUES(novedad), id_usuario=VALUES(id_usuario)`,
      [idPersonal, fecha, estado || 'PRESENTE',
       flag.DESAYUNO, flag.ALMUERZO, flag.MERIENDA, novedad || null, idUsuario]);

    await conn.commit();
    await auditar(idUsuario, 'RESERVA',
      `Reserva ${fecha} cobro $${redondear(cobro)} reembolso $${redondear(reembolso)}`);
    res.json({
      ok: true, fecha,
      cobrado: redondear(cobro),
      reembolsado: redondear(reembolso),
      saldo: nuevoSaldo,
    });
  } catch (e) {
    await conn.rollback();
    console.error(e);
    res.status(500).json({ error: 'Error al reservar' });
  } finally {
    conn.release();
  }
});

// ===============================================================
// QR de un solo uso: generar (usuario) y canjear (ranchero/admin)
// ===============================================================
// El QR es el TICKET de una comida YA reservada y pagada. No cobra nada:
// solo entrega el código para pasar al rancho.
app.post('/api/qr', verificarToken, async (req, res) => {
  const { fecha, comida } = req.body;
  if (!req.usuario.id_personal)
    return res.status(400).json({ error: 'Tu cuenta no está vinculada a una ficha de personal' });
  if (!fecha || !COMIDAS.includes(comida))
    return res.status(400).json({ error: 'Fecha o comida inválida' });

  try {
    const [tk] = await pool.query(
      `SELECT token, precio, estado FROM ticket
       WHERE id_usuario = ? AND fecha = ? AND comida = ? AND estado <> 'ANULADO'`,
      [req.usuario.id_usuario, fecha, comida]);
    if (tk.length === 0)
      return res.status(400).json({ error: `No reservaste ${comida.toLowerCase()} para esa fecha (recuerda que la reserva se paga al hacerla)` });
    if (tk[0].estado === 'CANJEADO')
      return res.status(409).json({ error: 'Ya usaste esta comida (QR canjeado)' });
    res.json({ token: tk[0].token, comida, fecha, precio: Number(tk[0].precio) });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error al generar el QR' });
  }
});

app.post('/api/canjear', verificarToken, soloRol('RANCHERO', 'ADMIN'), async (req, res) => {
  const token = (req.body.token || '').trim();
  if (!token) return res.status(400).json({ error: 'Falta el código del QR' });

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    const [tk] = await conn.query(
      `SELECT t.*, p.nombres, p.apellidos, g.abreviatura AS grado, un.siglas AS unidad
       FROM ticket t
       JOIN usuario us ON t.id_usuario = us.id_usuario
       LEFT JOIN personal p ON us.id_personal = p.id_personal
       LEFT JOIN grado g    ON p.id_grado     = g.id_grado
       LEFT JOIN unidad un  ON p.id_unidad    = un.id_unidad
       WHERE t.token = ? FOR UPDATE`, [token]);
    if (tk.length === 0) {
      await conn.rollback();
      return res.status(404).json({ error: 'QR no válido' });
    }
    const t = tk[0];
    if (t.estado === 'CANJEADO') {
      await conn.rollback();
      return res.status(409).json({ error: 'Ese QR ya fue usado' });
    }
    if (t.estado !== 'ACTIVO') {
      await conn.rollback();
      return res.status(400).json({ error: 'QR no disponible' });
    }
    // La comida ya se pagó al reservar: el canje solo marca la entrada.
    await conn.query(
      `UPDATE ticket SET estado='CANJEADO', canjeado_en=NOW(), id_canjeador=? WHERE id_ticket=?`,
      [req.usuario.id_usuario, t.id_ticket]);
    await conn.commit();
    await auditar(req.usuario.id_usuario, 'CANJE', `Canjeó ${t.comida} de ${t.nombres} ${t.apellidos}`);
    res.json({
      ok: true,
      persona: `${t.grado || ''} ${t.apellidos || ''} ${t.nombres || ''}`.trim(),
      unidad: t.unidad,
      comida: t.comida,
      fecha: t.fecha,
      monto: Number(t.precio),
    });
  } catch (e) {
    await conn.rollback();
    console.error(e);
    res.status(500).json({ error: 'Error al canjear el QR' });
  } finally {
    conn.release();
  }
});

// ===============================================================
// ADMINISTRACIÓN (solo ADMIN / root)
// ===============================================================
const ROLES_VALIDOS = ['ADMIN', 'OPERADOR', 'CONSULTA', 'RANCHERO', 'TESORERO'];

// Listar todos los usuarios con su ficha
app.get('/api/usuarios', verificarToken, soloRol('ADMIN'), async (_req, res) => {
  const [rows] = await pool.query(`
    SELECT us.id_usuario, us.username, us.rol, us.activo,
           p.nombres, p.apellidos, g.abreviatura AS grado, u.siglas AS unidad
    FROM usuario us
    LEFT JOIN personal p ON us.id_personal = p.id_personal
    LEFT JOIN grado g    ON p.id_grado     = g.id_grado
    LEFT JOIN unidad u   ON p.id_unidad    = u.id_unidad
    ORDER BY us.username`);
  res.json(rows.map((r) => ({ ...r, activo: !!r.activo })));
});

// Cambiar rol y/o estado (activo) de un usuario
app.put('/api/usuarios/:id', verificarToken, soloRol('ADMIN'), async (req, res) => {
  const id = Number(req.params.id);
  const { rol, activo } = req.body;

  if (id === req.usuario.id_usuario)
    return res.status(400).json({ error: 'No puedes cambiar tu propia cuenta desde aquí' });
  if (rol != null && !ROLES_VALIDOS.includes(rol))
    return res.status(400).json({ error: 'Rol inválido' });

  const campos = [];
  const vals = [];
  if (rol != null) { campos.push('rol = ?'); vals.push(rol); }
  if (activo != null) { campos.push('activo = ?'); vals.push(activo ? 1 : 0); }
  if (campos.length === 0)
    return res.status(400).json({ error: 'Nada que actualizar' });

  vals.push(id);
  try {
    const [r] = await pool.query(
      `UPDATE usuario SET ${campos.join(', ')} WHERE id_usuario = ?`, vals);
    if (r.affectedRows === 0)
      return res.status(404).json({ error: 'Usuario no encontrado' });
    await auditar(req.usuario.id_usuario, 'ADMIN_USUARIO',
      `Actualizó usuario ${id}: ${JSON.stringify({ rol, activo })}`);
    res.json({ ok: true });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error al actualizar el usuario' });
  }
});

// Restablecer la contraseña de un usuario (el admin define una nueva)
app.post('/api/usuarios/:id/password', verificarToken, soloRol('ADMIN'), async (req, res) => {
  const id = Number(req.params.id);
  const { nueva_password } = req.body;
  const errAdminPwd = validarPassword(nueva_password);
  if (errAdminPwd) return res.status(400).json({ error: errAdminPwd });
  try {
    const hash = await bcrypt.hash(nueva_password, 10);
    const [r] = await pool.query(
      'UPDATE usuario SET password_hash = ? WHERE id_usuario = ?', [hash, id]);
    if (r.affectedRows === 0)
      return res.status(404).json({ error: 'Usuario no encontrado' });
    await auditar(req.usuario.id_usuario, 'ADMIN_PASSWORD', `Restableció clave del usuario ${id}`);
    res.json({ ok: true });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error al restablecer la contraseña' });
  }
});

// Registros / bitácora de auditoría (últimos 100)
app.get('/api/auditoria', verificarToken, soloRol('ADMIN'), async (_req, res) => {
  const [rows] = await pool.query(`
    SELECT a.id_auditoria, a.accion, a.detalle, a.fecha_hora, us.username
    FROM auditoria a
    LEFT JOIN usuario us ON a.id_usuario = us.id_usuario
    ORDER BY a.fecha_hora DESC
    LIMIT 100`);
  res.json(rows);
});

// ===============================================================
// PRODUCCIÓN (ranchero): cuántos platos cocinar en una fecha
// ===============================================================
app.get('/api/produccion/:fecha', verificarToken, soloRol('ADMIN', 'RANCHERO'), async (req, res) => {
  const fecha = req.params.fecha;
  const [tot] = await pool.query(`
    SELECT SUM(desayuno) AS desayunos, SUM(almuerzo) AS almuerzos, SUM(merienda) AS meriendas,
           COUNT(*) AS personas
    FROM confronta WHERE fecha = ?`, [fecha]);

  const [porUnidad] = await pool.query(`
    SELECT u.nombre AS unidad, u.siglas,
           SUM(c.desayuno) AS desayunos, SUM(c.almuerzo) AS almuerzos, SUM(c.merienda) AS meriendas
    FROM confronta c
    JOIN personal p ON c.id_personal = p.id_personal
    JOIN unidad u   ON p.id_unidad   = u.id_unidad
    WHERE c.fecha = ?
    GROUP BY u.id_unidad, u.nombre, u.siglas
    ORDER BY u.nombre`, [fecha]);

  res.json({
    fecha,
    desayunos: Number(tot[0].desayunos || 0),
    almuerzos: Number(tot[0].almuerzos || 0),
    meriendas: Number(tot[0].meriendas || 0),
    personas: Number(tot[0].personas || 0),
    por_unidad: porUnidad.map((u) => ({
      unidad: u.unidad, siglas: u.siglas,
      desayunos: Number(u.desayunos), almuerzos: Number(u.almuerzos), meriendas: Number(u.meriendas),
    })),
  });
});

// ===============================================================
// REPORTES (en tiempo real) — resumen del día para gestores
// ===============================================================
app.get('/api/reporte/:fecha', verificarToken, soloRol('ADMIN', 'OPERADOR'), async (req, res) => {
  const tarifa = await tarifaVigente(req.params.fecha);
  const [resumen] = await pool.query(`
    SELECT estado, COUNT(*) AS total,
           SUM(desayuno) AS desayunos,
           SUM(almuerzo) AS almuerzos,
           SUM(merienda) AS meriendas
    FROM confronta WHERE fecha = ?
    GROUP BY estado`, [req.params.fecha]);

  const [tot] = await pool.query(`
    SELECT SUM(desayuno) AS desayunos, SUM(almuerzo) AS almuerzos, SUM(merienda) AS meriendas
    FROM confronta WHERE fecha = ?`, [req.params.fecha]);

  const d = Number(tot[0].desayunos || 0);
  const a = Number(tot[0].almuerzos || 0);
  const me = Number(tot[0].meriendas || 0);

  res.json({
    fecha: req.params.fecha,
    tarifa,
    resumen: resumen.map((r) => ({
      ...r,
      desayunos: Number(r.desayunos), almuerzos: Number(r.almuerzos), meriendas: Number(r.meriendas),
    })),
    totales: {
      desayunos: d, almuerzos: a, meriendas: me,
      total: redondear(d * tarifa.desayuno + a * tarifa.almuerzo + me * tarifa.merienda),
    },
  });
});

// ===============================================================
// KPIs — GOBIERNO DE TI (indicadores para el mando, solo ADMIN)
// ===============================================================
app.get('/api/kpis', verificarToken, soloRol('ADMIN'), async (_req, res) => {
  const ahora = new Date();
  const hoy = ahora.toISOString().slice(0, 10);
  const anio = ahora.getFullYear();
  const mes = ahora.getMonth() + 1;

  const [[pers]] = await pool.query('SELECT COUNT(*) c FROM personal WHERE activo = 1');
  const [roles] = await pool.query('SELECT rol, COUNT(*) c FROM usuario GROUP BY rol');
  const [[rsv]] = await pool.query(
    'SELECT COALESCE(SUM(desayuno+almuerzo+merienda),0) r FROM confronta WHERE fecha = ?', [hoy]);
  const [[cons]] = await pool.query(
    "SELECT COUNT(*) c, COALESCE(SUM(precio),0) m FROM ticket WHERE estado='CANJEADO' AND fecha = ?", [hoy]);
  const [[saldo]] = await pool.query('SELECT COALESCE(SUM(saldo),0) s FROM usuario');
  const [[recmes]] = await pool.query(
    'SELECT COALESCE(SUM(monto),0) m FROM recarga WHERE YEAR(fecha_hora)=? AND MONTH(fecha_hora)=?', [anio, mes]);
  const [[consmes]] = await pool.query(
    "SELECT COALESCE(SUM(precio),0) m FROM ticket WHERE estado IN ('ACTIVO','CANJEADO') AND YEAR(creado_en)=? AND MONTH(creado_en)=?", [anio, mes]);

  const reservadasHoy = Number(rsv.r);
  const consumidasHoy = Number(cons.c);
  const cumplimiento = reservadasHoy > 0 ? Math.round((consumidasHoy / reservadasHoy) * 100) : 0;

  res.json({
    fecha: hoy,
    personal_activo: Number(pers.c),
    usuarios_por_rol: roles.map((r) => ({ rol: r.rol, total: Number(r.c) })),
    reservas_hoy: reservadasHoy,
    consumos_hoy: consumidasHoy,
    cumplimiento_hoy_pct: cumplimiento,
    desperdicio_hoy: Math.max(0, reservadasHoy - consumidasHoy),
    costo_consumido_hoy: redondear(Number(cons.m)),
    saldo_en_circulacion: redondear(Number(saldo.s)),
    recaudo_mes: redondear(Number(recmes.m)),
    consumo_mes: redondear(Number(consmes.m)),
  });
});

// ===============================================================
// REPORTES EXCEL (.xlsx) — uno por rol
// ===============================================================
function fdt(d) {
  if (!d) return '';
  const x = new Date(d);
  return `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')} ` +
         `${String(x.getHours()).padStart(2, '0')}:${String(x.getMinutes()).padStart(2, '0')}`;
}
function fd(d) {
  if (!d) return '';
  const x = new Date(d);
  return `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`;
}
async function enviarExcel(res, wb, nombre) {
  res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
  res.setHeader('Content-Disposition', `attachment; filename="${nombre}"`);
  await wb.xlsx.write(res);
  res.end();
}

// Usuario (cualquiera): mis movimientos y saldo
app.get('/api/reportes/mi-consumo.xlsx', verificarToken, async (req, res) => {
  const [[u]] = await pool.query('SELECT saldo FROM usuario WHERE id_usuario = ?', [req.usuario.id_usuario]);
  const [mov] = await pool.query(`
    SELECT tipo, monto, fecha_hora, comida FROM (
      SELECT 'RECARGA' tipo, monto, fecha_hora, NULL comida FROM recarga WHERE id_usuario = ?
      UNION ALL
      SELECT 'CONSUMO' tipo, (-precio) monto, creado_en fecha_hora, comida FROM ticket
        WHERE id_usuario = ? AND estado IN ('ACTIVO','CANJEADO')
    ) t ORDER BY fecha_hora DESC`, [req.usuario.id_usuario, req.usuario.id_usuario]);

  const wb = new ExcelJS.Workbook();
  const h = wb.addWorksheet('Mis movimientos');
  h.addRow([`Usuario: ${req.usuario.username}`]);
  h.addRow([`Saldo actual: $${redondear(Number(u.saldo))}`]);
  h.addRow([]);
  h.addRow(['Fecha', 'Tipo', 'Comida', 'Monto ($)']);
  h.getRow(4).font = { bold: true };
  mov.forEach((m) => h.addRow([fdt(m.fecha_hora), m.tipo, m.comida || '', Number(m.monto)]));
  h.columns.forEach((c) => { c.width = 18; });
  await enviarExcel(res, wb, 'mi_consumo.xlsx');
});

// Ranchero/Admin: producción por fecha
app.get('/api/reportes/produccion.xlsx', verificarToken, soloRol('RANCHERO', 'ADMIN'), async (req, res) => {
  const fecha = (req.query.fecha || new Date().toISOString().slice(0, 10));
  const [rows] = await pool.query(`
    SELECT u.nombre unidad, u.siglas,
           SUM(c.desayuno) d, SUM(c.almuerzo) a, SUM(c.merienda) m
    FROM confronta c
    JOIN personal p ON c.id_personal = p.id_personal
    JOIN unidad u   ON p.id_unidad   = u.id_unidad
    WHERE c.fecha = ? GROUP BY u.id_unidad, u.nombre, u.siglas ORDER BY u.nombre`, [fecha]);
  const wb = new ExcelJS.Workbook();
  const h = wb.addWorksheet('Producción');
  h.addRow([`Producción de rancho — ${fecha}`]);
  h.addRow([]);
  h.addRow(['Unidad', 'Siglas', 'Desayunos', 'Almuerzos', 'Meriendas']);
  h.getRow(3).font = { bold: true };
  let td = 0, ta = 0, tm = 0;
  rows.forEach((r) => { td += Number(r.d); ta += Number(r.a); tm += Number(r.m); h.addRow([r.unidad, r.siglas, Number(r.d), Number(r.a), Number(r.m)]); });
  h.addRow(['TOTAL', '', td, ta, tm]).font = { bold: true };
  h.columns.forEach((c) => { c.width = 22; });
  await enviarExcel(res, wb, `produccion_${fecha}.xlsx`);
});

// Tesorero/Admin: recargas realizadas
app.get('/api/reportes/recargas.xlsx', verificarToken, soloRol('TESORERO', 'ADMIN'), async (_req, res) => {
  const [rows] = await pool.query(`
    SELECT r.fecha_hora, p.cedula, p.nombres, p.apellidos, r.monto, pt.nombres t_nom, pt.apellidos t_ape
    FROM recarga r
    JOIN usuario us ON r.id_usuario = us.id_usuario
    LEFT JOIN personal p  ON us.id_personal = p.id_personal
    LEFT JOIN usuario ust ON r.id_tesorero = ust.id_usuario
    LEFT JOIN personal pt ON ust.id_personal = pt.id_personal
    ORDER BY r.fecha_hora DESC`);
  const wb = new ExcelJS.Workbook();
  const h = wb.addWorksheet('Recargas');
  h.addRow(['Fecha', 'Cédula', 'Militar', 'Monto ($)', 'Tesorero']);
  h.getRow(1).font = { bold: true };
  let total = 0;
  rows.forEach((r) => {
    total += Number(r.monto);
    h.addRow([fdt(r.fecha_hora), r.cedula || '', `${r.apellidos || ''} ${r.nombres || ''}`.trim(),
      Number(r.monto), `${r.t_ape || ''} ${r.t_nom || ''}`.trim()]);
  });
  h.addRow(['', '', 'TOTAL', total, '']).font = { bold: true };
  h.columns.forEach((c) => { c.width = 22; });
  await enviarExcel(res, wb, 'recargas.xlsx');
});

// Admin: reporte general (varias hojas)
app.get('/api/reportes/general.xlsx', verificarToken, soloRol('ADMIN'), async (_req, res) => {
  const wb = new ExcelJS.Workbook();

  const [usuarios] = await pool.query(`
    SELECT us.username, us.rol, us.activo, us.saldo, p.nombres, p.apellidos, g.abreviatura grado, u.siglas unidad
    FROM usuario us
    LEFT JOIN personal p ON us.id_personal = p.id_personal
    LEFT JOIN grado g ON p.id_grado = g.id_grado
    LEFT JOIN unidad u ON p.id_unidad = u.id_unidad ORDER BY us.username`);
  const hu = wb.addWorksheet('Usuarios');
  hu.addRow(['Cédula/Usuario', 'Rol', 'Activo', 'Saldo', 'Grado', 'Nombres', 'Apellidos', 'Unidad']);
  hu.getRow(1).font = { bold: true };
  usuarios.forEach((u) => hu.addRow([u.username, u.rol, u.activo ? 'Sí' : 'No', Number(u.saldo),
    u.grado || '', u.nombres || '', u.apellidos || '', u.unidad || '']));
  hu.columns.forEach((c) => { c.width = 18; });

  const [consumos] = await pool.query(`
    SELECT t.fecha, t.comida, t.precio, t.canjeado_en, p.cedula, p.nombres, p.apellidos
    FROM ticket t JOIN usuario us ON t.id_usuario = us.id_usuario
    LEFT JOIN personal p ON us.id_personal = p.id_personal
    WHERE t.estado='CANJEADO' ORDER BY t.canjeado_en DESC`);
  const hc = wb.addWorksheet('Consumos');
  hc.addRow(['Fecha', 'Comida', 'Precio', 'Canjeado', 'Cédula', 'Militar']);
  hc.getRow(1).font = { bold: true };
  consumos.forEach((c) => hc.addRow([fd(c.fecha), c.comida, Number(c.precio), fdt(c.canjeado_en),
    c.cedula || '', `${c.apellidos || ''} ${c.nombres || ''}`.trim()]));
  hc.columns.forEach((c) => { c.width = 18; });

  const [aud] = await pool.query(`
    SELECT a.fecha_hora, a.accion, a.detalle, us.username
    FROM auditoria a LEFT JOIN usuario us ON a.id_usuario = us.id_usuario
    ORDER BY a.fecha_hora DESC LIMIT 500`);
  const ha = wb.addWorksheet('Auditoría');
  ha.addRow(['Fecha', 'Acción', 'Detalle', 'Usuario']);
  ha.getRow(1).font = { bold: true };
  aud.forEach((a) => ha.addRow([fdt(a.fecha_hora), a.accion, a.detalle || '', a.username || '']));
  ha.columns.forEach((c) => { c.width = 22; });

  await enviarExcel(res, wb, 'reporte_general.xlsx');
});

// ===============================================================
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`API escuchando en http://localhost:${PORT}`));
