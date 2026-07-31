package com.brigada.confronta.data

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface Api {

    @POST("api/login")
    suspend fun login(@Body body: LoginReq): Response<LoginResp>

    @POST("api/registro")
    suspend fun registro(@Body body: RegistroReq): Response<RegistroResp>

    @GET("api/olvido/pregunta")
    suspend fun pregunta(@Query("username") username: String): Response<PreguntaResp>

    @POST("api/olvido/reset")
    suspend fun reset(@Body body: ResetReq): Response<OkResp>

    @GET("api/grados")
    suspend fun grados(): Response<List<Grado>>

    @GET("api/unidades")
    suspend fun unidades(): Response<List<Unidad>>

    @GET("api/tarifa")
    suspend fun tarifa(): Response<Tarifa>

    @PUT("api/tarifa")
    suspend fun actualizarTarifa(@Body body: Tarifa): Response<Tarifa>

    @POST("api/confronta")
    suspend fun confronta(@Body body: ConfrontaReq): Response<ConfrontaResp>

    @GET("api/mi/perfil")
    suspend fun miPerfil(): Response<Perfil>

    @GET("api/mi/consumo/{anio}/{mes}")
    suspend fun miConsumo(@Path("anio") anio: Int, @Path("mes") mes: Int): Response<ConsumoMes>

    @GET("api/mi/historial/{anio}")
    suspend fun miHistorial(@Path("anio") anio: Int): Response<HistorialAnio>

    @GET("api/mi/liquidacion/{anio}/{mes}")
    suspend fun miLiquidacion(@Path("anio") anio: Int, @Path("mes") mes: Int): Response<Liquidacion>

    // ---- Administración (root) ----
    @GET("api/usuarios")
    suspend fun usuarios(): Response<List<UsuarioAdmin>>

    @PUT("api/usuarios/{id}")
    suspend fun actualizarUsuario(@Path("id") id: Int, @Body body: UpdateUsuarioReq): Response<OkResp>

    @POST("api/usuarios/{id}/password")
    suspend fun resetPasswordUsuario(@Path("id") id: Int, @Body body: AdminPassReq): Response<OkResp>

    @GET("api/auditoria")
    suspend fun auditoria(): Response<List<AuditoriaItem>>

    // ---- Producción (ranchero) ----
    @GET("api/produccion/{fecha}")
    suspend fun produccion(@Path("fecha") fecha: String): Response<Produccion>

    // ---- KPIs (Gobierno de TI) ----
    @GET("api/kpis")
    suspend fun kpis(): Response<Kpis>

    // ---- Reportes Excel ----
    @Streaming @GET("api/reportes/mi-consumo.xlsx")
    suspend fun reporteMiConsumo(): Response<ResponseBody>

    @Streaming @GET("api/reportes/produccion.xlsx")
    suspend fun reporteProduccion(@Query("fecha") fecha: String): Response<ResponseBody>

    @Streaming @GET("api/reportes/recargas.xlsx")
    suspend fun reporteRecargas(): Response<ResponseBody>

    @Streaming @GET("api/reportes/general.xlsx")
    suspend fun reporteGeneral(): Response<ResponseBody>

    // ---- Saldo / movimientos ----
    @GET("api/mi/estado")
    suspend fun miEstado(): Response<MiEstado>

    // ---- Tesorería ----
    @GET("api/tesoreria/buscar")
    suspend fun buscarPorCedula(@Query("cedula") cedula: String): Response<PersonaSaldo>

    @POST("api/recargas")
    suspend fun recargar(@Body body: RecargaReq): Response<RecargaResp>

    // ---- Reserva ----
    @POST("api/reserva")
    suspend fun reservar(@Body body: ReservaReq): Response<ReservaResp>

    // ---- QR ----
    @POST("api/qr")
    suspend fun generarQr(@Body body: QrReq): Response<QrResp>

    @POST("api/canjear")
    suspend fun canjear(@Body body: CanjeReq): Response<CanjeResp>
}
