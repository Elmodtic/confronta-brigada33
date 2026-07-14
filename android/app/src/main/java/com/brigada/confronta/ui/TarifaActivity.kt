package com.brigada.confronta.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.Tarifa
import com.brigada.confronta.databinding.ActivityTarifaBinding
import kotlinx.coroutines.launch

class TarifaActivity : AppCompatActivity() {

    private lateinit var b: ActivityTarifaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTarifaBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Editar precios"

        b.btnGuardar.setOnClickListener { guardar() }
        cargar()
    }

    private fun cargar() {
        cargando(true)
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.tarifa()
                if (resp.isSuccessful && resp.body() != null) {
                    val t = resp.body()!!
                    b.etDesayuno.setText(t.desayuno.toString())
                    b.etAlmuerzo.setText(t.almuerzo.toString())
                    b.etMerienda.setText(t.merienda.toString())
                }
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                cargando(false)
            }
        }
    }

    private fun guardar() {
        val d = b.etDesayuno.text?.toString()?.toDoubleOrNull()
        val a = b.etAlmuerzo.text?.toString()?.toDoubleOrNull()
        val m = b.etMerienda.text?.toString()?.toDoubleOrNull()
        if (d == null || a == null || m == null) {
            toast("Ingresa valores numéricos válidos"); return
        }
        cargando(true)
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.actualizarTarifa(Tarifa(d, a, m))
                if (resp.isSuccessful) {
                    toast("Precios actualizados")
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
        b.btnGuardar.isEnabled = !activo
    }
}
