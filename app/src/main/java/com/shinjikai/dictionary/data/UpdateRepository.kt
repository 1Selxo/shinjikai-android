package com.shinjikai.dictionary.data

import com.google.gson.Gson
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

class UpdateRepository(
    private val metadataUrl: String,
    private val fallbackUrl: String,
    client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {
    private val httpClient = client

    suspend fun checkForUpdate(currentVersionCode: Long): Result<AppUpdateInfo?> {
        return runCatching {
            val request = Request.Builder()
                .url(metadataUrl)
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }

                val body = response.body?.string()?.takeIf { it.isNotBlank() }
                    ?: throw IOException("Empty update response")
                val metadata = gson.fromJson(body, AppUpdateMetadata::class.java)
                metadata.toUpdateInfoOrNull(currentVersionCode)
            }
        }.mapFailure { throwable ->
            when (throwable) {
                is IOException -> IOException("تعذّر التحقق من التحديثات حالياً.", throwable)
                else -> throwable
            }
        }
    }

    private fun AppUpdateMetadata.toUpdateInfoOrNull(currentVersionCode: Long): AppUpdateInfo? {
        if (latestVersionCode <= currentVersionCode) return null
        return AppUpdateInfo(
            latestVersionCode = latestVersionCode,
            latestVersionName = latestVersionName.ifBlank { latestVersionCode.toString() },
            required = required,
            title = title?.trim().orEmpty().ifBlank { "يتوفر تحديث جديد" },
            notes = notes?.trim().orEmpty(),
            url = url?.trim().orEmpty().ifBlank { fallbackUrl }
        )
    }

    private fun <T> Result<T>.mapFailure(mapper: (Throwable) -> Throwable): Result<T> {
        val failure = exceptionOrNull() ?: return this
        return Result.failure(mapper(failure))
    }
}
