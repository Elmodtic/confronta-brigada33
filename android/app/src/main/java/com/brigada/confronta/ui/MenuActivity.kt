package com.brigada.confronta.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.Sesion
import com.brigada.confronta.databinding.ActivityMenuBinding
import kotlinx.coroutines.launch

class MenuActivity : AppCompatActivity() {

    private lateinit var b: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Confronta B-33"

        b.tvSaludo.text = "Hola, ${Sesion.nombre ?: Sesion.username ?: "usuario"}"
        b.tvRol.text = "Rol: ${Sesion.rol ?: "-"}"

        val tieneFicha = Sesion.tieneFicha()
        val esAdmin = Sesion.rol == "ADMIN"
        val esRanchero = Sesion.rol == "RANCHERO"
        val esTesorero = Sesion.rol == "TESORERO"

        // Funciones de comensal (requieren ficha de personal)
        b.btnReservar.isEnabled = tieneFicha
        b.btnQR.isEnabled = tieneFicha
        b.btnEstado.isEnabled = tieneFicha
        b.tvAvisoFicha.visibility = if (tieneFicha) View.GONE else View.VISIBLE

        // Permisos adicionales según rol
        b.btnRecargar.visibility = if (esTesorero || esAdmin) View.VISIBLE else View.GONE
        b.btnCanje.visibility = if (esRanchero || esAdmin) View.VISIBLE else View.GONE
        b.btnProduccion.visibility = if (esRanchero || esAdmin) View.VISIBLE else View.GONE

        val visAdmin = if (esAdmin) View.VISIBLE else View.GONE
        b.tvAdmin.visibility = visAdmin
        b.btnUsuarios.visibility = visAdmin
        b.btnAuditoria.visibility = visAdmin
        b.btnTarifa.visibility = visAdmin
        b.btnKpis.visibility = visAdmin

        b.btnReservar.setOnClickListener { abrir(ConsumoActivity::class.java) }
        b.btnQR.setOnClickListener { abrir(QrActivity::class.java) }
        b.btnEstado.setOnClickListener { abrir(EstadoCuentaActivity::class.java) }
        b.btnRecargar.setOnClickListener { abrir(RecargaActivity::class.java) }
        b.btnCanje.setOnClickListener { abrir(CanjeActivity::class.java) }
        b.btnProduccion.setOnClickListener { abrir(ProduccionActivity::class.java) }
        b.btnUsuarios.setOnClickListener { abrir(UsuariosActivity::class.java) }
        b.btnAuditoria.setOnClickListener { abrir(AuditoriaActivity::class.java) }
        b.btnTarifa.setOnClickListener { abrir(TarifaActivity::class.java) }
        b.btnKpis.setOnClickListener { abrir(KpisActivity::class.java) }
        b.btnCerrar.setOnClickListener { cerrarSesion() }
    }

    override fun onResume() {
        super.onResume()
        cargarSaldoYTarifa()   // refresca el saldo al volver de otras pantallas
    }

    private fun cargarSaldoYTarifa() {
        lifecycleScope.launch {
            try {
                if (Sesion.tieneFicha()) {
                    val e = ApiClient.api.miEstado()
                    if (e.isSuccessful && e.body() != null)
                        b.tvSaldo.text = money(e.body()!!.saldo)
                }
                val t = ApiClient.api.tarifa()
                if (t.isSuccessful && t.body() != null) {
                    val tf = t.body()!!
                    b.tvTarifa.text =
                        "Desayuno ${money(tf.desayuno)} · Almuerzo ${money(tf.almuerzo)} · Merienda ${money(tf.merienda)}"
                }
            } catch (_: Exception) {
                b.tvTarifa.text = "Sin conexión al servidor"
            }
        }
    }

    private fun abrir(clase: Class<*>) = startActivity(Intent(this, clase))

    private fun cerrarSesion() {
        Sesion.cerrar()
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}
