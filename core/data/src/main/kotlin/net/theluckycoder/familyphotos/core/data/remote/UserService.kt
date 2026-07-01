package net.theluckycoder.familyphotos.core.data.remote

import net.theluckycoder.familyphotos.core.data.model.network.UserDto
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

internal interface UserService {

    @FormUrlEncoded
    @POST("/api/login")
    suspend fun login(
        @Field("user_id") userId: String,
        @Field("password") password: String,
    ): Response<UserDto>

    @POST("/api/logout")
    suspend fun logout()

    @GET("/api/members")
    suspend fun getMembersList(): Response<List<UserDto>>
}