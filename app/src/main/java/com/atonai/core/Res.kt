package com.atonai.core

/**
 * A tiny result type for operations that fail with a user-facing message but
 * are not exceptions (tool calls, network fetches, MCP probes).
 */
sealed interface Res<out T> {
    data class Ok<T>(val value: T) : Res<T>
    data class Err(val message: String, val cause: Throwable? = null) : Res<Nothing>

    val isOk: Boolean get() = this is Ok
}

inline fun <T, R> Res<T>.map(block: (T) -> R): Res<R> = when (this) {
    is Res.Ok -> Res.Ok(block(value))
    is Res.Err -> this
}

fun <T> Res<T>.getOrNull(): T? = (this as? Res.Ok)?.value

fun <T> Res<T>.getOrElse(fallback: (String) -> T): T = when (this) {
    is Res.Ok -> value
    is Res.Err -> fallback(message)
}

inline fun <T> runRes(block: () -> T): Res<T> =
    try {
        Res.Ok(block())
    } catch (t: Throwable) {
        Res.Err(t.message ?: t.javaClass.simpleName, t)
    }
