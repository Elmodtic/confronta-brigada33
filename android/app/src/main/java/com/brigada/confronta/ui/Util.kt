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
