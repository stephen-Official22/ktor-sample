
package com.example.services

import com.example.models.TranslationResult
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class GoogleTranslationProvider {

    private val apiKey: String
        get() = System.getenv("GOOGLE_TRANSLATE_API_KEY")
            ?: throw IllegalStateException(
                "GOOGLE_TRANSLATE_API_KEY is missing"
            )

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String? = null
    ): TranslationResult {

        require(text.isNotBlank()) {
            "Text cannot be empty."
        }

        require(targetLanguage.matches(
            Regex("^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?$")
        )) {
            "Invalid target language."
        }

        val url = URL(
            "https://translation.googleapis.com/language/translate/v2" +
                    "?key=$apiKey"
        )

        val requestBody = buildJsonObject {
            put("q", text)
            put("target", targetLanguage)

            if (!sourceLanguage.isNullOrBlank()) {
                put("source", sourceLanguage)
            }

            put("format", "text")
        }.toString()

        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.outputStream.use { output ->
                output.write(
                    requestBody.toByteArray(StandardCharsets.UTF_8)
                )
            }

            val status = connection.responseCode

            val responseStream =
                if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val responseText = responseStream
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                ?: ""

            if (status !in 200..299) {
                throw IllegalStateException(
                    "Google Translation API returned HTTP $status: " +
                            responseText.take(500)
                )
            }

            val response = json.parseToJsonElement(
                responseText
            ).jsonObject

            val translation = response["data"]
                ?.jsonObject
                ?.get("translations")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?: throw IllegalStateException(
                    "Invalid translation response."
                )

            return TranslationResult(
                translatedText = translation["translatedText"]
                    ?.jsonPrimitive
                    ?.content
                    ?: throw IllegalStateException(
                        "Translation text is missing."
                    ),
                detectedSourceLanguage = translation[
                    "detectedSourceLanguage"
                ]?.jsonPrimitive?.content
            )

        } finally {
            connection.disconnect()
        }
    }
}
