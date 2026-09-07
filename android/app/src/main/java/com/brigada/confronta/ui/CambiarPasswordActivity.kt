package com.brigada.confronta.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.CambiarPasswordReq
import com.brigada.confronta.data.Sesion
import com.brigada.confronta.databinding.ActivityCambiarPasswordBinding
import kotlinx.coroutines.launch

/**
 * Cambio obligatorio de contraseña tras un reinicio del administrador.
 *
 * La contraseña temporal es la propia cédula, un dato que conoce media
 * unidad, así que el servidor no deja hacer nada más hasta cambiarla.
 * Terminado esto viene la inscripción del segundo factor.
 */
class CambiarPasswordActivity : AppCompatActivity() {

    private lateinit var b: ActivityCambiarPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCambiarPasswordBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Actualiza tu contraseña"

        // Salir sin cambiarla dejaría una sesión que no sirve para nada.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Sesion.cerrar()
                startActivity(Intent(this@CambiarPasswordActivity, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        })

        b.btnGuardar.setOnClickListener { guardar() }
    }

    private fun guardar() {
        val actual = b.etActual.text?.toString().orEmpty()
        val nueva = b.etNueva.text?.toString().orEmpty()
        val confirma = b.etConfirmar.text?.toString().orEmpty()

        if (actual.isEmpty()) { toast("Escribe tu contraseña actual"); return }
        if (nueva != confirma) { toast("Las dos contraseñas nuevas no coinciden"); return }
        // Se valida aquí con la misma política del servidor para no gastar
        // un viaje de red en un error que se ve al instante.
        validarPassword(nueva)?.let { toast(it); return }

        cargando(true)
        lifecycleScope.launch {
            try {
                val r = ApiClient.api.cambiarPassword(CambiarPasswordReq(actual, nueva))
                if (r.isSuccessful && r.body() != null) {
                    val c = r.body()!!
                    // La sesión anterior sigue marcada como "debe cambiar";
                    // el servidor manda una nueva ya limpia.
                    c.token?.let { Sesion.token = it }
                    toast("Contraseña actualizada")
                    if (c.totp_obligatorio) {
                        startActivity(Intent(this@CambiarPasswordActivity, SeguridadActivity::class.java)
                            .putExtra(SeguridadActivity.EXTRA_OBLIGATORIO, true))
                    } else {
                        startActivity(Intent(this@CambiarPasswordActivity, MenuActivity::class.java))
                    }
                    finish()
                } else toast(errorDeApi(r))
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                cargando(false)
            }
        }
    }

    private fun cargando(activo: Boolean) {
        b.progreso.visibility = if (activo) View.VISIBLE else View.GONE
        b.btnGuardar.isEnabled = !activo
    }
}
