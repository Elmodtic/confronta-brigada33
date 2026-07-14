package com.brigada.confronta.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.QrReq
import com.brigada.confronta.databinding.ActivityQrBinding
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class QrActivity : AppCompatActivity() {

    private lateinit var b: ActivityQrBinding
    private val fecha = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQrBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Pasar a comer (QR)"

        actualizarBotonFecha()
        b.btnFecha.setOnClickListener { elegirFecha() }
        b.btnGenerar.setOnClickListener { generar() }
    }

    private fun elegirFecha() {
        DatePickerDialog(this,
            { _, y, m, d -> fecha.set(y, m, d); actualizarBotonFecha() },
            fecha.get(Calendar.YEAR), fecha.get(Calendar.MONTH), fecha.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun actualizarBotonFecha() {
        b.btnFecha.text = "Fecha: " + String.format(Locale.US, "%02d/%02d/%04d",
            fecha.get(Calendar.DAY_OF_MONTH), fecha.get(Calendar.MONTH) + 1, fecha.get(Calendar.YEAR))
    }

    private fun fechaIso() = String.format(Locale.US, "%04d-%02d-%02d",
        fecha.get(Calendar.YEAR), fecha.get(Calendar.MONTH) + 1, fecha.get(Calendar.DAY_OF_MONTH))

    private fun generar() {
        val comida = b.spComida.selectedItem?.toString() ?: "ALMUERZO"
        cargando(true)
        b.cardQr.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.generarQr(QrReq(fechaIso(), comida))
                if (resp.isSuccessful && resp.body() != null) {
                    val q = resp.body()!!
                    b.imgQr.setImageBitmap(generarQrBitmap(q.token))
                    b.tvInfo.text = "${q.comida}  ·  ${money(q.precio)}\n${q.fecha}"
                    b.tvCodigo.text = "Código: ${q.token}"
                    b.cardQr.visibility = View.VISIBLE
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

    private fun cargando(activo: Boolean) {
        b.progreso.visibility = if (activo) View.VISIBLE else View.GONE
        b.btnGenerar.isEnabled = !activo
    }
}
