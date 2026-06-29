package com.example.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini API Request/Response Data Classes ---

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "topP") val topP: Float? = null,
    @Json(name = "topK") val topK: Int? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content?
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

// --- Gemini Client ---

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    /**
     * Summarizes note text using Gemini 3.5 Flash.
     */
    suspend fun summarizeNote(title: String, contentText: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) {
            return@withContext "خطأ: لم يتم تهيئة مفتاح API الخاص بـ Gemini. يرجى تهيئته في لوحة الأسرار."
        }

        val prompt = """
            قم بتلخيص هذه الملاحظة تلخيصاً ذكياً ومرتباً باللغة العربية.
            العنوان: $title
            المحتوى: $contentText
            اجعل التلخيص كقائمة من النقاط القصيرة والمفيدة بدون مقدمات أو خاتمة.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = "أنت مساعد ذكي لتلخيص الملاحظات باللغة العربية ومساعد ماندو")))
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "لم نتمكن من توليد تلخيص لهذه الملاحظة."
        } catch (e: Exception) {
            Log.e(TAG, "Error summarizing note", e)
            "حدث خطأ أثناء الاتصال بالذكاء الاصطناعي: ${e.localizedMessage ?: "خطأ غير معروف"}"
        }
    }

    /**
     * Extracts tag keywords for the note content.
     */
    suspend fun extractKeywords(title: String, contentText: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) {
            return@withContext "أفكار, مراجعة, ملاحظات"
        }

        val prompt = """
            استخرج من 2 إلى 4 كلمات مفتاحية قصيرة تناسب هذه الملاحظة.
            العنوان: $title
            المحتوى: $contentText
            أرجع الكلمات مفصولة بفاصلة فقط وبدون شرح أو أرقام، مثال: "عمل, دراسة, برمجة".
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: "ملاحظة"
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting keywords", e)
            "عام"
        }
    }

    /**
     * Chats with Gemini about the note contents.
     */
    suspend fun chatAboutNote(
        noteTitle: String,
        noteContent: String,
        chatHistory: List<Pair<String, Boolean>>, // Pair<MessageText, IsUser>
        newMessage: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) {
            return@withContext "خطأ: مفتاح Gemini غير متوفر."
        }

        val systemPrompt = """
            أنت مساعد الذكاء الاصطناعي "Mando AI" المدمج في تطبيق الملاحظات "Mando AI Notes".
            تساعد المستخدم في فهم ومناقشة والزيادة على الملاحظة التالية:
            عنوان الملاحظة: $noteTitle
            محتوى الملاحظة: $noteContent
            
            أجب بلغة عربية فصحى، وبأسلوب ذكي وودود ومساعد جداً، واجعل ردودك مختصرة ومريحة للقراءة في شاشات الجوال.
        """.trimIndent()

        // Construct contents showing user & model alternating turns
        val contentsList = mutableListOf<Content>()
        
        // Add historic turns
        chatHistory.forEach { (text, isUser) ->
            val role = if (isUser) "user" else "model"
            // For general API, we can prefix user/model queries or send simple parts
            contentsList.add(Content(parts = listOf(Part(text = text))))
        }
        
        // Add new user query
        contentsList.add(Content(parts = listOf(Part(text = newMessage))))

        val request = GeminiRequest(
            contents = contentsList,
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "عذراً، لم أستطع معالجة هذا الرد حالياً."
        } catch (e: Exception) {
            Log.e(TAG, "Error chatting about note", e)
            "حدث خطأ أثناء الاتصال بالذكاء الاصطناعي: ${e.localizedMessage}"
        }
    }
}
