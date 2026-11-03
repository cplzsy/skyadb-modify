package com.sky22333.skyadb.download

/** GitHub Release 中的单个可下载资产。 */
data class ReleaseAsset(
    val fileName: String,
    val downloadUrl: String,
)

/** 获取最新 Release 资产列表的结果。 */
sealed interface ReleaseFetchResult {
    data class Success(val assets: List<ReleaseAsset>) : ReleaseFetchResult

    data class Failure(
        val message: String,
        val suggestion: String? = null,
    ) : ReleaseFetchResult
}
