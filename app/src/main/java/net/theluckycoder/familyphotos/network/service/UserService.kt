package net.theluckycoder.familyphotos.network.service

import net.theluckycoder.familyphotos.model.User
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface UserService {

    @GET("/user/name/{userName}")
    suspend fun getUser(
        @Header("Authorization") auth: String,
        @Path("userName") userName: String,
    ): Response<User>
}