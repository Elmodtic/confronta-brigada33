package com.brigada.confronta.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.Toast
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import retrofit2.Response
import java.util.Locale

fun Context.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

/** Formatea un valor como dinero: 3.65 -> "$3.65" */
fun money(valor: Double): String = "$" + String.format(Locale.US, "%.2f", valor)

/** Genera un bitmap con el código QR del texto dado. */
fun generarQrBitmap(texto: String, tamano: Int = 640): Bitmap {
    val matrix = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, tamano, tamano)
    val bmp = Bitmap.createBitmap(tamano, tamano, Bitmap.Config.RGB_565)
    for (x in 0 until tamano) {
        for (y in 0 until tamano) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bmp
}

/** Extrae el mensaje {"error": "..."} del cuerpo de error de la API. */
fun errorDeApi(resp: Response<*>): String {
    return try {
        val raw = resp.errorBody()?.string()
        if (raw.isNullOrBlank()) "Error ${resp.code()}"
        else {
            val map = Gson().fromJson(raw, Map::class.java)
            (map["error"] as? String) ?: "Error ${resp.code()}"
        }
    } catch (e: Exception) {
        "Error ${resp.code()}"
    }
}

// ===================================================================
// VALIDACIONES DE ENTRADA (mismo criterio que aplica el backend)
// ===================================================================

/** Nombres y apellidos: solo letras (con tildes y ñ) y espacios. */
val RE_SOLO_LETRAS = Regex("^[a-zA-ZáéíóúüñÁÉÍÓÚÜÑ]+(?: [a-zA-ZáéíóúüñÁÉÍÓÚÜÑ]+)*$")

/** Cédula ecuatoriana: exactamente 10 dígitos. */
val RE_CEDULA = Regex("""^\d{10}$""")

/**
 * Política de contraseñas idéntica a la del servidor (server.js):
 * 10–72 caracteres, sin espacios, con minúscula, mayúscula, número y símbolo.
 * Devuelve null si es válida, o el mensaje de error.
 */
fun validarPassword(pwd: String): String? {
    if (pwd.length < 10) return "La contraseña debe tener al menos 10 caracteres"
    if (pwd.length > 72) return "La contraseña no puede superar los 72 caracteres"
    if (pwd.any { it.isWhitespace() }) return "La contraseña no puede contener espacios"
    if (!pwd.any { it.isLowerCase() }) return "Falta una letra minúscula"
    if (!pwd.any { it.isUpperCase() }) return "Falta una letra mayúscula"
    if (!pwd.any { it.isDigit() }) return "Falta un número"
    if (!pwd.any { !it.isLetterOrDigit() && !it.isWhitespace() }) return "Falta un carácter especial (ej. * ! # $)"
    return null
}

// ===================================================================
// FECHAS
// ===================================================================

/**
 * Convierte la marca de tiempo que devuelve la API (ISO-8601 en UTC,
 * p. ej. "2026-09-04T18:04:58.000Z") a la hora local del dispositivo,
 * en formato "dd/MM/yyyy HH:mm". Si no se puede interpretar, devuelve
 * el texto original para no perder información.
 */
fun fechaHora(iso: String?): String {
    val t = iso?.trim().orEmpty()
    if (t.isEmpty()) return "-"
    return try {
        val instante = java.time.Instant.parse(t)
        java.time.LocalDateTime.ofInstant(instante, java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    } catch (e: Exception) {
        try {
            java.time.LocalDateTime.parse(t.replace(" ", "T").substringBefore("."))
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        } catch (e2: Exception) {
            t
        }
    }
}
