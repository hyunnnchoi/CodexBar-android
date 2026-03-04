package com.codexbar.android.core.domain.model

sealed class Result<out T, out E> {
    data class Success<T>(val value: T) : Result<T, Nothing>()
    data class Failure<E>(val error: E) : Result<Nothing, E>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun <R> map(transform: (T) -> R): Result<R, E> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> Failure(error)
    }

    fun <R> mapError(transform: (E) -> R): Result<T, R> = when (this) {
        is Success -> Success(value)
        is Failure -> Failure(transform(error))
    }

    fun <R> flatMap(transform: (T) -> Result<R, @UnsafeVariance E>): Result<R, E> = when (this) {
        is Success -> transform(value)
        is Failure -> Failure(error)
    }

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun errorOrNull(): E? = when (this) {
        is Success -> null
        is Failure -> error
    }
}
