package com.sky22333.skyadb.download

import java.io.IOException
import java.security.MessageDigest
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/** 飞牛 fnOS 分享页中的单个文件条目（list 接口返回）。 */
data class NasShareFile(
    val fileName: String,
    val fileId: Long,
    val path: String,
    val size: Long,
)

/** 获取 NAS 分享页 APK 列表的结果。 */
sealed interface NasFetchResult {
    data class Success(
        val files: List<NasShareFile>,
        val shareId: String,
        val token: String,
        val cookie: String,
    ) : NasFetchResult

    data class Failure(
        val message: String,
        val suggestion: String? = null,
    ) : NasFetchResult
}

/** 获取 NAS 下载地址的结果。 */
sealed interface NasDownloadResult {
    data class Success(
        val downloadUrl: String,
        val cookie: String,
    ) : NasDownloadResult

    data class Failure(
        val message: String,
        val suggestion: String? = null,
    ) : NasDownloadResult
}

/**
 * 飞牛 fnOS 分享页客户端。
 *
 * 协议要点（已实测）：
 * 1. GET 分享页 HTML 内嵌 `share-data` JSON，取 `data.token`（每次刷新变化）；
 * 2. 下载 GET 必须携带 Cookie `{shareId}={token}`，否则返回 400；
 * 3. 所有 POST 需带 `Auth: token` 与 `authx` 签名（MD5），签名串固定拼接规则。
 */
