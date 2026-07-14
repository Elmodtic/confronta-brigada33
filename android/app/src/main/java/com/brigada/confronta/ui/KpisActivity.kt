package com.brigada.confronta.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.Kpis
import com.brigada.confronta.databinding.ActivityKpisBinding
import kotlinx.coroutines.launch

class KpisActivity : AppCompatActivity() {

    private lateinit var b: ActivityKpisBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKpisBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Indicadores (Gobierno de TI)"

        b.btnExcel.setOnClickListener { descargarGeneral() }
        cargar()
    }

    private fun cargar() {
        b.progreso.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.kpis()
                if (resp.isSuccessful && resp.body() != null) pintar(resp.body()!!)
                else toast(errorDeApi(resp))
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
            }
        }
    }

    private fun pintar(k: Kpis) {
        b.tvFecha.text = "Al ${k.fecha}"
        b.contenedor.removeAllViews()
        fila("Personal activo", k.personal_activo.toString())
        fila("Reservas de hoy", k.reservas_hoy.toString())
        fila("Consumos de hoy", k.consumos_hoy.toString())
        fila("Cumplimiento de hoy", "${k.cumplimiento_hoy_pct}%")
        fila("Desperdicio de hoy (raciones)", k.desperdicio_hoy.toString())
        fila("Costo consumido hoy", money(k.costo_consumido_hoy))
        fila("Saldo en circulación", money(k.saldo_en_circulacion))
        fila("Recaudo del mes", money(k.recaudo_mes))
        fila("Consumo del mes", money(k.consumo_mes))
        val roles = k.usuarios_por_rol.joinToString("  ·  ") { "${it.rol}: ${it.total}" }
        fila("Usuarios por rol", roles)
    }

    private fun fila(etiqueta: String, valor: String) {
        val f = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 22, 16, 22)
            gravity = Gravity.CENTER_VERTICAL
        }
        f.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = etiqueta
            textSize = 14f
        })
        f.addView(TextView(this).apply {
            text = valor
            textSize = 15f
            gravity = Gravity.END
            setTextColor(getColor(com.brigada.confronta.R.color.verde_militar))
        })
        b.contenedor.addView(f)
        b.contenedor.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(0xFFE0E0E0.toInt())
        })
    }

    private fun descargarGeneral() {
        b.progreso.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.reporteGeneral()
                if (resp.isSuccessful && resp.body() != null) {
                    guardarYAbrirXlsx(resp.body()!!, "reporte_general.xlsx")
                } else {
                    toast(errorDeApi(resp))
                }
            } catch (e: Exception) {
                toast("No se pudo descargar.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
            }
        }
    }
}
