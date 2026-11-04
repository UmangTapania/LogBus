package io.github.umangtapania.logbus

/**
 * Change or drop a log before it reaches any route. Return a modified [LogEvent], or `null` to drop
 * the log entirely.
 *
 * Central interceptors (passed when you build the [LogBus]) run once, in order, before delivery —
 * good for adding context to every log, hiding secrets, or filtering. Route-level interceptors are
 * planned for later (same type, applied per route).
 *
 * An interceptor only ever runs on the live path, before anything is written down, so its events carry
 * a [LogError.Live] and the original [Throwable] is reachable here. The event already has its
 * [LogEvent.sessionId] and [LogEvent.sequence], so an interceptor can read them — and dropping a log
 * leaves a gap in the numbers, which is fine: every route sees the same stream.
 */
public fun interface LogInterceptor {
    public fun intercept(event: LogEvent<LogError.Live>): LogEvent<LogError.Live>?
}
