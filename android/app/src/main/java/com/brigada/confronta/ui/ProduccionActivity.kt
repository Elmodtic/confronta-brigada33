package com.brigada.confronta.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.ProdUnidad
import com.brigada.confronta.databinding.ActivityProduccionBinding
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class ProduccionActivity : AppCompatActivity() {

    private lateinit var b: ActivityProduccionBinding
    private val fecha = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityProduccionBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Confronta del día"

        b.btnDiaAnterior.setOnClickListener { fecha.add(Calendar.DAY_OF_MONTH, -1); cargar() }
        b.btnDiaSiguiente.setOnClickListener { fecha.add(Calendar.DAY_OF_MONTH, 1); cargar() }
        b.btnExcel.setOnClickListener { exportar() }
        cargar()
    }

    private fun exportar() {
        b.progreso.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.reporteProduccion(fechaIso())
                if (resp.isSuccessful && resp.body() != null)
                    guardarYAbrirXlsx(resp.body()!!, "confronta_${fechaIso()}.xlsx")
                else toast(errorDeApi(resp))
            } catch (e: Exception) {
                toast("No se pudo descargar.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
            }
        }
    }

    private fun fechaIso() = String.format(Locale.US, "%04d-%02d-%02d",
        fecha.get(Calendar.YEAR), fecha.get(Calendar.MONTH) + 1, fecha.get(Calendar.DAY_OF_MONTH))

    private fun cargar() {
        b.tvFecha.text = fechaIso()
        b.progreso.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.produccion(fechaIso())
                if (resp.isSuccessful && resp.body() != null) {
                    val p = resp.body()!!

                    // Cada sección lee la misma cifra de las tres comidas, así
                    // se comparan en vertical: preparar / pasaron / faltan.
                    b.tvDesayunos.text = p.desayunos.confronta.toString()
                    b.tvAlmuerzos.text = p.almuerzos.confronta.toString()
                    b.tvMeriendas.text = p.meriendas.confronta.toString()

                    b.tvDesayunosPasaron.text = p.desayunos.pasaron.toString()
                    b.tvAlmuerzosPasaron.text = p.almuerzos.pasaron.toString()
                    b.tvMeriendasPasaron.text = p.meriendas.pasaron.toString()

                    b.tvDesayunosFaltan.text = p.desayunos.faltan.toString()
                    b.tvAlmuerzosFaltan.text = p.almuerzos.faltan.toString()
                    b.tvMeriendasFaltan.text = p.meriendas.faltan.toString()

                    b.tvPersonas.text = "Personas en la confronta: ${p.personas}"
                    pintarUnidades(p.por_unidad)
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

    /** Celda numérica de la tabla; su ancho debe coincidir con el encabezado. */
    private fun celda(valor: Int): TextView = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT)
        text = valor.toString()
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(getColor(com.brigada.confronta.R.color.verde_militar))
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

    /**
     * Tabla de unidades: solo la confronta a preparar. El detalle de quién
     * pasó y quién falta ya está arriba, en las dos secciones grandes, y
     * repetirlo aquí volvía la lista ilegible.
     */
    private fun pintarUnidades(unidades: List<ProdUnidad>) {
        b.contenedorUnidades.removeAllViews()
        b.tvVacio.visibility = if (unidades.isEmpty()) View.VISIBLE else View.GONE
        for (u in unidades) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4), dp(12), dp(4), dp(12))
                gravity = Gravity.CENTER_VERTICAL
            }
            val izq = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = u.siglas ?: u.unidad
                textSize = 14f
                setTextColor(getColor(com.brigada.confronta.R.color.texto_principal))
            }
            fila.addView(izq)
            fila.addView(celda(u.desayunos.confronta))
            fila.addView(celda(u.almuerzos.confronta))
            fila.addView(celda(u.meriendas.confronta))
            b.contenedorUnidades.addView(fila)

            val linea = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(0xFFE0E0E0.toInt())
            }
            b.contenedorUnidades.addView(linea)
        }
    }
}
