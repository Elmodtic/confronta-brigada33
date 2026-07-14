package com.brigada.confronta.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.MesHistorial
import com.brigada.confronta.databinding.ActivityHistorialBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class HistorialActivity : AppCompatActivity() {

    private lateinit var b: ActivityHistorialBinding
    private var anio = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHistorialBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Historial por meses"

        anio = Calendar.getInstance().get(Calendar.YEAR)

        b.btnAnioAnterior.setOnClickListener { anio--; cargar() }
        b.btnAnioSiguiente.setOnClickListener { anio++; cargar() }

        cargar()
    }

    private fun cargar() {
        b.tvAnio.text = anio.toString()
        b.progreso.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.miHistorial(anio)
                if (resp.isSuccessful && resp.body() != null) {
                    pintar(resp.body()!!.meses)
                    b.tvTotalAnio.text = "Total del año: ${money(resp.body()!!.total_anio)}"
                } else {
                    toast(errorDeApi(resp))
                }
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
            }
        }
    }

    private fun pintar(meses: List<MesHistorial>) {
        b.contenedorMeses.removeAllViews()
        for (m in meses) {
            val hayConsumo = (m.desayunos + m.almuerzos + m.meriendas) > 0

            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 22, 16, 22)
                gravity = Gravity.CENTER_VERTICAL
            }
            val izq = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = if (hayConsumo)
                    "${m.mes_nombre}\nD:${m.desayunos}  A:${m.almuerzos}  M:${m.meriendas}"
                else
                    "${m.mes_nombre}\nSin consumo"
                textSize = 14f
                if (!hayConsumo) setTextColor(getColor(com.brigada.confronta.R.color.gris_texto))
            }
            val der = TextView(this).apply {
                text = money(m.total)
                textSize = 16f
                gravity = Gravity.END
                setTextColor(
                    if (hayConsumo) getColor(com.brigada.confronta.R.color.verde_militar)
                    else getColor(com.brigada.confronta.R.color.gris_texto))
            }
            fila.addView(izq)
            fila.addView(der)
            b.contenedorMeses.addView(fila)

            val linea = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(0xFFE0E0E0.toInt())
            }
            b.contenedorMeses.addView(linea)
        }
    }
}
