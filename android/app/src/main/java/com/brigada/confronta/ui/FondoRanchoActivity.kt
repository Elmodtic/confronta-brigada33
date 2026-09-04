package com.brigada.confronta.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.ConfirmarFondoReq
import com.brigada.confronta.data.FondoRancho
import com.brigada.confronta.data.GastoItem
import com.brigada.confronta.data.GastoReq
import com.brigada.confronta.databinding.ActivityFondoRanchoBinding
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Fondo rotativo del rancho, visto por el RANCHERO.
 *
 * Distingue dos saldos que nunca se mezclan:
 *  - Saldo de comensal: su crédito personal para comer.
 *  - Fondo del rancho: recursos operativos entregados por el TESORERO.
 *
 * El fondo solo se acredita cuando el ranchero escanea el QR de la
 * entrega. Ese escaneo es la prueba de recepción: el tesorero no puede
 * dar por entregado un dinero que el ranchero nunca aceptó.
 */
class FondoRanchoActivity : AppCompatActivity() {

    private lateinit var b: ActivityFondoRanchoBinding
    private val fecha = Calendar.getInstance()

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            b.etCodigo.setText(result.contents)
            confirmar()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityFondoRanchoBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Fondo del rancho"

        b.btnFecha.setOnClickListener { elegirFecha() }
        b.btnEscanear.setOnClickListener { escanear() }
        b.btnConfirmar.setOnClickListener { confirmar() }
        b.btnRegistrarGasto.setOnClickListener { registrarGasto() }
        actualizarBotonFecha()
        cargar()
    }

    private fun escanear() {
        val opciones = ScanOptions()
            .setPrompt("Apunta al QR que muestra el tesorero")
            .setBeepEnabled(true)
            .setOrientationLocked(false)
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        scanLauncher.launch(opciones)
    }

    private fun confirmar() {
        val token = b.etCodigo.text?.toString()?.trim().orEmpty()
        if (token.isEmpty()) { toast("Escanea el QR o escribe el código"); return }

        b.progreso.visibility = View.VISIBLE
        b.btnConfirmar.isEnabled = false
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.confirmarFondo(ConfirmarFondoReq(token))
                if (resp.isSuccessful && resp.body() != null) {
                    val r = resp.body()!!
                    b.etCodigo.text = null
                    AlertDialog.Builder(this@FondoRanchoActivity)
                        .setTitle("Recepción confirmada")
                        .setMessage(
                            "Monto: ${money(r.monto)}\n" +
                            "Entregado por: ${r.tesorero ?: "-"}\n" +
                            "Concepto: ${r.concepto ?: "Sin concepto"}\n" +
                            "Registrada: ${fechaHora(r.entregado_en)}\n\n" +
                            "Fondo del rancho: ${money(r.fondo_rancho)}")
                        .setPositiveButton("Listo") { _, _ -> cargar() }
                        .show()
                } else toast(errorDeApi(resp))
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
                b.btnConfirmar.isEnabled = true
            }
        }
    }

    /**
     * Registra una compra hecha con el fondo. Es lo unico que hace bajar el
     * saldo para cocinar: sin esto el ranchero tendria dinero infinito.
     */
    private fun registrarGasto() {
        val monto = b.etMontoGasto.text?.toString()?.trim()?.toDoubleOrNull()
        if (monto == null || monto <= 0) {
            b.etMontoGasto.error = "Monto invalido"
            b.etMontoGasto.requestFocus()
            toast("Escribe cuanto gastaste"); return
        }
        val categoria = b.spCategoria.selectedItem?.toString() ?: "OTROS"
        val detalle = b.etDetalleGasto.text?.toString()?.trim().orEmpty()

        AlertDialog.Builder(this)
            .setTitle("Confirmar compra")
            .setMessage("¿Registrar ${money(monto)} en $categoria?\n\n" +
                    "Se descontará de tu fondo para cocinar.")
            .setPositiveButton("Registrar") { _, _ ->
                b.progreso.visibility = View.VISIBLE
                b.btnRegistrarGasto.isEnabled = false
                lifecycleScope.launch {
                    try {
                        val r = ApiClient.api.registrarGasto(
                            GastoReq(monto, categoria, detalle.ifBlank { null }))
                        if (r.isSuccessful && r.body() != null) {
                            b.etMontoGasto.text = null
                            b.etDetalleGasto.text = null
                            toast("Compra registrada. Fondo restante: ${money(r.body()!!.fondo_restante)}")
                            cargar()
                        } else toast(errorDeApi(r))
                    } catch (e: Exception) {
                        toast("No se pudo conectar con el servidor.\n${e.message}")
                    } finally {
                        b.progreso.visibility = View.GONE
                        b.btnRegistrarGasto.isEnabled = true
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Permite corregir una compra mal anotada: el dinero vuelve al fondo. */
    private fun anularGasto(g: GastoItem) {
        AlertDialog.Builder(this)
            .setTitle("Anular compra")
            .setMessage("${g.categoria} · ${money(g.monto)}\n${fechaHora(g.fecha_hora)}\n\n" +
                    "El dinero volverá a tu fondo. ¿Continuar?")
            .setPositiveButton("Anular") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val r = ApiClient.api.anularGasto(g.id_gasto)
                        if (r.isSuccessful) { toast("Compra anulada"); cargar() }
                        else toast(errorDeApi(r))
                    } catch (e: Exception) {
                        toast("No se pudo conectar con el servidor.\n${e.message}")
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
                val resp = ApiClient.api.fondoRancho(fecha = fechaIso())
                if (resp.isSuccessful && resp.body() != null) pintar(resp.body()!!)
                else toast(errorDeApi(resp))
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
            }
        }
    }

    private fun pintar(f: FondoRancho) {
        b.tvFondo.text = money(f.fondo_rancho)
        b.tvFondoDetalle.text = "Recibido ${money(f.recibido_total)} · " +
                "gastado ${money(f.gastado_total)} en ${f.compras_total} compra(s)"
        b.tvSaldoComensal.text = money(f.saldo_comensal)

        // Aviso de entregas que el tesorero ya registró pero nadie confirmó.
        if (f.entregas_por_confirmar > 0) {
            b.tvPorConfirmar.visibility = View.VISIBLE
            b.tvPorConfirmar.text =
                "Tienes ${f.entregas_por_confirmar} entrega(s) por confirmar, " +
                "${money(f.por_confirmar)} en total. Escanea el QR del tesorero."
        } else {
            b.tvPorConfirmar.visibility = View.GONE
        }

        b.tvTituloPeriodo.text = "Movimiento · día ${f.fecha} y ${f.mes_nombre} ${f.anio}"
        b.contenedorPeriodo.removeAllViews()
        fila(b.contenedorPeriodo, "Recibido el día (${f.entregas_dia})", money(f.recibido_dia))
        fila(b.contenedorPeriodo, "Gastado el día (${f.compras_dia})", money(f.gastado_dia))
        fila(b.contenedorPeriodo, "Recibido en el mes (${f.entregas_mes})", money(f.recibido_mes))
        fila(b.contenedorPeriodo, "Gastado en el mes (${f.compras_mes})", money(f.gastado_mes))
        fila(b.contenedorPeriodo, "Fondo disponible", money(f.fondo_rancho))
        fila(b.contenedorPeriodo, "Por confirmar", money(f.por_confirmar))

        b.contenedorMovimientos.removeAllViews()
        if (f.movimientos.isEmpty()) {
            b.contenedorMovimientos.addView(TextView(this).apply {
                text = "Todavía no has recibido fondos del tesorero."
                textSize = 14f
                setPadding(16, 24, 16, 24)
                setTextColor(getColor(com.brigada.confronta.R.color.texto_secundario))
            })
        } else {
            for (m in f.movimientos) {
                val estado = if (m.estado == "CONFIRMADA")
                    "Confirmada ${fechaHora(m.confirmado_en)}"
                else
                    "PENDIENTE de confirmar"
                fila(b.contenedorMovimientos,
                    "${m.tesorero ?: "Tesorería"}\n${m.concepto ?: "Sin concepto"}\n" +
                    "Entregada ${fechaHora(m.fecha_hora)}\n$estado",
                    money(m.monto),
                    resaltar = m.estado != "CONFIRMADA")
            }
        }

        // Compras hechas con el fondo. Se pueden tocar para anularlas.
        b.contenedorGastos.removeAllViews()
        if (f.gastos.isEmpty()) {
            b.contenedorGastos.addView(TextView(this).apply {
                text = "Todavía no registras compras."
                textSize = 14f
                setPadding(16, 24, 16, 24)
                setTextColor(getColor(com.brigada.confronta.R.color.texto_secundario))
            })
        } else {
            for (g in f.gastos) {
                val f2 = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(16, 22, 16, 22)
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    setBackgroundResource(android.R.drawable.list_selector_background)
                    setOnClickListener { anularGasto(g) }
                }
                f2.addView(TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = "${g.categoria}\n${g.detalle ?: "Sin detalle"}\n${fechaHora(g.fecha_hora)}"
                    textSize = 14f
                })
                f2.addView(TextView(this).apply {
                    text = "− " + money(g.monto)
                    textSize = 15f
                    gravity = Gravity.END
                    setTextColor(getColor(com.brigada.confronta.R.color.rojo_novedad))
                })
                b.contenedorGastos.addView(f2)
                b.contenedorGastos.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(0xFFE0E0E0.toInt())
                })
            }
        }
    }

    private fun fila(
        destino: LinearLayout,
        etiqueta: String,
        valor: String,
        resaltar: Boolean = false
    ) {
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
            setTextColor(getColor(
                if (resaltar) com.brigada.confronta.R.color.rojo_novedad
                else com.brigada.confronta.R.color.verde_militar))
        })
        destino.addView(f)
        destino.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(0xFFE0E0E0.toInt())
        })
    }
}
