package com.helltar.twitchviewerbot.utils

import kotlinx.coroutines.CancellationException

inline fun <T> runCatchingPreservingCancellation(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
