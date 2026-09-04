package com.brigada.confronta.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.AuditoriaResp
import com.brigada.confronta.databinding.ActivityAuditoriaBinding
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Bitácora de auditoría con filtros.
 *
 * El periodo se elige con calendario (un día concreto o el mes completo)
 * y se puede buscar por cédula, usuario, nombre, acción o detalle. Todo
 * el filtrado ocurre en el servidor.
 */
class AuditoriaActivity : AppCompatActivity() {

    private lateinit var b: ActivityAuditoriaBinding
    private val fecha = Calendar.getInstance()

    /** Periodo activo: DIA, MES o TODO. */
    private enum class Periodo { DIA, MES, TODO }
    private var periodo = Periodo.TODO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAuditoriaBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Registros (auditoría)"

        b.btnFecha.setOnClickListener { elegirDia() }
        b.btnMes.setOnClickListener { elegirMes() }
        b.btnTodo.setOnClickListener {
            periodo = Periodo.TODO
            actualizarBotones()
            cargar()
        }
        b.btnBuscar.setOnClickListener { cargar() }
        b.etBuscar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { cargar(); true } else false
        }

        actualizarBotones()
        cargar()
    }

    private fun elegirDia() {
        DatePickerDialog(this,
            { _, y, m, d ->
                fecha.set(y, m, d)
                periodo = Periodo.DIA
                actualizarBotones()
                cargar()
            },
            fecha.get(Calendar.YEAR), fecha.get(Calendar.MONTH), fecha.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /** Para el mes se reutiliza el calendario; solo importan año y mes. */
    private fun elegirMes() {
        DatePickerDialog(this,
            { _, y, m, _ ->
                fecha.set(y, m, 1)
                periodo = Periodo.MES
                actualizarBotones()
                cargar()
            },
            fecha.get(Calendar.YEAR), fecha.get(Calendar.MONTH), 1
        ).show()
    }

    private fun actualizarBotones() {
        val dia = String.format(Locale.US, "%02d/%02d/%04d",
            fecha.get(Calendar.DAY_OF_MONTH), fecha.get(Calendar.MONTH) + 1, fecha.get(Calendar.YEAR))
        val mes = String.format(Locale.US, "%02d/%04d",
            fecha.get(Calendar.MONTH) + 1, fecha.get(Calendar.YEAR))
        b.btnFecha.text = if (periodo == Periodo.DIA) "Día: $dia" else "Día"
        b.btnMes.text = if (periodo == Periodo.MES) "Mes: $mes" else "Mes"
        b.btnTodo.text = if (periodo == Periodo.TODO) "Todo ✓" else "Todo"
    }

    private fun cargar() {
        val buscar = b.etBuscar.text?.toString()?.trim().orEmpty()
        b.progreso.visibility = View.VISIBLE
        b.contenedor.removeAllViews()
        lifecycleScope.launch {
            try {
                val resp = when (periodo) {
                    Periodo.DIA -> ApiClient.api.auditoria(
                        fecha = String.format(Locale.US, "%04d-%02d-%02d",
                            fecha.get(Calendar.YEAR), fecha.get(Calendar.MONTH) + 1,
                            fecha.get(Calendar.DAY_OF_MONTH)),
                        buscar = buscar.ifBlank { null })
                    Periodo.MES -> ApiClient.api.auditoria(
                        anio = fecha.get(Calendar.YEAR),
                        mes = fecha.get(Calendar.MONTH) + 1,
                        buscar = buscar.ifBlank { null })
                    Periodo.TODO -> ApiClient.api.auditoria(
                        buscar = buscar.ifBlank { null })
                }
                if (resp.isSuccessful && resp.body() != null) pintar(resp.body()!!)
                else toast(errorDeApi(resp))
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
            }
        }
    }

    private fun pintar(r: AuditoriaResp) {
        val periodoTxt = when {
            r.filtro.fecha != null -> "día ${r.filtro.fecha}"
            r.filtro.anio != null && r.filtro.mes != null -> "mes ${r.filtro.mes}/${r.filtro.anio}"
            else -> "todo el historial"
        }
        val buscaTxt = if (r.filtro.buscar.isNullOrBlank()) "" else " · \"${r.filtro.buscar}\""
        b.tvResumen.text =
            "${r.mostrados} registro(s) en $periodoTxt$buscaTxt · ${r.total_registros} en total"

        if (r.registros.isEmpty()) {
            b.contenedor.addView(TextView(this).apply {
                text = "Sin registros para ese filtro."
                textSize = 14f
                setPadding(16, 40, 16, 40)
                setTextColor(getColor(com.brigada.confronta.R.color.texto_secundario))
            })
            return
        }

        for (a in r.registros) {
            val cont = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 18, 16, 18)
            }
            cont.addView(TextView(this).apply {
                val quien = a.persona ?: a.username ?: "-"
                text = "${a.accion}  ·  $quien"
                textSize = 14f
                setTextColor(getColor(com.brigada.confronta.R.color.verde_militar_oscuro))
            })
            if (!a.cedula.isNullOrBlank() || !a.username.isNullOrBlank()) {
                cont.addView(TextView(this).apply {
                    text = "Cédula/usuario: ${a.cedula ?: a.username}"
                    textSize = 12f
                    setTextColor(getColor(com.brigada.confronta.R.color.texto_secundario))
                })
            }
            if (!a.detalle.isNullOrBlank()) {
                cont.addView(TextView(this).apply {
                    text = a.detalle
                    textSize = 13f
                })
            }
            cont.addView(TextView(this).apply {
                text = fechaHora(a.fecha_hora)
                textSize = 12f
                setTextColor(getColor(com.brigada.confronta.R.color.texto_secundario))
            })
            b.contenedor.addView(cont)
            b.contenedor.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(0xFFE0E0E0.toInt())
            })
        }
    }
}
