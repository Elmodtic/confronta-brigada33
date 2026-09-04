package com.brigada.confronta.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.BloqueKpi
import com.brigada.confronta.data.KpisPeriodo
import com.brigada.confronta.databinding.ActivityKpisBinding
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Indicadores de gestión (Gobierno de TI).
 *
 * Presenta dos cortes sobre la misma consulta:
 *  - Corte diario de la fecha seleccionada.
 *  - Consolidado del mes al que pertenece esa fecha.
 */
class KpisActivity : AppCompatActivity() {

    private lateinit var b: ActivityKpisBinding
    private val fecha = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKpisBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Indicadores (Gobierno de TI)"

        b.btnFecha.setOnClickListener { elegirFecha() }
        b.btnExcel.setOnClickListener { descargarGeneral() }
        actualizarBotonFecha()
        cargar()
    }

    private fun elegirFecha() {
        DatePickerDialog(this,
            { _, y, m, d ->
                fecha.set(y, m, d)
                actualizarBotonFecha()
                cargar()
            },
            fecha.get(Calendar.YEAR), fecha.get(Calendar.MONTH), fecha.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun actualizarBotonFecha() {
        b.btnFecha.text = "Fecha: " + String.format(Locale.US, "%02d/%02d/%04d",
            fecha.get(Calendar.DAY_OF_MONTH), fecha.get(Calendar.MONTH) + 1, fecha.get(Calendar.YEAR))
    }

    private fun fechaIso(): String = String.format(Locale.US, "%04d-%02d-%02d",
        fecha.get(Calendar.YEAR), fecha.get(Calendar.MONTH) + 1, fecha.get(Calendar.DAY_OF_MONTH))

    private fun cargar() {
        b.progreso.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.kpis(fecha = fechaIso())
                if (resp.isSuccessful && resp.body() != null) pintar(resp.body()!!)
                else toast(errorDeApi(resp))
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
            }
        }
    }

    private fun pintar(k: KpisPeriodo) {
        b.tvTituloDia.text = "Corte del día ${k.fecha}"
        b.tvTituloMes.text = "Consolidado de ${k.mes_nombre} ${k.anio}"

        b.contenedorDia.removeAllViews()
        bloque(b.contenedorDia, k.dia)

        b.contenedorMes.removeAllViews()
        bloque(b.contenedorMes, k.mes_resumen)

        b.contenedorGeneral.removeAllViews()
        fila(b.contenedorGeneral, "Personal activo", k.personal_activo.toString())
        fila(b.contenedorGeneral, "Saldo en circulación", money(k.saldo_en_circulacion))
        val roles = k.usuarios_por_rol.joinToString("  ·  ") { "${it.rol}: ${it.total}" }
        fila(b.contenedorGeneral, "Usuarios por rol", roles)
    }

    /** Pinta las métricas comunes a un periodo (día o mes). */
    private fun bloque(destino: LinearLayout, x: BloqueKpi) {
        fila(destino, "Raciones reservadas", x.reservas.toString())
        fila(destino, "Raciones consumidas", x.consumos.toString())
        fila(destino, "Cumplimiento", "${x.cumplimiento_pct}%")
        fila(destino, "Desperdicio (raciones)", x.desperdicio.toString())
        fila(destino, "Costo consumido", money(x.costo_consumido))
        fila(destino, "Recaudado (tesorería)", money(x.recaudado))
        fila(destino, "Transferido al rancho", money(x.transferido))
    }

    private fun fila(destino: LinearLayout, etiqueta: String, valor: String) {
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
        destino.addView(f)
        destino.addView(View(this).apply {
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
