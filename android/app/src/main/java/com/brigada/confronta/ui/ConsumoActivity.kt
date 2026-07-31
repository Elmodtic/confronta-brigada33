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
import com.brigada.confronta.data.DiaConsumo
import com.brigada.confronta.data.ReservaReq
import com.brigada.confronta.data.Tarifa
import com.brigada.confronta.databinding.ActivityConsumoBinding
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class ConsumoActivity : AppCompatActivity() {

    private lateinit var b: ActivityConsumoBinding
    private var anio = 0
    private var mes = 0                     // 1..12
    private val fechaSel = Calendar.getInstance()  // fecha a registrar
    private var tarifa = Tarifa(1.90, 3.00, 1.75)

    private val NOMBRES_MES = arrayOf("Enero", "Febrero", "Marzo", "Abril", "Mayo",
        "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityConsumoBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Reservar comida"

        val hoy = Calendar.getInstance()
        anio = hoy.get(Calendar.YEAR)
        mes = hoy.get(Calendar.MONTH) + 1

        // Por defecto se reserva para MAÑANA (el cupo cierra a las 17:00 de hoy)
        fechaSel.add(Calendar.DAY_OF_MONTH, 1)
        actualizarBotonFecha()

        b.btnFecha.setOnClickListener { elegirFecha() }
        b.btnGuardar.setOnClickListener { guardar() }
        b.btnMesAnterior.setOnClickListener { cambiarMes(-1) }
        b.btnMesSiguiente.setOnClickListener { cambiarMes(1) }

        cargarTarifa()
        cargarMes()
    }

    private fun cambiarMes(delta: Int) {
        mes += delta
        if (mes < 1) { mes = 12; anio-- }
        if (mes > 12) { mes = 1; anio++ }
        cargarMes()
    }

    private fun elegirFecha() {
        DatePickerDialog(this,
            { _, y, m, d ->
                fechaSel.set(y, m, d)
                actualizarBotonFecha()
            },
            fechaSel.get(Calendar.YEAR),
            fechaSel.get(Calendar.MONTH),
            fechaSel.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun actualizarBotonFecha() {
        val f = String.format(Locale.US, "%02d/%02d/%04d",
            fechaSel.get(Calendar.DAY_OF_MONTH),
            fechaSel.get(Calendar.MONTH) + 1,
            fechaSel.get(Calendar.YEAR))
        b.btnFecha.text = "Fecha: $f"
    }

    private fun fechaSelIso(): String = String.format(Locale.US, "%04d-%02d-%02d",
        fechaSel.get(Calendar.YEAR),
        fechaSel.get(Calendar.MONTH) + 1,
        fechaSel.get(Calendar.DAY_OF_MONTH))

    private fun cargarTarifa() {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.tarifa()
                if (resp.isSuccessful && resp.body() != null) tarifa = resp.body()!!
            } catch (_: Exception) { }
            b.cbDesayuno.text = "Desayuno (${money(tarifa.desayuno)})"
            b.cbAlmuerzo.text = "Almuerzo (${money(tarifa.almuerzo)})"
            b.cbMerienda.text = "Merienda (${money(tarifa.merienda)})"
        }
    }

    private fun guardar() {
        val fecha = fechaSelIso()
        val estado = b.spEstado.selectedItem?.toString() ?: "PRESENTE"
        val novedad = b.etNovedad.text?.toString()?.trim().orEmpty()

        if (!b.cbDesayuno.isChecked && !b.cbAlmuerzo.isChecked && !b.cbMerienda.isChecked) {
            toast("Marca al menos una comida para reservar"); return
        }
        cargando(true)
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.reservar(
                    ReservaReq(
                        fecha = fecha,
                        estado = estado,
                        desayuno = b.cbDesayuno.isChecked,
                        almuerzo = b.cbAlmuerzo.isChecked,
                        merienda = b.cbMerienda.isChecked,
                        novedad = if (novedad.isEmpty()) null else novedad
                    ))
                if (resp.isSuccessful && resp.body() != null) {
                    val r = resp.body()!!
                    val msg = when {
                        r.cobrado > 0 -> "Reservado y pagado: se descontó ${money(r.cobrado)}. Saldo: ${money(r.saldo)}"
                        r.reembolsado > 0 -> "Reserva actualizada: se devolvió ${money(r.reembolsado)}. Saldo: ${money(r.saldo)}"
                        else -> "Reserva actualizada. Saldo: ${money(r.saldo)}"
                    }
                    toast(msg)
                    anio = fechaSel.get(Calendar.YEAR)
                    mes = fechaSel.get(Calendar.MONTH) + 1
                    cargarMes()
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

    private fun cargarMes() {
        b.tvMes.text = "${NOMBRES_MES[mes - 1]} $anio"
        cargando(true)
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.miConsumo(anio, mes)
                if (resp.isSuccessful && resp.body() != null) {
                    pintarDias(resp.body()!!.dias)
                    b.tvTotal.text = "Valor estimado del mes: ${money(resp.body()!!.totales.total)}"
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

    private fun pintarDias(dias: List<DiaConsumo>) {
        b.contenedorDias.removeAllViews()
        b.tvVacio.visibility = if (dias.isEmpty()) View.VISIBLE else View.GONE

        for (d in dias) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 20, 16, 20)
                gravity = Gravity.CENTER_VERTICAL
            }

            val raciones = buildList {
                if (d.desayuno) add("Des")
                if (d.almuerzo) add("Alm")
                if (d.merienda) add("Mer")
            }.joinToString("+").ifEmpty { "—" }

            val izq = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val dia = d.fecha.substring(8, 10)
                text = "Día $dia · ${d.estado}\n$raciones"
                textSize = 14f
            }
            val der = TextView(this).apply {
                text = money(d.costo)
                textSize = 15f
                setTextColor(getColor(com.brigada.confronta.R.color.verde_militar))
                gravity = Gravity.END
            }
            fila.addView(izq)
            fila.addView(der)
            b.contenedorDias.addView(fila)

            val linea = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(0xFFE0E0E0.toInt())
            }
            b.contenedorDias.addView(linea)
        }
    }

    private fun cargando(activo: Boolean) {
        b.progreso.visibility = if (activo) View.VISIBLE else View.GONE
        b.btnGuardar.isEnabled = !activo
    }
}
