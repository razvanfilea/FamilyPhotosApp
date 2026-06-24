package net.theluckycoder.familyphotos.core.data.remote

import net.theluckycoder.familyphotos.core.data.model.network.User
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

internal interface UserService {

    @FormUrlEncoded
    @POST("/login")
    suspend fun login(
        @Field("user_id") userId: String,
        @Field("password") password: String,
    ): Response<User>

    @POST("logout")
    suspend fun logout()
}