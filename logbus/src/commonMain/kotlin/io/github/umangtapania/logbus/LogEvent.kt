package io.github.umangtapania.logbus

/**
 * One log, as an object.
 *
 * Everything downstream (interceptors, routes, formatters) works on this single object. Keeping it
 * an object means adding a field later won't break existing routes.
 *
 * [sessionId] and [sequence] together identify a log **globally and in order**. Every route receives
 * the same pair for the same log, so you can line routes up against each other: if the console shows
 * `sequence = 1847` and your backend never received it, that log was lost between them. Within a run,
 * [sequence] increases by one for every event the bus delivers, counting from 1.
 *
 * Only the bus hands out positions. An event you build yourself carries [UNSEQUENCED] and, unless you
 * pass one, [NO_SESSION] — both meaning "nobody assigned this", neither ever produced by a bus.
 *
 * The type parameter [E] says what kind of error information this event carries, and the bus picks it
 * for you based on the route:
 * - [SyncLogRoute] and [AsyncLogRoute] receive `LogEvent<LogError.Live>` — the original [Throwable] is
 *   right there.
 * - [DurableLogRoute] receives `LogEvent<LogError.Snapshot>` — the throwable did not survive being
 *   written to disk, so you get everything that *was* captured about it instead.
 *
 * You never write the type parameter yourself; override `emit` and the right one is already filled in.
 *
 * @param sessionId identifies one run of the app. Restarting the app starts a new session.
 * @param sequence this log's position within [sessionId], counting from 1.
 * @param metadata spare key–value space — mainly for interceptors to attach context (like a user id)
 *   without touching the message. String values only, so the whole event can be stored as-is.
 */
@ExposedCopyVisibility
public data class LogEvent<out E : LogError> internal constructor(
    val sessionId: String,
    val sequence: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val error: E? = null,
    val timeMillis: Long,
    val metadata: Map<String, String> = emptyMap(),
) {
    public companion object {
        /** [sessionId] of an event that no bus produced, so it belongs to no run. */
        public const val NO_SESSION: String = ""

        /**
         * [sequence] of an event that no bus numbered, so it has no position. The bus counts from 1,
         * so this can never collide with a real one.
         */
        public const val UNSEQUENCED: Long = 0L

        /**
         * Build an event the way the store does — with a real position and a real timestamp, because
         * both are recorded facts rather than things to invent.
         *
         * This is what a [DurableLogRoute] receives, so it is also how you drive one directly with no
         * bus:
         *
         * ```kotlin
         * route.log(
         *     listOf(
         *         LogEvent.fromStore(
         *             sessionId = 1L,
         *             sequence = 42L,
         *             level = DefaultLevel.ERROR,
         *             tag = "Checkout",
         *             message = "upload failed",
         *             timeMillis = 1_700_000_000_000L,
         *             error = LogError.Snapshot("IOException", "disk full", trace),
         *         ),
         *     ),
         * )
         * ```
         *
         * A log store implementation uses this too, to turn its rows back into events.
         */
        public fun fromStore(
            sessionId: String,
            sequence: Long,
            level: LogLevel,
            tag: String,
            message: String,
            timeMillis: Long,
            error: LogError.Snapshot? = null,
            metadata: Map<String, String> = emptyMap(),
        ): LogEvent<LogError.Snapshot> =
            LogEvent(sessionId, sequence, level, tag, message, error, timeMillis, metadata)
    }
}

/**
 * Build an event by hand, to drive a route directly with no bus — mainly for testing a route in
 * isolation.
 *
 * ```kotlin
 * route.log(LogEvent(level = DefaultLevel.INFO, tag = "Checkout", message = "hi"))
 * route.log(LogEvent(level = DefaultLevel.ERROR, tag = "Checkout", message = "failed", error = e))
 * ```
 *
 * It takes only what you actually know. There is no `sequence` parameter: a position means something
 * only when a bus handed it out, so an event built here is always [LogEvent.UNSEQUENCED] rather than
 * carrying a number you made up. There is no `timeMillis` either — the log is happening now, so it is
 * stamped now. [sessionId] you may pass: give it `bus.sessionId` to attribute the log to this run, or
 * leave it [LogEvent.NO_SESSION].
 *
 * The result is always a `LogEvent<LogError.Live>`, because an event you build is one you have the
 * exception for. The [LogError.Snapshot] form only ever comes back out of the store.
 *
 * Driving a route this way runs only the **route's** pipeline — `minPriority`, `isLoggable`, the
 * formatter, then `emit`. The bus's level gate, its sequencer and every interceptor are skipped.
 *
 * If a test needs a fixed timestamp (checking a formatter's output, say), build the event and then
 * `copy(timeMillis = ...)`.
 */
public fun LogEvent(
    level: LogLevel,
    tag: String,
    message: String,
    error: Throwable? = null,
    sessionId: String = LogEvent.NO_SESSION,
    metadata: Map<String, String> = emptyMap(),
): LogEvent<LogError.Live> = LogEvent(
    sessionId = sessionId,
    sequence = LogEvent.UNSEQUENCED,
    level = level,
    tag = tag,
    message = message,
    error = error?.let { LogError.Live(it) },
    timeMillis = nowMillis(),
    metadata = metadata,
)

/**
 * What a log knows about an error. Always these three things, whatever route you are writing and
 * wherever the event came from:
 *
 * - [type] — the exception's class name, e.g. `"IOException"`.
 * - [message] — its message, if it had one.
 * - [stackTrace] — the full trace as text.
 *
 * The two kinds differ only in whether the exception object itself is still around. [Live] has it.
 * [Snapshot] doesn't, because an object can't outlive the process that created it — so instead of
 * handing you a rebuilt fake, the type itself tells you what you have.
 */
public sealed interface LogError {
    /** The exception's simple class name, e.g. `"IOException"`. Never null; `"Throwable"` as a last resort. */
    public val type: String

    /** The exception's message, or `null` if it had none. */
    public val message: String?

    /**
     * The full stack trace as text. On a [Live] error this is computed the first time you ask, on
     * whichever thread asks — never on the thread that called `log()`.
     */
    public val stackTrace: String

    /**
     * The exception is still here: this log is on its way to a route in the same process that threw
     * it, so [throwable] is the original object.
     *
     * Reach for [throwable] when you need to hand it to something that only takes a `Throwable` —
     * `FirebaseCrashlytics.recordException(t)`, `Log.e(tag, msg, t)`. For anything you'd print or send
     * as text, [stackTrace] is simpler and works in durable routes too.
     */
    public class Live(
        /** The exception exactly as it was thrown. */
        public val throwable: Throwable,
    ) : LogError {
        override val type: String = throwable::class.simpleName ?: "Throwable"

        override val message: String? = throwable.message

        // Deliberately lazy: stackTraceToString() walks the stack and builds a large string, and the
        // thread that called log() should never pay for that. Whichever worker asks for it does.
        // PUBLICATION rather than the default lock: the same event is handed to several routes on
        // different threads, and a rare double-compute is cheaper than a lock in the logging path.
        override val stackTrace: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
            throwable.stackTraceToString()
        }
    }

    /**
     * A capture of an error, without the error. The [Throwable] is gone — it died with the process
     * that threw it, and no format brings an object back — so this carries everything about it that
     * was written down.
     *
     * There is deliberately no fake throwable here. One would pass `is Throwable`, fail
     * `is IOException`, and print the stack of wherever it was rebuilt, all while looking correct.
     */
    public class Snapshot(
        override val type: String,
        override val message: String?,
        override val stackTrace: String,
    ) : LogError
}
