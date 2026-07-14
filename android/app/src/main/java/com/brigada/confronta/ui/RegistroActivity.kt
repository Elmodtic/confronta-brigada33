package com.brigada.confronta.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.Grado
import com.brigada.confronta.data.LoginReq
import com.brigada.confronta.data.RegistroReq
import com.brigada.confronta.data.Sesion
import com.brigada.confronta.data.Unidad
import com.brigada.confronta.databinding.ActivityRegistroBinding
import kotlinx.coroutines.launch

class RegistroActivity : AppCompatActivity() {

    private lateinit var b: ActivityRegistroBinding
    private var grados: List<Grado> = emptyList()
    private var unidades: List<Unidad> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Crear cuenta"

        cargarCatalogos()
        b.btnRegistrar.setOnClickListener { registrar() }
    }

    private fun cargarCatalogos() {
        cargando(true)
        lifecycleScope.launch {
            try {
                val rG = ApiClient.api.grados()
                val rU = ApiClient.api.unidades()
                if (rG.isSuccessful && rU.isSuccessful) {
                    grados = rG.body().orEmpty()
                    unidades = rU.body().orEmpty()
                    b.spGrado.adapter = ArrayAdapter(
                        this@RegistroActivity,
                        android.R.layout.simple_spinner_dropdown_item, grados)
                    b.spUnidad.adapter = ArrayAdapter(
                        this@RegistroActivity,
                        android.R.layout.simple_spinner_dropdown_item, unidades)
                } else {
                    toast("No se pudieron cargar grados/unidades")
                }
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                cargando(false)
            }
        }
    }

    private fun registrar() {
        val nombres = b.etNombres.text?.toString()?.trim().orEmpty()
        val apellidos = b.etApellidos.text?.toString()?.trim().orEmpty()
        val cedula = b.etCedula.text?.toString()?.trim().orEmpty()
        val pass = b.etPassword.text?.toString().orEmpty()
        val respuesta = b.etRespuesta.text?.toString()?.trim().orEmpty()
        val pregunta = b.spPregunta.selectedItem?.toString().orEmpty()

        if (nombres.isEmpty() || apellidos.isEmpty() || cedula.isEmpty() || pass.isEmpty()) {
            toast("Completa nombres, apellidos, cédula y contraseña")
            return
        }
        if (grados.isEmpty() || unidades.isEmpty()) {
            toast("Aún no cargan grados/unidades, espera un momento")
            return
        }
        if (respuesta.isEmpty()) {
            toast("Escribe la respuesta de seguridad")
            return
        }
        val idGrado = grados[b.spGrado.selectedItemPosition].id_grado
        val idUnidad = unidades[b.spUnidad.selectedItemPosition].id_unidad

        cargando(true)
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.registro(
                    RegistroReq(pass, pregunta, respuesta, cedula,
                        nombres, apellidos, idGrado, idUnidad))
                if (resp.isSuccessful) {
                    toast("Cuenta creada. Iniciando sesión...")
                    autoLogin(cedula, pass)
                } else {
                    toast(errorDeApi(resp))
                    cargando(false)
                }
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
                cargando(false)
            }
        }
    }

    private fun autoLogin(usuario: String, pass: String) {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.login(LoginReq(usuario, pass))
                if (resp.isSuccessful && resp.body() != null) {
                    Sesion.guardar(resp.body()!!)
                    startActivity(Intent(this@RegistroActivity, MenuActivity::class.java))
                    finishAffinity()
                } else {
                    toast("Cuenta creada. Ahora inicia sesión.")
                    finish()
                }
            } catch (e: Exception) {
                toast("Cuenta creada. Ahora inicia sesión.")
                finish()
            }
        }
    }

    private fun cargando(activo: Boolean) {
        b.progreso.visibility = if (activo) View.VISIBLE else View.GONE
        b.btnRegistrar.isEnabled = !activo
    }
}
