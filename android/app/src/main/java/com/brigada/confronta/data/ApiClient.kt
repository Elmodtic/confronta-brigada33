package com.brigada.confronta.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // URL del backend. Usa la IP LAN de tu PC: funciona tanto en el emulador
    // como en un CELULAR físico conectado a la misma red WiFi.
    //
    // ⚠️ Si tu PC cambia de IP (WiFi/DHCP), actualiza esta línea. Para saber tu
    //    IP actual abre CMD y escribe:  ipconfig   (busca "Dirección IPv4").
    //    Alternativa solo-emulador: "http://10.0.2.2:3000/".
    //
    // Requiere que el puerto 3000 esté abierto en el Firewall de Windows
    // (usa el archivo abrir_firewall_puerto3000.bat en la raíz del proyecto).
    const val BASE_URL = "http://192.168.1.104:3000/"

    val api: Api by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder().apply {
                    Sesion.token?.let { header("Authorization", "Bearer $it") }
                }.build()
                chain.proceed(req)
            }
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api::class.java)
    }
}
