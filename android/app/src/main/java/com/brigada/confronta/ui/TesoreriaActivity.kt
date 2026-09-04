package com.brigada.confronta.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.EntregaItem
import com.brigada.confronta.data.MovimientoCaja
import com.brigada.confronta.data.RancheroFondo
import com.brigada.confronta.data.TesoreriaResumen
import com.brigada.confronta.data.TransferenciaReq
import com.brigada.confronta.databinding.ActivityTesoreriaBinding
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Tesorería: contabilidad de la recaudación y entrega del fondo rotativo
 * al rancho.
 *
 *   Caja disponible = recaudado − (entregas confirmadas + en tránsito)
 *
 * Una entrega no se da por recibida sola: al registrarla se genera un QR
 * que el ranchero debe escanear. Hasta entonces queda EN TRÁNSITO: el
 * dinero ya salió de la caja pero todavía no acredita su fondo.
 */
class TesoreriaActivity : AppCompatActivity() {

    private lateinit var b: ActivityTesoreriaBinding
    private val fecha = Calendar.getInstance()
    private var rancheros: List<RancheroFondo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTesoreriaBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Tesorería"

        b.btnFecha.setOnClickListener { elegirFecha() }
        b.btnEntregar.setOnClickListener { confirmarEntrega() }
        b.btnExcel.setOnClickListener { descargarRecargas() }
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
                val resp = ApiClient.api.tesoreriaResumen(fecha = fechaIso())
                if (resp.isSuccessful && resp.body() != null) pintar(resp.body()!!)
                else toast(errorDeApi(resp))

