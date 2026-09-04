package com.brigada.confronta.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.AdminPassReq
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.UpdateUsuarioReq
import com.brigada.confronta.data.RelevoReq
import com.brigada.confronta.data.UsuarioAdmin
import com.brigada.confronta.databinding.ActivityUsuariosBinding
import com.brigada.confronta.databinding.DialogUsuarioBinding
import kotlinx.coroutines.launch

/**
 * Gestión de usuarios y roles.
 *
 * La búsqueda se resuelve en el servidor (`GET /api/usuarios?buscar=`) y se
 * limita el número de resultados: con miles de fichas no tiene sentido bajar
 * la nómina completa al teléfono ni obligar al administrador a desplazarse.
 */
class UsuariosActivity : AppCompatActivity() {

    private lateinit var b: ActivityUsuariosBinding

    // El orden DEBE coincidir con el array @array/roles del diálogo, porque
    // el rol se resuelve por posición del spinner.
    private val ROLES = listOf("ADMIN", "OPERADOR", "CONSULTA", "RANCHERO", "TESORERO")

    private val LIMITE = 50

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityUsuariosBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Gestión de usuarios"

        b.btnBuscar.setOnClickListener { buscar() }
        b.etBuscar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { buscar(); true } else false
        }

        // Primera carga: los más recientes, sin filtro.
        buscar()
    }

    private fun buscar() {
        val texto = b.etBuscar.text?.toString()?.trim().orEmpty()
        b.progreso.visibility = View.VISIBLE
        b.contenedorUsuarios.removeAllViews()
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.usuarios(
                    buscar = texto.ifBlank { null },
                    limite = LIMITE)
                if (resp.isSuccessful && resp.body() != null) {
                    val r = resp.body()!!
                    b.tvResumen.text = if (texto.isBlank())
                        "Mostrando ${r.mostrados} de ${r.total_registrados} usuarios registrados"
                    else
                        "${r.mostrados} coincidencia(s) para \"$texto\" · ${r.total_registrados} usuarios en total"
                    if (r.usuarios.isEmpty()) sinResultados(texto) else pintar(r.usuarios)
                    if (r.mostrados >= r.limite)
                        toast("Se muestran los primeros ${r.limite}. Afina la búsqueda.")
                } else toast(errorDeApi(resp))
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
            }
        }
    }

    private fun sinResultados(texto: String) {
        b.contenedorUsuarios.addView(TextView(this).apply {
            text = "Sin resultados para \"$texto\"."
            textSize = 15f
            setPadding(20, 40, 20, 40)
            gravity = Gravity.CENTER
            setTextColor(getColor(com.brigada.confronta.R.color.texto_secundario))
        })
    }

    private fun pintar(usuarios: List<UsuarioAdmin>) {
        for (u in usuarios) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 26, 20, 26)
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { editarUsuario(u) }
            }
            val nombre = listOfNotNull(u.nombres, u.apellidos)
                .joinToString(" ").ifBlank { "(sin ficha)" }
            val izq = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "${u.username}\n$nombre"
                textSize = 15f
            }
            val der = TextView(this).apply {
                text = if (u.activo) u.rol else "${u.rol} · inactivo"
                textSize = 13f
                gravity = Gravity.END
                setTextColor(getColor(
                    if (u.activo) com.brigada.confronta.R.color.verde_militar
                    else com.brigada.confronta.R.color.rojo_novedad))
            }
            fila.addView(izq)
            fila.addView(der)
            b.contenedorUsuarios.addView(fila)

            val linea = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(0xFFE0E0E0.toInt())
            }
            b.contenedorUsuarios.addView(linea)
        }
    }

    private fun editarUsuario(u: UsuarioAdmin) {
        val db = DialogUsuarioBinding.inflate(layoutInflater)
        db.spRol.setSelection(ROLES.indexOf(u.rol).coerceAtLeast(0))
        db.swActivo.isChecked = u.activo

        val nombre = listOfNotNull(u.nombres, u.apellidos).joinToString(" ")
        AlertDialog.Builder(this)
            .setTitle(u.username)
            .setMessage(if (nombre.isBlank()) null else nombre)
            .setView(db.root)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoRol = ROLES[db.spRol.selectedItemPosition]
                val nuevoActivo = db.swActivo.isChecked
                val nuevaClave = db.etNuevaClave.text?.toString()?.trim().orEmpty()
                guardar(u, nuevoRol, nuevoActivo, nuevaClave)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * El cargo ya tiene titular. Se explica el traspaso y, si el admin
     * acepta, se releva: el saliente entrega el fondo operativo y vuelve a
     * ser comensal comun. Los saldos personales no se tocan.
     */
    private fun ofrecerRelevo(u: UsuarioAdmin, rol: String, motivo: String) {
        val nombre = listOfNotNull(u.nombres, u.apellidos).joinToString(" ").ifBlank { u.username }
        AlertDialog.Builder(this)
            .setTitle("Relevar el cargo de $rol")
            .setMessage(
                "$motivo\n\n" +
                "Si continúas:\n" +
                "  · $nombre pasa a ser $rol\n" +
                "  · el titular actual vuelve a ser OPERADOR\n" +
                (if (rol == "RANCHERO")
                    "  · el fondo del rancho pasa al nuevo titular\n"
                 else
                    "  · queda constancia del efectivo entregado\n") +
                "\nLos saldos personales para comer NO se tocan.")
            .setPositiveButton("Relevar") { _, _ -> relevar(u, rol) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun relevar(u: UsuarioAdmin, rol: String) {
        b.progreso.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val r = ApiClient.api.relevarCargo(u.id_usuario, RelevoReq(rol, null))
                if (r.isSuccessful && r.body() != null) {
                    val x = r.body()!!
                    AlertDialog.Builder(this@UsuariosActivity)
                        .setTitle("Cargo relevado")
                        .setMessage(
                            "Sale: ${x.saliente?.persona ?: "cargo vacante"}\n" +
                            "Entra: ${x.entrante?.persona ?: x.entrante?.username ?: "-"}\n" +
                            "Traspasado: ${money(x.monto_traspasado)}\n\n" +
                            (x.mensaje ?: ""))
                        .setPositiveButton("Listo") { _, _ -> buscar() }
                        .show()
                } else toast(detalleError(r).mensaje)
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
            }
        }
    }

    private fun guardar(u: UsuarioAdmin, rol: String, activo: Boolean, nuevaClave: String) {
        // La clave nueva se valida contra la misma política del servidor.
        if (nuevaClave.isNotEmpty()) {
            val err = validarPassword(nuevaClave)
            if (err != null) { toast(err); return }
        }
        lifecycleScope.launch {
            try {
                if (rol != u.rol || activo != u.activo) {
                    val r = ApiClient.api.actualizarUsuario(
                        u.id_usuario,
                        UpdateUsuarioReq(
                            rol = if (rol != u.rol) rol else null,
                            activo = if (activo != u.activo) activo else null))
                    if (!r.isSuccessful) {
                        val err = detalleError(r)
                        // TESORERO y RANCHERO son cargos unicos. Si ya hay
                        // titular, se ofrece relevarlo en vez de solo fallar.
                        if (err.cargoOcupado) ofrecerRelevo(u, rol, err.mensaje)
                        else toast(err.mensaje)
                        return@launch
                    }
                }
                if (nuevaClave.isNotEmpty()) {
                    val r = ApiClient.api.resetPasswordUsuario(u.id_usuario, AdminPassReq(nuevaClave))
                    if (!r.isSuccessful) { toast(errorDeApi(r)); return@launch }
                }
                toast("Usuario ${u.username} actualizado")
                buscar()
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            }
        }
    }
}
