package com.brigada.confronta.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.Sesion
import com.brigada.confronta.data.TotpCodigoReq
import com.brigada.confronta.data.TotpDesactivarReq
import com.brigada.confronta.databinding.ActivitySeguridadBinding
import kotlinx.coroutines.launch

/**
 * Verificación en dos pasos (TOTP, RFC 6238).
 *
 * La inscripción es en dos tiempos a propósito: primero se muestra el QR
 * y recién cuando el usuario confirma un código el servidor la activa.
 * Así nadie se queda fuera de su cuenta por haber escaneado mal.
 */
class SeguridadActivity : AppCompatActivity() {

    companion object {
        /** Se abre como paso forzado del ingreso, no como ajuste opcional. */
        const val EXTRA_OBLIGATORIO = "obligatorio"
    }

    private lateinit var b: ActivitySeguridadBinding

    /** Cuenta que todavía no puede usar la app hasta inscribirse. */
    private var obligatorio = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySeguridadBinding.inflate(layoutInflater)
        setContentView(b.root)

        obligatorio = intent.getBooleanExtra(EXTRA_OBLIGATORIO, false)
        supportActionBar?.title =
            if (obligatorio) "Activa tu verificación" else "Verificación en dos pasos"

        if (obligatorio) {
            b.tvObligatorio.visibility = View.VISIBLE
            // Volver atrás sin inscribirse dejaría la sesión inservible: se
            // sale del todo, que es lo único coherente.
            onBackPressedDispatcher.addCallback(this,
                object : androidx.activity.OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() = salir()
                })
        }

        b.btnEmpezar.setOnClickListener { iniciarInscripcion() }
        b.btnActivar.setOnClickListener { activar() }
        b.btnNuevosRespaldos.setOnClickListener { pedirCodigoY(::regenerarRespaldos) }
        b.btnDesactivar.setOnClickListener { desactivar() }

        consultarEstado()
    }

    private fun salir() {
        Sesion.cerrar()
        startActivity(android.content.Intent(this, LoginActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                      android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun cargando(activo: Boolean) {
        b.progreso.visibility = if (activo) View.VISIBLE else View.GONE
    }

    private fun consultarEstado() {
        cargando(true)
        lifecycleScope.launch {
            try {
                val r = ApiClient.api.totpEstado()
                if (r.isSuccessful && r.body() != null) {
                    val e = r.body()!!
                    Sesion.totpActivado = e.activado
                    if (e.activado) {
                        b.tvEstado.text = "✅  Activada"
                        b.tvDetalle.text =
                            "Activada el ${fechaHora(e.activado_en)}\n" +
                            "Te quedan ${e.codigos_respaldo_disponibles} códigos de respaldo."
                        b.btnEmpezar.visibility = View.GONE
                        b.panelInscripcion.visibility = View.GONE
                        b.tvObligatorio.visibility = View.GONE
                        b.btnNuevosRespaldos.visibility = View.VISIBLE
                        // Solo el ADMIN puede apagarlo: para el resto es
                        // obligatorio y el servidor rechaza el intento.
                        b.btnDesactivar.visibility =
                            if (Sesion.rol == "ADMIN") View.VISIBLE else View.GONE
                    } else {
                        b.tvEstado.text = "⚠️  Desactivada"
                        b.tvDetalle.text = "Tu cuenta entra solo con la contraseña."
                        b.btnEmpezar.visibility = View.VISIBLE
                        b.btnNuevosRespaldos.visibility = View.GONE
                        b.btnDesactivar.visibility = View.GONE
                    }
                } else toast(errorDeApi(r))
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                cargando(false)
            }
        }
    }

    private fun iniciarInscripcion() {
        cargando(true)
        lifecycleScope.launch {
            try {
                val r = ApiClient.api.totpIniciar()
                if (r.isSuccessful && r.body() != null) {
                    val i = r.body()!!
                    // El QR es el mismo generador que usan los tickets de
                    // comida; aquí lo que se codifica es la URI otpauth://
                    b.imgQr.setImageBitmap(generarQrBitmap(i.uri))
                    b.tvSecreto.text = i.secreto
                    b.panelInscripcion.visibility = View.VISIBLE
                    b.btnEmpezar.visibility = View.GONE
                } else toast(errorDeApi(r))
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                cargando(false)
            }
        }
    }

    private fun activar() {
        val codigo = b.etCodigo.text?.toString()?.trim().orEmpty()
        if (codigo.length != 6) { toast("Escribe los 6 dígitos"); return }
        cargando(true)
        lifecycleScope.launch {
            try {
                val r = ApiClient.api.totpActivar(TotpCodigoReq(codigo))
                if (r.isSuccessful && r.body() != null) {
                    b.etCodigo.text = null
                    // El token que traíamos decía que la cuenta no tenía
                    // segundo factor; el servidor manda uno nuevo que ya lo
                    // reconoce. Sin cambiarlo, la app quedaría bloqueada.
                    r.body()!!.token?.let { Sesion.token = it }
                    Sesion.totpActivado = true
                    mostrarRespaldos(r.body()!!.codigos_respaldo.orEmpty(), activando = true)
                } else toast(errorDeApi(r))
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                cargando(false)
            }
        }
    }

    private fun regenerarRespaldos(codigo: String) {
        cargando(true)
        lifecycleScope.launch {
            try {
                val r = ApiClient.api.totpNuevosRespaldos(TotpCodigoReq(codigo))
                if (r.isSuccessful && r.body() != null)
                    mostrarRespaldos(r.body()!!.codigos_respaldo.orEmpty(), activando = false)
                else toast(errorDeApi(r))
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                cargando(false)
            }
        }
    }

    /**
     * Los códigos se muestran UNA sola vez: en el servidor quedan
     * hasheados y no hay forma de volver a leerlos.
     */
    private fun mostrarRespaldos(codigos: List<String>, activando: Boolean) {
        val texto = TextView(this).apply {
            text = codigos.joinToString("\n")
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 17f
            setTextIsSelectable(true)
            setPadding(60, 30, 60, 20)
            setTextColor(getColor(com.brigada.confronta.R.color.texto_principal))
        }
        AlertDialog.Builder(this)
            .setTitle(if (activando) "✅ Activada — guarda estos códigos" else "Códigos nuevos")
            .setMessage(
                "Anótalos en papel y guárdalos aparte del teléfono.\n\n" +
                "Cada uno sirve UNA vez y son tu única forma de entrar si pierdes el " +
                "teléfono. No se pueden volver a ver.")
            .setView(texto)
            .setCancelable(false)
            .setPositiveButton("Ya los guardé") { _, _ ->
                // Si era el paso obligatorio del ingreso, recién ahora la
                // cuenta puede usar la app: se entra al menú.
                if (obligatorio && activando) {
                    startActivity(android.content.Intent(this, MenuActivity::class.java))
                    finish()
                } else {
                    consultarEstado()
                }
            }
            .show()
    }

    /** Pide un código vigente antes de una operación delicada. */
    private fun pedirCodigoY(accion: (String) -> Unit) {
        val campo = EditText(this).apply {
            hint = "Código de 6 dígitos"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(50, 40, 50, 40)
        }
        AlertDialog.Builder(this)
            .setTitle("Confirma con tu código")
            .setView(campo)
            .setPositiveButton("Continuar") { _, _ ->
                val c = campo.text?.toString()?.trim().orEmpty()
                if (c.isEmpty()) toast("Escribe el código") else accion(c)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Para apagar el segundo factor se piden los dos: contraseña y código.
     * Si bastara con la sesión abierta, quien tome el teléfono desbloqueado
     * podría quitarlo y dejar la cuenta con sola contraseña.
     */
    private fun desactivar() {
        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }
        val clave = EditText(this).apply {
            hint = "Tu contraseña"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val codigo = EditText(this).apply {
            hint = "Código de 6 dígitos"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        caja.addView(clave)
        caja.addView(codigo)

        AlertDialog.Builder(this)
            .setTitle("Desactivar la verificación")
            .setMessage("Tu cuenta volverá a entrar solo con la contraseña.")
            .setView(caja)
            .setPositiveButton("Desactivar") { _, _ ->
                val p = clave.text?.toString().orEmpty()
                val c = codigo.text?.toString()?.trim().orEmpty()
                if (p.isEmpty() || c.isEmpty()) { toast("Completa los dos campos"); return@setPositiveButton }
                cargando(true)
                lifecycleScope.launch {
                    try {
                        val r = ApiClient.api.totpDesactivar(TotpDesactivarReq(p, c))
                        if (r.isSuccessful) {
                            toast("Verificación en dos pasos desactivada")
                            consultarEstado()
                        } else toast(errorDeApi(r))
                    } catch (e: Exception) {
                        toast("No se pudo conectar con el servidor.\n${e.message}")
                    } finally {
                        cargando(false)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