                val ent = ApiClient.api.entregas(
                    anio = fecha.get(Calendar.YEAR), mes = fecha.get(Calendar.MONTH) + 1)
                b.contenedorEntregas.removeAllViews()
                if (ent.isSuccessful && ent.body() != null) {
                    val e = ent.body()!!
                    b.tvTituloEntregas.text =
                        "Entregas de ${e.mes_nombre} ${e.anio} · confirmado ${money(e.confirmado)} · " +
                        "en tránsito ${money(e.pendiente)}"
                    if (e.entregas.isEmpty()) {
                        b.contenedorEntregas.addView(textoVacio("Sin entregas registradas este mes."))
                    } else {
                        for (x in e.entregas) filaEntrega(x)
                    }
                }
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
            }
        }
    }

    private fun pintar(t: TesoreriaResumen) {
        b.tvCaja.text = money(t.acumulado.caja)
        // La caja es el dinero real; lo pendiente todavía no la descuenta,
        // pero sí reduce lo que aún puede comprometer en una entrega nueva.
        b.tvAcumulado.text = if (t.acumulado.pendientes > 0)
            "Recaudado ${money(t.acumulado.recaudado)} · entregado ${money(t.acumulado.transferido)}\n" +
            "Sin confirmar: ${money(t.acumulado.en_transito)} (${t.acumulado.pendientes}) · " +
            "puedes entregar ${money(t.acumulado.disponible)}"
        else
            "Recaudado ${money(t.acumulado.recaudado)} · entregado ${money(t.acumulado.transferido)}"

        b.tvTituloDia.text = "Movimiento del día ${t.fecha}"
        b.contenedorDia.removeAllViews()
        movimiento(b.contenedorDia, t.dia)

        b.tvTituloMes.text = "Consolidado de ${t.mes_nombre} ${t.anio}"
        b.contenedorMes.removeAllViews()
        movimiento(b.contenedorMes, t.mes_resumen)

        rancheros = t.rancheros
        b.contenedorRancheros.removeAllViews()
        if (rancheros.isEmpty()) {
            b.contenedorRancheros.addView(
                textoVacio("No hay rancheros activos. Pide al administrador que asigne el rol."))
        } else {
            for (r in rancheros) {
                // Se muestra cuánto recibió, cuánto lleva gastado en víveres
                // y qué le queda; el saldo grande es el fondo disponible.
                item(b.contenedorRancheros,
                    "${r.persona}\nRecibió ${money(r.recibido)} · gastó ${money(r.gastado)}" +
                    if (r.en_transito > 0) "\nSin confirmar: ${money(r.en_transito)}" else "",
                    money(r.fondo_rancho))
            }
        }
        b.spRanchero.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            rancheros.map { it.persona })
        b.btnEntregar.isEnabled = rancheros.isNotEmpty()
    }

    private fun movimiento(destino: LinearLayout, m: MovimientoCaja) {
        fila(destino, "Recaudado (${m.recargas} recargas)", money(m.recaudado))
        fila(destino, "Entregado al rancho (${m.entregas})", money(m.transferido))
        fila(destino, "Neto", money(m.neto))
    }

    /** Fila del historial: muestra estado, fecha/hora y acciones si está pendiente. */
    private fun filaEntrega(x: EntregaItem) {
        val etiqueta = when (x.estado) {
            "CONFIRMADA" -> "Confirmada ${fechaHora(x.confirmado_en)}"
            "ANULADA" -> "Anulada ${fechaHora(x.anulado_en)}"
            else -> "Pendiente de confirmar"
        }
        val texto = "${x.ranchero ?: "-"}\n${x.concepto ?: "Sin concepto"}\n" +
                "Entregada ${fechaHora(x.fecha_hora)}\n$etiqueta"

        val f = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 22, 16, 22)
            gravity = Gravity.CENTER_VERTICAL
            if (x.estado == "PENDIENTE") {
                isClickable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { accionesPendiente(x) }
            }
        }
        f.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = texto
            textSize = 14f
        })
        f.addView(TextView(this).apply {
            text = money(x.monto)
            textSize = 15f
            gravity = Gravity.END
            setTextColor(getColor(
                if (x.estado == "ANULADA") com.brigada.confronta.R.color.texto_secundario
                else com.brigada.confronta.R.color.verde_militar))
        })
        b.contenedorEntregas.addView(f)
        b.contenedorEntregas.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(0xFFE0E0E0.toInt())
        })
    }

    private fun accionesPendiente(x: EntregaItem) {
        AlertDialog.Builder(this)
            .setTitle("Entrega de ${money(x.monto)}")
            .setMessage("${x.ranchero}\nRegistrada ${fechaHora(x.fecha_hora)}\n\n" +
                    "Todavía no la confirma el ranchero.")
            .setPositiveButton("Ver QR") { _, _ ->
                if (x.token.isNullOrBlank()) toast("Esta entrega no tiene código disponible")
                else mostrarQr(x.token, x.monto, x.ranchero ?: "")
            }
            .setNegativeButton("Anular") { _, _ -> anular(x) }
            .setNeutralButton("Cerrar", null)
            .show()
    }

    private fun anular(x: EntregaItem) {
        AlertDialog.Builder(this)
            .setTitle("Anular entrega")
            .setMessage("Se anulará la entrega de ${money(x.monto)} y el dinero volverá a tu caja. ¿Continuar?")
            .setPositiveButton("Anular") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val r = ApiClient.api.anularEntrega(x.id_transferencia)
                        if (r.isSuccessful) { toast("Entrega anulada"); cargar() }
                        else toast(errorDeApi(r))
                    } catch (e: Exception) {
                        toast("No se pudo conectar con el servidor.\n${e.message}")
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Muestra el QR que el ranchero debe escanear para confirmar. */
    private fun mostrarQr(token: String, monto: Double, ranchero: String) {
        val cont = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 20)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        cont.addView(TextView(this).apply {
            text = "Que el ranchero escanee este código desde\n\"Fondo del rancho\" para confirmar la recepción."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        })
        cont.addView(ImageView(this).apply {
            setImageBitmap(generarQrBitmap(token))
            layoutParams = LinearLayout.LayoutParams(700, 700)
        })
        cont.addView(TextView(this).apply {
            text = "Código: $token"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
            setTextColor(getColor(com.brigada.confronta.R.color.texto_secundario))
        })

        AlertDialog.Builder(this)
            .setTitle("${money(monto)} para $ranchero")
            .setView(cont)
            .setPositiveButton("Listo") { _, _ -> cargar() }
            .show()
    }

    private fun confirmarEntrega() {
        if (rancheros.isEmpty()) { toast("No hay rancheros activos"); return }
        val pos = b.spRanchero.selectedItemPosition
        if (pos !in rancheros.indices) { toast("Elige un ranchero"); return }
        val destino = rancheros[pos]

        val monto = b.etMonto.text?.toString()?.trim()?.toDoubleOrNull()
        if (monto == null || monto <= 0) {
            b.etMonto.error = "Monto inválido"
            b.etMonto.requestFocus()
            toast("Escribe un monto mayor a cero"); return
        }
        val concepto = b.etConcepto.text?.toString()?.trim().orEmpty()

        AlertDialog.Builder(this)
            .setTitle("Confirmar entrega")
            .setMessage("Entregar ${money(monto)} a ${destino.persona}?\n\n" +
                    "Se generará un QR que el ranchero debe escanear para confirmar que lo recibió.")
            .setPositiveButton("Generar QR") { _, _ -> entregar(destino, monto, concepto) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun entregar(destino: RancheroFondo, monto: Double, concepto: String) {
        b.progreso.visibility = View.VISIBLE
        b.btnEntregar.isEnabled = false
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.transferirFondos(
                    TransferenciaReq(destino.id_usuario, monto, concepto.ifBlank { null }))
                if (resp.isSuccessful && resp.body() != null) {
                    val r = resp.body()!!
                    b.etMonto.text = null
                    b.etConcepto.text = null
                    if (!r.token.isNullOrBlank()) mostrarQr(r.token, r.monto, destino.persona)
                    else { toast("Entrega registrada"); cargar() }
                } else toast(errorDeApi(resp))
            } catch (e: Exception) {
                toast("No se pudo conectar con el servidor.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
                b.btnEntregar.isEnabled = true
            }
        }
    }

    private fun descargarRecargas() {
        b.progreso.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.reporteRecargas()
                if (resp.isSuccessful && resp.body() != null)
                    guardarYAbrirXlsx(resp.body()!!, "recargas.xlsx")
                else toast(errorDeApi(resp))
            } catch (e: Exception) {
                toast("No se pudo descargar.\n${e.message}")
            } finally {
                b.progreso.visibility = View.GONE
            }
        }
    }

    // ---- Utilidades de pintado ----

    private fun textoVacio(msg: String) = TextView(this).apply {
        text = msg
        textSize = 14f
        setPadding(16, 24, 16, 24)
        setTextColor(getColor(com.brigada.confronta.R.color.texto_secundario))
    }

    private fun fila(destino: LinearLayout, etiqueta: String, valor: String) =
        item(destino, etiqueta, valor)

    private fun item(destino: LinearLayout, etiqueta: String, valor: String) {
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
}
