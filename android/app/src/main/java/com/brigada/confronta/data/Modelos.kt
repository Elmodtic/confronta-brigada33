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
    val unidad: String?
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

// ---- Reserva ----
data class ReservaReq(
    val fecha: String,
    val estado: String,
    val desayuno: Boolean,
    val almuerzo: Boolean,
    val merienda: Boolean,
    val novedad: String?
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
    val monto: Double,
    val saldo_restante: Double
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
