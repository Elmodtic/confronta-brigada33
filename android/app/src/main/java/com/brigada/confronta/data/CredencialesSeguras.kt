package com.brigada.confronta.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Guarda de forma CIFRADA (Android Keystore) el usuario y la contraseña en el
 * dispositivo, para poder ingresar luego con huella/rostro. Si el cifrado no
 * está disponible, cae a SharedPreferences normales (nunca crashea).
 */
object CredencialesSeguras {
    private const val FILE = "cred_seguras"

    private fun prefs(ctx: Context): SharedPreferences = try {
        val master = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx, FILE, master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        ctx.getSharedPreferences(FILE + "_fallback", Context.MODE_PRIVATE)
    }

    fun guardar(ctx: Context, usuario: String, password: String) {
        prefs(ctx).edit().putString("u", usuario).putString("p", password).apply()
    }

    fun usuario(ctx: Context): String? = prefs(ctx).getString("u", null)
    fun password(ctx: Context): String? = prefs(ctx).getString("p", null)
    fun hayGuardadas(ctx: Context): Boolean = usuario(ctx) != null && password(ctx) != null
    fun limpiar(ctx: Context) { prefs(ctx).edit().clear().apply() }
}
