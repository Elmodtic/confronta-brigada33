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
import com.brigada.confronta.data.LoginResp
import com.brigada.confronta.data.LoginTotpReq
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

        // La dirección del túnel cambia sola cada vez que se reinicia. En vez
        // de avisarle a cada usuario, la app la consulta en el directorio
        // público al abrir. Si falla, se sigue con la que ya tenía.
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { ApiClient.sincronizarDireccion() }
        }

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
                // Si no hay conexión lo más probable es que el túnel haya
                // cambiado de dirección. Se consulta el directorio y solo se
                // reintenta si efectivamente trajo una nueva; si el segundo
                // intento también falla, el problema es otro y hay que
                // mostrarlo en vez de seguir insistiendo.
                var resp = try {
                    ApiClient.api.login(LoginReq(usuario, pass))
                } catch (e: Exception) {
                    if (!withContext(Dispatchers.IO) { ApiClient.sincronizarDireccion() }) throw e
                    toast("El servidor cambió de dirección. Reintentando...")
                    ApiClient.api.login(LoginReq(usuario, pass))
                }

                if (resp.isSuccessful && resp.body() != null) {
                    val r = resp.body()!!
                    // Con verificación en dos pasos la contraseña sola no
                    // abre sesión: el servidor devuelve un token parcial y
                    // hay que canjearlo por el código de la app.
                    if (r.requiere_totp && !r.token_parcial.isNullOrEmpty()) {
                        pedirCodigo(r.token_parcial, usuario, pass, guardar)
                        return@launch
                    }
                    completarIngreso(r, usuario, pass, guardar)
                } else {
                    toast(errorDeApi(resp))
                }
            } catch (e: Exception) {
                // Se revela el ajuste manual justo cuando hace falta, para
                // poder corregirlo sin reinstalar la app.
                mostrarServidorActual()
                b.btnServidor.visibility = View.VISIBLE
                toast("No se pudo conectar con el servidor.\n" +
                      "Si te pasaron una dirección nueva, tócala abajo para cambiarla.")
            } finally {
                cargando(false)
            }
        }
    }

    /** Guarda la sesión y entra. Solo se llama con el login ya completo. */
    private suspend fun completarIngreso(
        r: LoginResp,
        usuario: String,
        pass: String,
        guardar: Boolean,
    ) {
        Sesion.guardar(r)
        // Las credenciales para la huella se guardan recién aquí: no tiene
        // sentido recordar una contraseña con la que todavía no se entró.
        if (guardar) withContext(Dispatchers.IO) {
            CredencialesSeguras.guardar(this@LoginActivity, usuario, pass)
        }
        toast("Bienvenido, ${Sesion.nombre ?: usuario}")
        // El servidor exige el segundo factor a todos menos al ADMIN. Sin
        // inscribirse, la sesión no sirve para nada más, así que se lleva
        // directo a la inscripción en vez de a un menú que fallaría entero.
        if (r.totp_obligatorio) {
            startActivity(Intent(this, SeguridadActivity::class.java)
                .putExtra(SeguridadActivity.EXTRA_OBLIGATORIO, true))
            finish()
        } else {
            irAlMenu()
        }
    }

    /**
     * Segundo paso. Acepta el código de seis dígitos de la app de
     * autenticación o uno de respaldo (XXXX-XXXX), por eso el campo no
     * se limita a números.
     */
    private fun pedirCodigo(tokenParcial: String, usuario: String, pass: String, guardar: Boolean) {
        val campo = EditText(this).apply {
            hint = "Código de 6 dígitos"
            setPadding(50, 40, 50, 40)
        }
        AlertDialog.Builder(this)
            .setTitle("Verificación en dos pasos")
            .setMessage(
                "Abre tu app de autenticación y escribe el código de 6 dígitos.\n\n" +
                "Si perdiste el teléfono, puedes usar uno de tus códigos de respaldo.")
            .setView(campo)
            .setCancelable(false)
            .setPositiveButton("Verificar", null)   // se enlaza abajo
            .setNegativeButton("Cancelar", null)
            .create()
            .apply {
                // El listener se pone después de mostrar el diálogo para que
                // un código equivocado no lo cierre: así el usuario reintenta
                // sin volver a escribir la contraseña.
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val codigo = campo.text?.toString()?.trim().orEmpty()
                        if (codigo.isEmpty()) { toast("Escribe el código"); return@setOnClickListener }
                        verificarCodigo(this, tokenParcial, codigo, usuario, pass, guardar)
                    }
                }
                show()
            }
    }

    private fun verificarCodigo(
        dialogo: AlertDialog,
        tokenParcial: String,
        codigo: String,
        usuario: String,
        pass: String,
        guardar: Boolean,
    ) {
        cargando(true)
        lifecycleScope.launch {
            try {
                val r = ApiClient.api.loginTotp(LoginTotpReq(tokenParcial, codigo))
                if (r.isSuccessful && r.body()?.token != null) {
                    dialogo.dismiss()
                    completarIngreso(r.body()!!, usuario, pass, guardar)
                } else {
                    // Un código equivocado deja el diálogo abierto para
                    // reintentar; si el servidor marca `reiniciar` (token
                    // parcial caducado o cuenta bloqueada) ya no sirve de
                    // nada insistir y hay que volver a la contraseña.
                    val err = detalleError(r)
                    toast(err.mensaje)
                    if (err.reiniciar) dialogo.dismiss()
                }
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
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
