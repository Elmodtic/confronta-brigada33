package com.brigada.confronta.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.Movimiento
import com.brigada.confronta.databinding.ActivityEstadoBinding
import kotlinx.coroutines.launch

class EstadoCuentaActivity : AppCompatActivity() {

    private lateinit var b: ActivityEstadoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityEstadoBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Mi saldo y movimientos"
        b.btnExcel.setOnClickListener { exportar() }
        cargar()
    }

    private fun exportar() {
        b.progreso.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.reporteMiConsumo()
                if (resp.isSuccessful && resp.body() != null)
                    guardarYAbrirXlsx(resp.body()!!, "mi_consumo.xlsx")
                else toast(errorDeApi(resp))
            } catch (e: Exception) {
                toast("No se pudo descargar.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
            }
        }
    }

    private fun cargar() {
        b.progreso.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.miEstado()
                if (resp.isSuccessful && resp.body() != null) {
                    b.tvSaldo.text = money(resp.body()!!.saldo)
                    pintar(resp.body()!!.movimientos)
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

    private fun pintar(movs: List<Movimiento>) {
        b.contenedor.removeAllViews()
        b.tvVacio.visibility = if (movs.isEmpty()) View.VISIBLE else View.GONE
        for (m in movs) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 20, 16, 20)
                gravity = Gravity.CENTER_VERTICAL
            }
            val esRecarga = m.tipo == "RECARGA"
            val desc = if (esRecarga) "Recarga de saldo"
                       else "Consumo${m.comida?.let { " · ${it.lowercase()}" } ?: ""}"
            val izq = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "$desc\n${(m.fecha_hora ?: "").replace("T", " ").take(19)}"
                textSize = 14f
            }
            val der = TextView(this).apply {
                val signo = if (m.monto >= 0) "+" else "-"
                text = "$signo${money(kotlin.math.abs(m.monto))}"
                textSize = 16f
                gravity = Gravity.END
                setTextColor(getColor(
                    if (esRecarga) com.brigada.confronta.R.color.verde_militar
                    else com.brigada.confronta.R.color.rojo_novedad))
            }
            fila.addView(izq)
            fila.addView(der)
            b.contenedor.addView(fila)

            val linea = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(0xFFE0E0E0.toInt())
            }
            b.contenedor.addView(linea)
        }
    }
}
