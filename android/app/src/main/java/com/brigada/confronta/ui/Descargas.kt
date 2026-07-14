package com.brigada.confronta.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream

private const val XLSX_MIME =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Guarda el .xlsx recibido y lo abre con la app que el usuario elija. */
suspend fun AppCompatActivity.guardarYAbrirXlsx(body: ResponseBody, nombre: String) {
    val archivo = withContext(Dispatchers.IO) {
        val dir = File(getExternalFilesDir(null), "reportes").apply { mkdirs() }
        val f = File(dir, nombre)
        body.byteStream().use { input -> FileOutputStream(f).use { out -> input.copyTo(out) } }
        f
    }
    toast("Reporte descargado: ${archivo.name}")
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", archivo)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, XLSX_MIME)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(intent)
    } catch (e: Exception) {
        toast("Guardado en:\n${archivo.absolutePath}\n(Instala Excel/Sheets para abrirlo)")
    }
}
