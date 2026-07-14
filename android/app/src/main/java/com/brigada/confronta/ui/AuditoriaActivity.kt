package com.brigada.confronta.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.AuditoriaItem
import com.brigada.confronta.databinding.ActivityAuditoriaBinding
import kotlinx.coroutines.launch

class AuditoriaActivity : AppCompatActivity() {

    private lateinit var b: ActivityAuditoriaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAuditoriaBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Registros (auditoría)"
        cargar()
    }

    private fun cargar() {
        b.progreso.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.auditoria()
                if (resp.isSuccessful && resp.body() != null) pintar(resp.body()!!)
                else toast(errorDeApi(resp))
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
            }
        }
    }

    private fun pintar(items: List<AuditoriaItem>) {
        b.contenedor.removeAllViews()
        for (a in items) {
            val cont = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 18, 16, 18)
            }
            val titulo = TextView(this).apply {
                text = "${a.accion}  ·  ${a.username ?: "-"}"
                textSize = 14f
                setTextColor(getColor(com.brigada.confronta.R.color.verde_militar_oscuro))
            }
            val detalle = TextView(this).apply {
                text = a.detalle ?: ""
                textSize = 13f
            }
            val fecha = TextView(this).apply {
                text = (a.fecha_hora ?: "").replace("T", " ").take(19)
                textSize = 12f
                setTextColor(getColor(com.brigada.confronta.R.color.gris_texto))
            }
            cont.addView(titulo)
            if (!a.detalle.isNullOrBlank()) cont.addView(detalle)
            cont.addView(fecha)
            b.contenedor.addView(cont)

            val linea = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(0xFFE0E0E0.toInt())
            }
            b.contenedor.addView(linea)
        }
    }
}
