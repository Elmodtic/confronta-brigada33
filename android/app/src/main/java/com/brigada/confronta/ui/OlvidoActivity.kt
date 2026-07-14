package com.brigada.confronta.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.ResetReq
import com.brigada.confronta.databinding.ActivityOlvidoBinding
import kotlinx.coroutines.launch

class OlvidoActivity : AppCompatActivity() {

    private lateinit var b: ActivityOlvidoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOlvidoBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Recuperar contraseña"

        b.btnBuscar.setOnClickListener { buscarPregunta() }
        b.btnCambiar.setOnClickListener { cambiar() }
    }

    private fun buscarPregunta() {
        val usuario = b.etUsuario.text?.toString()?.trim().orEmpty()
        if (usuario.isEmpty()) { toast("Escribe tu usuario"); return }
        cargando(true)
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.pregunta(usuario)
                if (resp.isSuccessful && resp.body() != null) {
                    b.tvPregunta.text = resp.body()!!.pregunta_seguridad
                    b.bloqueReset.visibility = View.VISIBLE
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

    private fun cambiar() {
        val usuario = b.etUsuario.text?.toString()?.trim().orEmpty()
        val respuesta = b.etRespuesta.text?.toString()?.trim().orEmpty()
        val nueva = b.etNueva.text?.toString().orEmpty()
        if (respuesta.isEmpty() || nueva.isEmpty()) {
            toast("Completa la respuesta y la nueva contraseña"); return
        }
        cargando(true)
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.reset(ResetReq(usuario, respuesta, nueva))
                if (resp.isSuccessful) {
                    toast("Contraseña actualizada. Ya puedes iniciar sesión.")
                    finish()
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

    private fun cargando(activo: Boolean) {
        b.progreso.visibility = if (activo) View.VISIBLE else View.GONE
        b.btnBuscar.isEnabled = !activo
        b.btnCambiar.isEnabled = !activo
    }
}
