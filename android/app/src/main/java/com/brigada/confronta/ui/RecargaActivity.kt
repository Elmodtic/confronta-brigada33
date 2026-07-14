package com.brigada.confronta.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.RecargaReq
import com.brigada.confronta.databinding.ActivityRecargaBinding
import kotlinx.coroutines.launch

class RecargaActivity : AppCompatActivity() {

    private lateinit var b: ActivityRecargaBinding
    private var cedulaActual: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRecargaBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Recargar saldo"

        b.btnBuscar.setOnClickListener { buscar() }
        b.btnRecargar.setOnClickListener { recargar() }
        b.btnExcel.setOnClickListener { exportar() }
    }

    private fun exportar() {
        cargando(true)
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.reporteRecargas()
                if (resp.isSuccessful && resp.body() != null)
                    guardarYAbrirXlsx(resp.body()!!, "recargas.xlsx")
                else toast(errorDeApi(resp))
            } catch (e: Exception) {
                toast("No se pudo descargar.\n${e.message}")
            } finally {
                cargando(false)
            }
        }
    }

    private fun buscar() {
        val cedula = b.etCedula.text?.toString()?.trim().orEmpty()
        if (cedula.isEmpty()) { toast("Escribe la cédula"); return }
        cargando(true)
        b.cardPersona.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.buscarPorCedula(cedula)
                if (resp.isSuccessful && resp.body() != null) {
                    val p = resp.body()!!
                    cedulaActual = p.cedula
                    b.tvPersona.text = "${p.grado ?: ""} ${p.apellidos} ${p.nombres}".trim()
                    b.tvSaldoActual.text = "Cédula ${p.cedula} · Unidad ${p.unidad ?: "-"}\nSaldo actual: ${money(p.saldo)}"
                    b.cardPersona.visibility = View.VISIBLE
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

    private fun recargar() {
        val monto = b.etMonto.text?.toString()?.toDoubleOrNull()
        if (monto == null || monto <= 0) { toast("Ingresa un monto válido"); return }
        if (cedulaActual.isEmpty()) { toast("Primero busca a la persona"); return }
        cargando(true)
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.recargar(RecargaReq(cedulaActual, monto))
                if (resp.isSuccessful && resp.body() != null) {
                    val nuevo = resp.body()!!.saldo
                    toast("Recarga exitosa. Nuevo saldo: ${money(nuevo)}")
                    b.tvSaldoActual.text = "Cédula $cedulaActual\nSaldo actual: ${money(nuevo)}"
                    b.etMonto.text = null
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
        b.btnBuscar.isEnabled = !activo
        b.btnRecargar.isEnabled = !activo
    }
}
