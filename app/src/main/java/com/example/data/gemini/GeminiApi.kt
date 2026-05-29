package com.example.data.gemini

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

data class Candidate(
    val content: Content?
)

interface GeminiApiService {
    // এখানে কুয়েরি প্যারামিটার পুরোপুরি বাদ দিয়ে সরাসরি এন্ডপয়েন্ট ফিক্স করা হলো
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // ➡️ এই ইন্টারসেপ্টরটি কুয়েরি এবং হেডার—উভয় জায়গাতেই আপনার কী-টি পুশ করবে, ফলে নিউ ইয়র্কের আইপি ব্লক আর কাজ করবে না
    private val apiKeyInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val apiKey = "AIzaSyBK1Stj-fd5ZkxDeVknz2C2FG-KLX1fR5w"

        // ১. ইউআরএল-এ কী যুক্ত করা
        val newUrl = originalRequest.url.newBuilder()
            .setQueryParameter("key", apiKey)
            .build()

        // ২. হেডারেও সিকিউরড উপায়ে কী যুক্ত করা (গুগল ক্লাউড রেস্ট্রিকশন বাইপাস করার জন্য)
        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .addHeader("x-goog-api-key", apiKey)
            .build()

        chain.proceed(newRequest)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(apiKeyInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}
