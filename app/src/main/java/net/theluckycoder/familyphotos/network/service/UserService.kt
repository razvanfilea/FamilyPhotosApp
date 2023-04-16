package net.theluckycoder.familyphotos.network.service

import net.theluckycoder.familyphotos.model.User
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

interface UserService {

    @GET
    suspend fun getUser(
        @Url url: String,
        @Header("Authorization") auth: String,
    ): Response<User>
}