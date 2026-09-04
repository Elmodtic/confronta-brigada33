package com.brigada.confronta.data

// ---- Autenticación / cuentas ----
data class LoginReq(val username: String, val password: String)

data class LoginResp(
    val token: String,
    val rol: String,
    val username: String,
    val id_usuario: Int,
    val id_personal: Int?,
    val grado: String?,
    val nombres: String?,
    val apellidos: String?
)

data class Kpis(
    val fecha: String,
    val personal_activo: Int,
    val usuarios_por_rol: List<RolConteo>,
    val reservas_hoy: Int,
    val consumos_hoy: Int,
    val cumplimiento_hoy_pct: Int,
    val desperdicio_hoy: Int,
    val costo_consumido_hoy: Double,
    val saldo_en_circulacion: Double,
    val recaudo_mes: Double,
    val consumo_mes: Double
)

data class RolConteo(val rol: String, val total: Int)

data class RegistroReq(
    val password: String,
    val pregunta_seguridad: String,
    val respuesta_seguridad: String,
    val cedula: String,          // la cédula es también el usuario de acceso
    val nombres: String,
    val apellidos: String,
    val id_grado: Int,
    val id_unidad: Int
)

data class RegistroResp(val ok: Boolean, val id_usuario: Int?, val id_personal: Int?)

data class PreguntaResp(val pregunta_seguridad: String)

data class ResetReq(
    val username: String,
    val respuesta_seguridad: String,
    val nueva_password: String
)

data class OkResp(val ok: Boolean?)

// ---- Catálogos ----
data class Grado(val id_grado: Int, val nombre: String, val abreviatura: String) {
    override fun toString() = "$abreviatura - $nombre"
}

data class Unidad(val id_unidad: Int, val nombre: String, val siglas: String?, val descripcion: String?) {
    override fun toString() = if (siglas.isNullOrBlank()) nombre else "$siglas — $nombre"
}

// ---- Tarifa ----
data class Tarifa(val desayuno: Double, val almuerzo: Double, val merienda: Double)

// ---- Confronta / consumo ----
data class ConfrontaReq(
    val id_personal: Int?,
    val fecha: String,
    val estado: String,
    val desayuno: Boolean,
    val almuerzo: Boolean,
    val merienda: Boolean,
    val novedad: String?
)

data class ConfrontaResp(
    val ok: Boolean,
    val id_personal: Int?,
    val fecha: String?,
    val costo: Double
)

data class DiaConsumo(
    val fecha: String,
    val estado: String,
    val desayuno: Boolean,
    val almuerzo: Boolean,
    val merienda: Boolean,
    val novedad: String?,
    val costo: Double
)

data class TotalesMes(
    val desayunos: Int,
    val almuerzos: Int,
    val meriendas: Int,
    val dias_con_consumo: Int,
    val total: Double
)

data class ConsumoMes(
    val anio: Int,
    val mes: Int,
    val mes_nombre: String,
    val tarifa: Tarifa,
    val dias: List<DiaConsumo>,
    val totales: TotalesMes
)

// ---- Historial por meses ----
data class MesHistorial(
    val mes: Int,
    val mes_nombre: String,
    val desayunos: Int,
    val almuerzos: Int,
    val meriendas: Int,
    val total: Double
)

data class HistorialAnio(
    val anio: Int,
    val meses: List<MesHistorial>,
    val total_anio: Double
)

// ---- Liquidación ----
data class Persona(
    val id_personal: Int,
    val cedula: String?,
    val nombres: String,
    val apellidos: String,
    val grado: String?,
    val unidad: String?
)

data class Liquidacion(
    val persona: Persona?,
    val anio: Int,
    val mes: Int,
    val mes_nombre: String,
    val tarifa: Tarifa,
    val desayunos: Int,
    val almuerzos: Int,
    val meriendas: Int,
    val subtotal_desayuno: Double,
    val subtotal_almuerzo: Double,
    val subtotal_merienda: Double,
    val total: Double
)

// ---- Administración (root) ----
data class UsuarioAdmin(
    val id_usuario: Int,
    val username: String,
    val rol: String,
    val activo: Boolean,
    val nombres: String?,
    val apellidos: String?,
    val grado: String?,
    val unidad: String?,
    val cedula: String? = null,
    val saldo: Double = 0.0
)

data class UpdateUsuarioReq(val rol: String? = null, val activo: Boolean? = null)

data class AdminPassReq(val nueva_password: String)

data class AuditoriaItem(
    val id_auditoria: Int,
    val accion: String,
    val detalle: String?,
    val fecha_hora: String?,
    val username: String?
)

// ---- Producción (ranchero) ----
data class ProdUnidad(
    val unidad: String,
    val siglas: String?,
    val desayunos: Int,
    val almuerzos: Int,
    val meriendas: Int
)

data class Produccion(
    val fecha: String,
    val desayunos: Int,
    val almuerzos: Int,
    val meriendas: Int,
    val personas: Int,
    val por_unidad: List<ProdUnidad>
)

// ---- Saldo y movimientos ----
data class Movimiento(
    val tipo: String,        // RECARGA | CONSUMO
    val monto: Double,
    val fecha_hora: String?,
    val comida: String?
)

data class MiEstado(val saldo: Double, val movimientos: List<Movimiento>)

// ---- Tesorería ----
data class PersonaSaldo(
    val id_usuario: Int,
    val saldo: Double,
    val cedula: String,
    val nombres: String,
    val apellidos: String,
    val grado: String?,
    val unidad: String?
)

data class RecargaReq(val cedula: String, val monto: Double)
data class RecargaResp(val ok: Boolean, val saldo: Double)

