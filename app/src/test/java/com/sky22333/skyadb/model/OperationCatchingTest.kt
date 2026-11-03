package com.sky22333.skyadb.model

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class OperationCatchingTest {
    @Test(expected = CancellationException::class)
    fun cancellationMustEscapeFailureMapping() {
        operationCatching { throw CancellationException("Page left") }
    }

    @Test
    fun ioFailuresRemainReviewableResults() {
        val error = java.io.IOException("Disconnected")
        assertSame(error, operationCatching<Unit> { throw error }.exceptionOrNull())
    }
}