class FnosShareClient(
    shareUrl: String,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build(),
) {
    private val origin: String
    private val shareId: String

    init {
        val base = shareUrl.trim().trimEnd('/')
        origin = base.substringBefore("/s/").trimEnd('/')
        shareId = base.substringAfterLast("/s/").trim()
        require(origin.isNotBlank() && shareId.isNotBlank()) {
            "Invalid NAS share URL: $shareUrl"
        }
    }

    /** 递归收集分享页内全部 .apk 文件，同时返回 token / cookie 供后续下载。 */
    suspend fun fetchApkFiles(): NasFetchResult =
        withContext(Dispatchers.IO) {
            try {
                val pageResponse =
                    client
                        .newCall(Request.Builder().url("$origin/s/$shareId").get().build())
                        .await()
                if (!pageResponse.isSuccessful) {
                    return@withContext NasFetchResult.Failure(
                        message = "NAS 分享页不可访问（HTTP ${pageResponse.code}）",
                        suggestion = "请检查分享链接是否正确、NAS 是否在线。",
                    )
                }
                val html = pageResponse.body?.string().orEmpty()
                val token = parseShareToken(html)
                if (token.isNullOrBlank()) {
                    return@withContext NasFetchResult.Failure(
                        message = "无法解析 NAS 分享页令牌",
                        suggestion = "分享链接可能已失效，请重新获取。",
                    )
                }
                val cookie = "$shareId=$token"

                val files = mutableListOf<NasShareFile>()
                collectApkFiles(
                    path = "/",
                    fileId = null,
                    depth = 0,
                    token = token,
                    cookie = cookie,
                    out = files,
                )
                if (files.isEmpty()) {
                    return@withContext NasFetchResult.Failure(
                        message = "NAS 分享页中未找到 APK 文件",
                        suggestion = "请确认分享目录中已放入 .apk 文件。",
                    )
                }
                NasFetchResult.Success(
                    files = files.sortedBy { it.fileName },
                    shareId = shareId,
                    token = token,
                    cookie = cookie,
                )
            } catch (error: IOException) {
                NasFetchResult.Failure(
                    message = "连接 NAS 失败",
                    suggestion = "请检查网络与 NAS 地址是否可达。",
                )
            } catch (error: Exception) {
                NasFetchResult.Failure(
                    message = "获取 NAS 文件列表失败",
                    suggestion = error.message ?: "请稍后重试。",
                )
            }
        }

    /** 换取某个文件的实际下载地址（相对路径 → 完整 URL）。 */
    suspend fun fetchDownloadUrl(
        file: NasShareFile,
        token: String,
        cookie: String,
    ): NasDownloadResult =
        withContext(Dispatchers.IO) {
            try {
                val body =
                    JSONObject()
                        .put(
                            "files",
                            org.json.JSONArray().put(
                                JSONObject().put("path", file.path).put("fileId", file.fileId)
                            ),
                        )
                        .put("shareId", shareId)
                        .put("downloadFilename", file.fileName)
                val json =
                    postJson(
                        apiPath = "/s/$shareId/api/v1/share/download",
                        body = body,
                        token = token,
                        cookie = cookie,
                    )
                val path = json?.optJSONObject("data")?.optString("path").orEmpty()
                if (path.isBlank()) {
                    NasDownloadResult.Failure(
                        message = "NAS 返回了空的下载地址",
                        suggestion = "分享可能已失效，请重新获取列表。",
                    )
                } else {
                    NasDownloadResult.Success(
                        downloadUrl = origin + path,
                        cookie = cookie,
                    )
                }
            } catch (error: Exception) {
                NasDownloadResult.Failure(
                    message = "获取 NAS 下载地址失败",
                    suggestion = error.message ?: "请稍后重试。",
                )
            }
        }

    /** 递归遍历分享目录，收集所有 .apk 文件条目。 */
    private fun collectApkFiles(
        path: String,
        fileId: Long?,
        depth: Int,
        token: String,
        cookie: String,
        out: MutableList<NasShareFile>,
    ) {
        if (depth > MaxDirectoryDepth) return
        val body =
            JSONObject().put("shareId", shareId).put("path", path).apply {
                fileId?.let { put("fileId", it) }
            }
        val json =
            postJson(
                apiPath = "/s/$shareId/api/v1/share/list",
                body = body,
                token = token,
                cookie = cookie,
            ) ?: return
        val files = json.optJSONObject("data")?.optJSONArray("files") ?: return
        for (index in 0 until files.length()) {
            val entry = files.optJSONObject(index) ?: continue
            val name = entry.optString("file").trim()
            if (name.isEmpty()) continue
            val entryId = entry.optLong("fileId", -1L)
            if (entry.optInt("isDir", 0) == 1) {
                val childPath = if (path == "/") "/$name" else "$path/$name"
                collectApkFiles(childPath, entryId, depth + 1, token, cookie, out)
            } else if (name.endsWith(".apk", ignoreCase = true)) {
                val filePath =
                    if (entry.optString("path").isNullOrBlank() || entry.optString("path") == "/") {
                        "/$name"
                    } else {
                        entry.optString("path").trimEnd('/') + "/" + name
                    }
                out +=
                    NasShareFile(
                        fileName = name,
                        fileId = entryId,
                        path = filePath,
                        size = entry.optLong("size", 0L),
                    )
            }
        }
    }

    /** POST JSON 并解析响应；code != 0 或网络失败时返回 null。 */
    private fun postJson(
        apiPath: String,
        body: JSONObject,
        token: String,
        cookie: String,
    ): JSONObject? {
        val bodyString = body.toString()
        val authx = buildAuthx(apiPath, bodyString)
        val request =
            Request.Builder()
                .url(origin + apiPath)
                .header("Content-Type", "application/json")
                .header("Auth", token)
                .header("authx", authx)
                .header("Cookie", cookie)
                .post(bodyString.toRequestBody(JsonMediaType))
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val payload = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
            return if (json.optInt("code", -1) == 0) json else null
        }
    }

    /** 从分享页 HTML 中提取 share-data 里的 token。 */
    private fun parseShareToken(html: String): String? {
        val match = ShareDataRegex.find(html) ?: return null
        return runCatching {
            JSONObject(match.groupValues[1]).optJSONObject("data")?.optString("token")
        }.getOrNull()
    }

    /** 构造 authx 签名头（MD5，已实测通过）。 */
    private fun buildAuthx(apiPath: String, bodyString: String): String {
        val nonce = (Random().nextInt(900_000) + 100_000).toString()
        val timestamp = System.currentTimeMillis().toString()
        val payloadHash = md5Hex(bodyString)
        val signInput =
            "${SignSecret}_${apiPath}_${nonce}_${timestamp}_${payloadHash}_$ApiKey"
        val sign = md5Hex(signInput)
        return "nonce=$nonce&timestamp=$timestamp&sign=$sign"
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    private suspend fun Call.await(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response) { _, value, _ -> value.close() }
                    }
                }
            )
        }

    private companion object {
        const val MaxDirectoryDepth = 3
        const val SignSecret = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh"
        const val ApiKey = "814&d6470861a4cfbbb4fe2fd3f\$6581f6"
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
        val ShareDataRegex =
            Regex(
                """<script[^>]*id=["']share-data["'][^>]*>(.*?)</script>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
            )
    }
}
