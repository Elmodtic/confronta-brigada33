package com.brigada.confronta

import android.app.Application
import com.brigada.confronta.data.Sesion

class ConfrontaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Sesion.init(this)
    }
}
