package com.brigada.confronta.data

import android.content.Context
import android.content.SharedPreferences
import com.brigada.confronta.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP de la API.
 *
 * La dirección del servidor NO está fija en el código: se guarda en el
 * dispositivo y se puede cambiar desde la pantalla de inicio de sesión.
 *
 * El motivo es práctico. El servidor se publica con un túnel de Cloudflare
 * y la dirección del túnel gratuito cambia cada vez que se reinicia. Si la
 * URL estuviera compilada dentro del APK, cualquier reinicio obligaría a
 * generar e instalar un APK nuevo en todos los teléfonos. Así, basta con
 * que cada quien actualice el campo "Servidor".
 */
object ApiClient {

    /** Dirección con la que sale el APK. Sirve mientras el túnel no cambie. */
    const val URL_POR_DEFECTO = "https://demand-repeated-bios-ancient.trycloudflare.com/"

    /**
     * Directorio público con la dirección vigente del servidor.
     *
     * El túnel gratuito cambia de dirección cada vez que se reinicia, y
     * avisarle a mano a cada usuario no escala. La app consulta este
     * archivo al abrir y se reconfigura sola. Es un JSON en el repositorio
     * público del proyecto: no expone nada que no esté ya publicado, y no
     * cuesta ni depende de otro servicio.
     */
    private const val URL_DIRECTORIO =
        "https://raw.githubusercontent.com/Elmodtic/confronta-brigada33/main/servidor.json"

    private const val PREFS = "confronta_config"
    private const val CLAVE_URL = "base_url"

    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /**
     * Busca en el directorio la dirección vigente y la adopta si cambió.
     * Devuelve true solo si hubo cambio.
     *
     * Todo fallo se ignora a propósito: sin internet, con GitHub caído o
     * con el archivo mal formado, la app sigue con la dirección que ya
     * tenía. Esto es una comodidad, nunca un requisito para arrancar.
     */
    fun sincronizarDireccion(): Boolean {
        return try {
            val cliente = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .build()
            val peticion = okhttp3.Request.Builder().url(URL_DIRECTORIO).build()
            cliente.newCall(peticion).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val cuerpo = resp.body?.string() ?: return false
                val publicada = normalizar(
                    com.google.gson.JsonParser.parseString(cuerpo)
                        .asJsonObject["url"].asString)
                if (publicada.isBlank() || publicada == baseUrl) return false
                prefs?.edit()?.putString(CLAVE_URL, publicada)?.apply()
                reiniciar()
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * URL activa del servidor. Al asignarle un valor se descarta el cliente
     * anterior para que la siguiente petición ya use la dirección nueva.
     */
    var baseUrl: String
        get() = prefs?.getString(CLAVE_URL, URL_POR_DEFECTO) ?: URL_POR_DEFECTO
        set(valor) {
            prefs?.edit()?.putString(CLAVE_URL, normalizar(valor))?.apply()
            reiniciar()
        }

    /** Vuelve a la dirección con la que se compiló el APK. */
    fun restablecerUrl() {
        prefs?.edit()?.remove(CLAVE_URL)?.apply()
        reiniciar()
    }

    /**
     * Acepta lo que el usuario escriba y lo deja utilizable por Retrofit:
     * fuerza HTTPS (el tráfico en claro está prohibido) y garantiza la
     * barra final, que Retrofit exige.
     */
    fun normalizar(entrada: String): String {
        var u = entrada.trim()
        if (u.isEmpty()) return URL_POR_DEFECTO
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        u = u.replace("http://", "https://")
        if (!u.endsWith("/")) u = "$u/"
        return u
    }

    @Volatile
    private var instancia: Api? = null

    private fun reiniciar() {
        instancia = null
    }

    val api: Api
        get() = instancia ?: synchronized(this) {
            instancia ?: crear().also { instancia = it }
        }

    private fun crear(): Api {
        // NUNCA registrar cuerpos de peticion en release: contienen la cedula,
        // la contrasena y el token JWT. En debug se limita a metodo/URL/codigo.
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder().apply {
                    Sesion.token?.let { header("Authorization", "Bearer $it") }
                }.build()
                chain.proceed(req)
            }
            .addInterceptor(logging)
            // El túnel puede tardar en despertar la primera conexión.
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api::class.java)
    }
}
