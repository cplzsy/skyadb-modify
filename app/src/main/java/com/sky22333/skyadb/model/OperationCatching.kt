package com.sky22333.skyadb.model

import kotlinx.coroutines.CancellationException

/** Cancellation is lifecycle control, never an operation failure. */
inline fun <T> operationCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
