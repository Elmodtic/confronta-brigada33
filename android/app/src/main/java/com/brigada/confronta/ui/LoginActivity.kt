package com.brigada.confronta.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.CredencialesSeguras
import com.brigada.confronta.data.LoginReq
import com.brigada.confronta.data.Sesion
import com.brigada.confronta.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Sesion.estaLogueado()) {
            irAlMenu()
            return
        }

        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.hide()

        b.btnIngresar.setOnClickListener { ingresar() }
        b.btnOlvido.setOnClickListener { startActivity(Intent(this, OlvidoActivity::class.java)) }
        b.btnCrearCuenta.setOnClickListener { startActivity(Intent(this, RegistroActivity::class.java)) }
        // La direccion del servidor es configuracion tecnica, no algo que el
        // comensal deba ver ni tocar. Queda oculta y se llega a ella de dos
        // formas: manteniendo pulsado el escudo, o automaticamente cuando
        // falla la conexion (que es justo cuando hace falta corregirla).
        b.btnServidor.setOnClickListener { cambiarServidor() }
        b.imgEscudo.setOnLongClickListener { revelarServidor(); true }

        // Si hay credenciales guardadas en este dispositivo, ofrece huella/rostro.
        // Abrir el almacen cifrado deriva la clave maestra en el Keystore y es
        // lento: se hace fuera del hilo principal para no congelar la pantalla.
        lifecycleScope.launch {
            val guardadas = withContext(Dispatchers.IO) { CredencialesSeguras.guardadas(this@LoginActivity) }
            if (guardadas != null) {
                b.btnHuella.visibility = View.VISIBLE
                b.etUsuario.setText(guardadas.first)
                b.btnHuella.setOnClickListener { ingresarConBiometria() }
            }
        }
    }

    /** Deja visible el ajuste del servidor y lo abre. */
    private fun revelarServidor() {
        mostrarServidorActual()
        b.btnServidor.visibility = View.VISIBLE
        cambiarServidor()
    }

    /** Muestra en el botón a qué servidor apunta la app ahora mismo. */
    private fun mostrarServidorActual() {
        val host = ApiClient.baseUrl
            .removePrefix("https://").removePrefix("http://").trimEnd('/')
        b.btnServidor.text = "Servidor: $host"
    }

    /**
     * El servidor se publica con un túnel cuya dirección puede cambiar.
     * En vez de recompilar el APK para todos, cada quien la actualiza aquí.
     */
    private fun cambiarServidor() {
        val campo = EditText(this).apply {
            setText(ApiClient.baseUrl)
            setSingleLine(true)
            setPadding(48, 40, 48, 40)
        }
        AlertDialog.Builder(this)
            .setTitle("Dirección del servidor")
            .setMessage("Pégala tal como te la pasen. Siempre se usa HTTPS.")
            .setView(campo)
            .setPositiveButton("Guardar") { _, _ ->
                val nueva = campo.text?.toString().orEmpty()
                if (nueva.isBlank()) { toast("Escribe una dirección"); return@setPositiveButton }
                ApiClient.baseUrl = nueva
                mostrarServidorActual()
                toast("Servidor actualizado")
            }
            .setNeutralButton("Restablecer") { _, _ ->
                ApiClient.restablecerUrl()
                mostrarServidorActual()
                toast("Se restableció la dirección original")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ingresar() {
        val usuario = b.etUsuario.text?.toString()?.trim().orEmpty()
        val pass = b.etPassword.text?.toString().orEmpty()
        if (usuario.isEmpty() || pass.isEmpty()) {
            toast("Ingresa tu cédula y contraseña"); return
        }
        loginCon(usuario, pass, guardar = b.cbRecordar.isChecked)
    }

    private fun loginCon(usuario: String, pass: String, guardar: Boolean) {
        cargando(true)
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.login(LoginReq(usuario, pass))
                if (resp.isSuccessful && resp.body() != null) {
                    Sesion.guardar(resp.body()!!)
                    if (guardar) withContext(Dispatchers.IO) {
                        CredencialesSeguras.guardar(this@LoginActivity, usuario, pass)
                    }
                    toast("Bienvenido, ${Sesion.nombre ?: usuario}")
                    irAlMenu()
                } else {
                    toast(errorDeApi(resp))
                }
            } catch (e: Exception) {
                // Sin conexión lo más probable es que la dirección del túnel
                // haya cambiado. Se revela el ajuste justo cuando hace falta,
                // para poder corregirlo sin reinstalar la app.
                mostrarServidorActual()
                b.btnServidor.visibility = View.VISIBLE
                toast("No se pudo conectar con el servidor.\n" +
                      "Si te pasaron una dirección nueva, tócala abajo para cambiarla.")
            } finally {
                cargando(false)
            }
        }
    }

    private fun ingresarConBiometria() {
        val bm = BiometricManager.from(this)
        val autenticadores = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (bm.canAuthenticate(autenticadores) != BiometricManager.BIOMETRIC_SUCCESS) {
            toast("Este dispositivo no tiene huella/rostro o PIN configurado")
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    lifecycleScope.launch {
                        val g = withContext(Dispatchers.IO) { CredencialesSeguras.guardadas(this@LoginActivity) }
                        if (g != null) loginCon(g.first, g.second, guardar = false)
                        else toast("No hay credenciales guardadas")
                    }
                }
                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    if (code != BiometricPrompt.ERROR_USER_CANCELED &&
                        code != BiometricPrompt.ERROR_NEGATIVE_BUTTON)
                        toast("Autenticación cancelada: $msg")
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Ingresar a Confronta")
            .setSubtitle("Usa tu huella, rostro o PIN del dispositivo")
            .setAllowedAuthenticators(autenticadores)
            .build()
        prompt.authenticate(info)
    }

    private fun cargando(activo: Boolean) {
        b.progreso.visibility = if (activo) View.VISIBLE else View.GONE
        b.btnIngresar.isEnabled = !activo
    }

    private fun irAlMenu() {
        startActivity(Intent(this, MenuActivity::class.java))
        finish()
    }
}
