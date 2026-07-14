package com.brigada.confronta.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.CredencialesSeguras
import com.brigada.confronta.data.LoginReq
import com.brigada.confronta.data.Sesion
import com.brigada.confronta.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

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

        // Si hay credenciales guardadas en este dispositivo, ofrece huella/rostro
        if (CredencialesSeguras.hayGuardadas(this)) {
            b.btnHuella.visibility = View.VISIBLE
            b.etUsuario.setText(CredencialesSeguras.usuario(this))
            b.btnHuella.setOnClickListener { ingresarConBiometria() }
        }
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
                    if (guardar) CredencialesSeguras.guardar(this@LoginActivity, usuario, pass)
                    toast("Bienvenido, ${Sesion.nombre ?: usuario}")
                    irAlMenu()
                } else {
                    toast(errorDeApi(resp))
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
                    val u = CredencialesSeguras.usuario(this@LoginActivity)
                    val p = CredencialesSeguras.password(this@LoginActivity)
                    if (u != null && p != null) loginCon(u, p, guardar = false)
                    else toast("No hay credenciales guardadas")
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
