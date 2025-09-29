package net.redproxy

import retrofit2.Call
import retrofit2.http.GET

interface SoaxRest {

    data class SoaxData(
        val ip: String,
        val country_code: String
    )

    data class SoaxResponse(
        val data: SoaxData
    )

    @GET("/api/ipinfo")
    fun info(): Call<SoaxResponse>

}