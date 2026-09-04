package com.brigada.confronta.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Guarda de forma CIFRADA (Android Keystore) el usuario y la contraseña en el
 * dispositivo, para poder ingresar luego con huella/rostro. Si el cifrado no
 * está disponible, cae a SharedPreferences normales (nunca crashea).
 *
 * IMPORTANTE: la instancia se crea UNA sola vez y se reutiliza. Crear un
 * EncryptedSharedPreferences implica derivar la clave maestra en el Keystore,
 * que es una operación lenta; hacerlo en cada consulta bloqueaba el hilo
 * principal y provocaba un ANR ("la aplicación no responde") en el arranque.
 */
object CredencialesSeguras {
    private const val FILE = "cred_seguras"

    @Volatile
    private var cache: SharedPreferences? = null

    private fun prefs(ctx: Context): SharedPreferences {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: crear(ctx.applicationContext).also { cache = it }
        }
    }

    private fun crear(ctx: Context): SharedPreferences = try {
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

    /** Lee ambos valores con una sola apertura del almacén cifrado. */
    fun guardadas(ctx: Context): Pair<String, String>? {
        val p = prefs(ctx)
        val u = p.getString("u", null) ?: return null
        val c = p.getString("p", null) ?: return null
        return u to c
    }

    fun hayGuardadas(ctx: Context): Boolean = guardadas(ctx) != null

    fun limpiar(ctx: Context) { prefs(ctx).edit().clear().apply() }
}
