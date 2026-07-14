package com.brigada.confronta.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.Liquidacion
import com.brigada.confronta.databinding.ActivityLiquidacionBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class LiquidacionActivity : AppCompatActivity() {

    private lateinit var b: ActivityLiquidacionBinding
    private var anio = 0
    private var mes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLiquidacionBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Liquidación del mes"

        val hoy = Calendar.getInstance()
        anio = hoy.get(Calendar.YEAR)
        mes = hoy.get(Calendar.MONTH) + 1

        b.btnMesAnterior.setOnClickListener { cambiarMes(-1) }
        b.btnMesSiguiente.setOnClickListener { cambiarMes(1) }

        cargar()
    }

    private fun cambiarMes(delta: Int) {
        mes += delta
        if (mes < 1) { mes = 12; anio-- }
        if (mes > 12) { mes = 1; anio++ }
        cargar()
    }

    private fun cargar() {
        b.progreso.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.miLiquidacion(anio, mes)
                if (resp.isSuccessful && resp.body() != null) {
                    pintar(resp.body()!!)
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

    private fun pintar(l: Liquidacion) {
        b.tvMes.text = "${l.mes_nombre} ${l.anio}"
        val p = l.persona
        b.tvPersona.text = if (p != null)
            "${p.grado ?: ""} ${p.apellidos} ${p.nombres}\nUnidad: ${p.unidad ?: "-"}"
        else "—"

        b.tvDesayuno.text = "Desayunos:  ${l.desayunos} × ${money(l.tarifa.desayuno)}  =  ${money(l.subtotal_desayuno)}"
        b.tvAlmuerzo.text = "Almuerzos:  ${l.almuerzos} × ${money(l.tarifa.almuerzo)}  =  ${money(l.subtotal_almuerzo)}"
        b.tvMerienda.text = "Meriendas:  ${l.meriendas} × ${money(l.tarifa.merienda)}  =  ${money(l.subtotal_merienda)}"
        b.tvTotal.text = "TOTAL A PAGAR: ${money(l.total)}"
    }
}