// ---- Reserva (se cobra al reservar) ----
data class ReservaReq(
    val fecha: String,
    val estado: String,
    val desayuno: Boolean,
    val almuerzo: Boolean,
    val merienda: Boolean,
    val novedad: String?
)

data class ReservaResp(
    val ok: Boolean,
    val fecha: String?,
    val cobrado: Double,
    val reembolsado: Double,
    val saldo: Double
)

// ---- QR ----
data class QrReq(val fecha: String, val comida: String)
data class QrResp(val token: String, val comida: String, val fecha: String, val precio: Double)

data class CanjeReq(val token: String)
data class CanjeResp(
    val ok: Boolean,
    val persona: String?,
    val unidad: String?,
    val comida: String?,
    val fecha: String?,
    val monto: Double
)

// ---- Perfil ----
data class Perfil(
    val id_personal: Int?,
    val cedula: String?,
    val nombres: String?,
    val apellidos: String?,
    val grado: String?,
    val grado_nombre: String?,
    val unidad: String?,
    val username: String?,
    val rol: String?
)

// ===================================================================
// GESTIÓN DE USUARIOS — respuesta paginada con búsqueda en el servidor
// ===================================================================
data class UsuariosResp(
    val total_registrados: Int,
    val mostrados: Int,
    val limite: Int,
    val buscar: String?,
    val usuarios: List<UsuarioAdmin>
)

// ===================================================================
// INDICADORES — bloque reutilizable para el día y para el mes
// ===================================================================
data class BloqueKpi(
    val reservas: Int,
    val consumos: Int,
    val cumplimiento_pct: Int,
    val desperdicio: Int,
    val costo_consumido: Double,
    val recaudado: Double,
    val transferido: Double
)

data class KpisPeriodo(
    val fecha: String,
    val anio: Int,
    val mes: Int,
    val mes_nombre: String,
    val personal_activo: Int,
    val usuarios_por_rol: List<RolConteo>,
    val saldo_en_circulacion: Double,
    val dia: BloqueKpi,
    val mes_resumen: BloqueKpi
)

// ===================================================================
// TESORERÍA — contabilidad y fondo rotativo del rancho
// ===================================================================
data class MovimientoCaja(
    val recaudado: Double,
    val recargas: Int,
    val transferido: Double,
    val entregas: Int,
    val neto: Double
)

data class Acumulado(
    val recaudado: Double,
    val transferido: Double,
    val en_transito: Double,
    val pendientes: Int,
    val caja: Double
)

data class RancheroFondo(
    val id_usuario: Int,
    val cedula: String?,
    val persona: String,
    val saldo_comensal: Double,
    val fondo_rancho: Double,
    val en_transito: Double
)

data class TesoreriaResumen(
    val fecha: String,
    val anio: Int,
    val mes: Int,
    val mes_nombre: String,
    val dia: MovimientoCaja,
    val mes_resumen: MovimientoCaja,
    val acumulado: Acumulado,
    val mi_saldo_comensal: Double,
    val rancheros: List<RancheroFondo>
)

data class TransferenciaReq(
    val id_ranchero: Int,
    val monto: Double,
    val concepto: String?
)

data class TransferenciaResp(
    val ok: Boolean,
    val id_transferencia: Int?,
    val token: String?,
    val estado: String?,
    val monto: Double,
    val fecha_hora: String?,
    val caja_restante: Double,
    val mensaje: String?
)

data class EntregaItem(
    val id_transferencia: Int,
    val monto: Double,
    val concepto: String?,
    val fecha_hora: String?,
    val estado: String?,
    val confirmado_en: String?,
    val anulado_en: String?,
    val token: String?,
    val ranchero: String?
)

data class EntregasResp(
    val anio: Int,
    val mes: Int,
    val mes_nombre: String,
    val total: Double,
    val confirmado: Double,
    val pendiente: Double,
    val entregas: List<EntregaItem>
)

// ===================================================================
// FONDO DEL RANCHO — vista del ranchero
// ===================================================================
data class MovimientoFondo(
    val monto: Double,
    val concepto: String?,
    val fecha_hora: String?,
    val confirmado_en: String?,
    val estado: String?,
    val tesorero: String?
)

data class FondoRancho(
    val fecha: String,
    val anio: Int,
    val mes: Int,
    val mes_nombre: String,
    val saldo_comensal: Double,
    val fondo_rancho: Double,
    val por_confirmar: Double,
    val entregas_por_confirmar: Int,
    val recibido_dia: Double,
    val entregas_dia: Int,
    val recibido_mes: Double,
    val entregas_mes: Int,
    val movimientos: List<MovimientoFondo>
)

// ---- Confirmación del fondo por QR (ranchero) ----
data class ConfirmarFondoReq(val token: String)

data class ConfirmarFondoResp(
    val ok: Boolean,
    val id_transferencia: Int?,
    val monto: Double,
    val concepto: String?,
    val entregado_en: String?,
    val tesorero: String?,
    val fondo_rancho: Double
)

// ---- Auditoría con filtros ----
data class AuditoriaRegistro(
    val id_auditoria: Int,
    val accion: String,
    val detalle: String?,
    val fecha_hora: String?,
    val username: String?,
    val cedula: String?,
    val persona: String?
)

data class FiltroAuditoria(
    val fecha: String?,
    val anio: Int?,
    val mes: Int?,
    val buscar: String?
)

data class AuditoriaResp(
    val total_registros: Int,
    val mostrados: Int,
    val limite: Int,
    val filtro: FiltroAuditoria,
    val registros: List<AuditoriaRegistro>
)
