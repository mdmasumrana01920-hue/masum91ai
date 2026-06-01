package com.example.data.gemini

import com.example.BuildConfig // গিটহাব সিক্রেট থেকে জেনারেট হওয়া BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// Groq এর চ্যাট রিকোয়েস্ট ফরম্যাট
data class GroqChatRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<GroqMessage>
)

data class GroqMessage(
    val role: String,
    val content: String
)

data class GroqChatResponse(
    val choices: List<GroqChoice>
)

data class GroqChoice(
    val message: GroqMessage
)

interface GroqApiService {
    @POST("v1/chat/completions")
    suspend fun getChatCompletion(
        @Body request: GroqChatRequest
    ): GroqChatResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://api.groq.com/openai/"

    // গিটহাব সিক্রেটের ভ্যালুটি বিল্ড হওয়ার সময় অল-অটোমেটিক এখান থেকে রিড হবে
    private val GROQ_KEY = BuildConfig.GROQ_API_KEY

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val apiKeyInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        
        // এখানে চেক করা হচ্ছে কী-টি সঠিকভাবে লোড হয়েছে কি না
        if (GROQ_KEY.isBlank() || GROQ_KEY == "YOUR_API_KEY") {
            throw IllegalStateException("Error: GitHub Secret থেকে Groq API Key লোড করা যায়নি!")
        }

        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $GROQ_KEY")
            .header("Content-Type", "application/json")
            .build()

        chain.proceed(newRequest)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(apiKeyInterceptor)
        .build()

    val service: GroqApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GroqApiService::class.java)
    }
}
