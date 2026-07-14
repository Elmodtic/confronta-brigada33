package com.brigada.confronta.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.AdminPassReq
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.UpdateUsuarioReq
import com.brigada.confronta.data.UsuarioAdmin
import com.brigada.confronta.databinding.ActivityUsuariosBinding
import com.brigada.confronta.databinding.DialogUsuarioBinding
import kotlinx.coroutines.launch

class UsuariosActivity : AppCompatActivity() {

    private lateinit var b: ActivityUsuariosBinding
    private val ROLES = listOf("ADMIN", "OPERADOR", "CONSULTA", "RANCHERO")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityUsuariosBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Gestión de usuarios"
        cargar()
    }

    private fun cargar() {
        b.progreso.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.usuarios()
                if (resp.isSuccessful && resp.body() != null) pintar(resp.body()!!)
                else toast(errorDeApi(resp))
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
            }
        }
    }

    private fun pintar(usuarios: List<UsuarioAdmin>) {
        b.contenedorUsuarios.removeAllViews()
        for (u in usuarios) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 26, 20, 26)
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { editarUsuario(u) }
            }
            val nombre = listOfNotNull(u.nombres, u.apellidos).joinToString(" ").ifBlank { "(sin ficha)" }
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

        AlertDialog.Builder(this)
            .setTitle(u.username)
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

    private fun guardar(u: UsuarioAdmin, rol: String, activo: Boolean, nuevaClave: String) {
        lifecycleScope.launch {
            try {
                // Actualiza rol/activo si cambiaron
                if (rol != u.rol || activo != u.activo) {
                    val r = ApiClient.api.actualizarUsuario(
                        u.id_usuario,
                        UpdateUsuarioReq(
                            rol = if (rol != u.rol) rol else null,
                            activo = if (activo != u.activo) activo else null))
                    if (!r.isSuccessful) { toast(errorDeApi(r)); return@launch }
                }
                // Restablece contraseña si se escribió una
                if (nuevaClave.isNotEmpty()) {
                    val r = ApiClient.api.resetPasswordUsuario(u.id_usuario, AdminPassReq(nuevaClave))
                    if (!r.isSuccessful) { toast(errorDeApi(r)); return@launch }
                }
                toast("Usuario ${u.username} actualizado")
                cargar()
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            }
        }
    }
}
