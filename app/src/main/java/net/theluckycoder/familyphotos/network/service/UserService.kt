package net.theluckycoder.familyphotos.network.service

import net.theluckycoder.familyphotos.model.User
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface UserService {

    @FormUrlEncoded
    @POST("/login")
    suspend fun login(
        @Field("userId") userId: String,
        @Field("password") password: String,
    ): Response<User>
}