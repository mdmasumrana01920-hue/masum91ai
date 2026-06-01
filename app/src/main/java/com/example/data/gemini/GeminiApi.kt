package com.example.data.gemini

import com.example.BuildConfig // .env থেকে এপিআই কি রিড করার জন্য এটি প্রয়োজন
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
    val model: String = "llama-3.3-70b-versatile", // বা তুমি যে মডেল ব্যবহার করছ
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
    // বেস ইউআরএল জেমিনি থেকে পরিবর্তন করে Groq এর আসল ইউআরএল দেওয়া হলো
    private const val BASE_URL = "https://api.groq.com/openai/"

    // কোডের ভেতর কোনো কী থাকবে না, অল-অটোমেটিক .env ফাইল থেকে রিড করবে
    private val GROQ_KEY = BuildConfig.GROQ_API_KEY

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // এই ইন্টারসেপ্টরটি প্রতিবার রিকোয়েস্ট পাঠানোর সময় হেডার হিসেবে Groq API Key যুক্ত করবে
    private val apiKeyInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        
        // এপিআই কী লোড হতে কোনো সমস্যা হয়েছে কি না চেক করার জন্য
        if (GROQ_KEY.isBlank() || GROQ_KEY == "YOUR_API_KEY") {
            throw IllegalStateException("Error: Groq API Key রিড করা যায়নি! দয়া করে .env ফাইলটি চেক করুন।")
        }

        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $GROQ_KEY") // হেডার হিসেবে সেফলি বসে যাবে
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
