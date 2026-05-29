

package com.example.data.gemini

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
    // এখানে মডেলের নাম পরিবর্তন করে একদম সঠিক "gemini-1.5-flash" করে দেওয়া হয়েছে
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
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
        
        val originalService = retrofit.create(GeminiApiService::class.java)
        
        // এখানে একটি ম্যাজিক করা হয়েছে: অন্য ফাইল থেকে যে কী-ই আসুক না কেন, 
        // অ্যাপ ব্যাকগ্রাউন্ডে সবসময় আপনার আসল সচল API Key-টিই ব্যবহার করবে।
        object : GeminiApiService {
            override suspend fun generateContent(
                apiKey: String,
                request: GenerateContentRequest
            ): GenerateContentResponse {
                val realApiKey = "AIzaSyBK1Stj-fd5ZkxDeVknz2C2FG-KLX1fR5w"
                return originalService.generateContent(realApiKey, request)
            }
        }
    }
}
