package com.brigada.confronta

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.brigada.confronta.data.ApiClient
import com.brigada.confronta.data.Sesion

class ConfrontaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Fuerza el modo claro en toda la app. Los layouts tienen el fondo fijo
        // en un gris claro; si el celular esta en modo oscuro, el sistema
        // aclaraba los textos y quedaban ilegibles sobre ese fondo.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        ApiClient.init(this)   // lee la URL guardada del servidor
        Sesion.init(this)
    }
}
