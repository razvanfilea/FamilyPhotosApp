package net.theluckycoder.familyphotos.network.service

import net.theluckycoder.familyphotos.model.User
import retrofit2.Response
import retrofit2.http.POST
import retrofit2.http.Query

interface UserService {

    @POST("/login")
    suspend fun login(
//        @Header("Authorization") auth: String,
        @Query("userId") userId: String,
        @Query("password") password: String,
    ): Response<User>
}