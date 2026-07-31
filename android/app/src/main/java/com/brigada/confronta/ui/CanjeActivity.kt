package com.brigada.confronta.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.CanjeReq
import com.brigada.confronta.databinding.ActivityCanjeBinding
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class CanjeActivity : AppCompatActivity() {

    private lateinit var b: ActivityCanjeBinding

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            b.etCodigo.setText(result.contents)
            canjear()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCanjeBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Canjear QR (comedor)"

        b.btnEscanear.setOnClickListener { escanear() }
        b.btnCanjear.setOnClickListener { canjear() }
    }

    private fun escanear() {
        val opciones = ScanOptions()
            .setPrompt("Apunta al QR del comensal")
            .setBeepEnabled(true)
            .setOrientationLocked(false)
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        scanLauncher.launch(opciones)
    }

    private fun canjear() {
        val token = b.etCodigo.text?.toString()?.trim().orEmpty()
        if (token.isEmpty()) { toast("Escanea o ingresa el código"); return }
        cargando(true)
        b.cardResultado.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.canjear(CanjeReq(token))
                if (resp.isSuccessful && resp.body() != null) {
                    val r = resp.body()!!
                    b.tvDetalle.text =
                        "Comensal: ${r.persona}\nUnidad: ${r.unidad ?: "-"}\n" +
                        "Comida: ${r.comida}  (${money(r.monto)})\n" +
                        "Estado: PAGADA ✓ — acceso autorizado al rancho"
                    b.cardResultado.visibility = View.VISIBLE
                    b.etCodigo.text = null
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
        b.btnCanjear.isEnabled = !activo
    }
}
