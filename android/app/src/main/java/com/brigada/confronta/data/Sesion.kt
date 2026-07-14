package com.brigada.confronta.data

import android.content.Context
import android.content.SharedPreferences

/** Guarda la sesión (token JWT y datos del usuario) en SharedPreferences. */
object Sesion {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("confronta_sesion", Context.MODE_PRIVATE)
    }

    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().putString("token", v).apply()

    var rol: String?
        get() = prefs.getString("rol", null)
        set(v) = prefs.edit().putString("rol", v).apply()

    var username: String?
        get() = prefs.getString("username", null)
        set(v) = prefs.edit().putString("username", v).apply()

    var idPersonal: Int
        get() = prefs.getInt("id_personal", -1)
        set(v) = prefs.edit().putInt("id_personal", v).apply()

    /** Nombre para mostrar: "GRADO Apellidos Nombres" o, si no hay ficha, el usuario. */
    var nombre: String?
        get() = prefs.getString("nombre", null)
        set(v) = prefs.edit().putString("nombre", v).apply()

    fun guardar(login: LoginResp) {
        token = login.token
        rol = login.rol
        username = login.username
        idPersonal = login.id_personal ?: -1
        nombre = if (login.apellidos != null)
            listOfNotNull(login.grado, login.apellidos, login.nombres).joinToString(" ")
        else login.username
    }

    fun cerrar() {
        prefs.edit().clear().apply()
    }

    fun estaLogueado() = !token.isNullOrEmpty()

    /** ADMIN u OPERADOR pueden gestionar a otras personas y ver reportes. */
    fun esGestor() = rol == "ADMIN" || rol == "OPERADOR"

    /** ¿La cuenta está vinculada a una ficha de personal? */
    fun tieneFicha() = idPersonal > 0
}
