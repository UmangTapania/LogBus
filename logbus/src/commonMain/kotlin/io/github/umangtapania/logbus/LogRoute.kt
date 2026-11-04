package io.github.umangtapania.logbus

/**
 * A destination for logs — console, file, network, anywhere. This is the sealed *family*: you don't
 * extend it directly, you extend one of its three members and override **one** thing, that member's
 * `emit`, which says what to actually do with a finished log.
 *
 * Pick by what your destination does:
 * - [SyncLogRoute] — fast, non-suspending work (console, a crash line). Runs **inline** on the caller's
 *   thread, so the write finishes before the log call returns. No worker.
 * - [AsyncLogRoute] — suspending I/O (file, network). Runs on the bus's background worker, so the
 *   caller never waits and a slow route can't hold up the others. Its mailbox lives in memory, so
 *   anything still queued when the process dies is gone.
 * - [DurableLogRoute] — the same suspending I/O, but fed from the log store instead of a mailbox.
 *   It receives logs in batches, says whether the batch got through, and picks up where it left off
 *   after a restart.
 *
 * Everything before `emit` is shared and lives here: the [minPriority] gate, the [isLoggable] filter
 * and the optional [formatter] step, applied in a fixed order by each member's `log`:
 * [isLoggable] → [format] → `emit`. Because `log` runs the whole pipeline itself, a route works on
 * its own with no bus — just call `route.log(event)`. The bus is only a fan-out over many routes.
 *
 * `sealed` keeps the hierarchy closed, so the bus can tell the three apart exhaustively and users
 * can't add a fourth kind it wouldn't know how to deliver to.
 */
public sealed class LogRoute(
    protected val formatter: LogFormatter? = null,
) {
    /**
     * The lowest level this route accepts, declared up front so the bus can know it without an event.
     *
     * It does three jobs. Every route's own `log` enforces it, so declaring a floor really does filter
     * this route. The bus takes the **minimum across all routes** and drops anything below that
     * **before building the event at all** — no allocation, no `nowMillis()`, no interceptors, no
     * mailbox. A release build spraying VERBOSE with no route that wants it costs one comparison. And
     * for an [AsyncLogRoute] the bus checks it once more before the event takes a slot in that route's
     * mailbox, so a flood of logs only *another* route wants can't push this one's logs out of its
     * queue.
     *
     * Defaults to "no floor", which is always safe. Override it when your route has a fixed threshold:
     *
     * ```kotlin
     * override val minPriority: Int get() = DefaultLevel.ERROR.priority
     * ```
     *
     * Only the bus-wide shortcut depends on other routes: if any route wants VERBOSE, every event is
     * built — but it still won't be queued for a route whose floor it doesn't clear. Filtering by tag or
     * anything else on the event belongs in [isLoggable], which runs on the route's own worker rather
     * than on the calling thread, because it is your code and may be slower than a comparison.
     */
    public open val minPriority: Int get() = Int.MIN_VALUE

    /**
     * A per-route filter: return `false` to skip this event before any formatting or emitting.
     * Defaults to accepting everything; override to filter by level, tag, or anything on the event
     * (e.g. `event.level.priority >= DefaultLevel.WARNING.priority`).
     *
     * This sees the widened `LogEvent<LogError>` so one filter can serve any route kind. The error's
     * [LogError.type], [LogError.message] and [LogError.stackTrace] are all available here; the live
     * [Throwable] is not, because a durable route wouldn't have one.
     */
    public open fun isLoggable(event: LogEvent<LogError>): Boolean = true

    /**
     * The shared format step, run by each member's `log` before `emit`. If a [formatter] is set, the
     * event's `message` is replaced with its output, so `emit` always gets a single, already-formatted
     * [LogEvent] (with `level`, `tag`, `error` still available). No formatter → the event is unchanged.
     */
    protected fun <E : LogError> format(event: LogEvent<E>): LogEvent<E> =
        if (formatter != null) event.copy(message = formatter.format(event)) else event

    /**
     * Called when an event will never reach this destination. Override it to count the loss, report
     * it, or fall back somewhere else — anything beats the silent drop you get by default.
     *
     * ```kotlin
     * override fun onDropped(event: LogEvent<LogError>, cause: Throwable) {
     *     lostLogs += 1
     * }
     * ```
     *
     * For a [SyncLogRoute] or an [AsyncLogRoute] this fires when `emit` throws: that event was handed
     * over once and there is no copy of it anywhere, so it is gone. A [DurableLogRoute] is different —
     * its logs are on disk before it sees them, so a batch whose `emit` fails is **retried, not
     * dropped**, and nothing is reported. It loses an event only when retention deletes a row the
     * route never managed to deliver, which arrives with the log store.
     *
     * **Keep it fast and non-blocking.** For a [SyncLogRoute] this runs inline on the thread that
     * called `log()`. Anything it throws is caught and ignored — a broken handler can't take down the
     * caller or the other routes — so don't rely on an exception escaping.
     */
    public open fun onDropped(event: LogEvent<LogError>, cause: Throwable) {}

    /** The full pre-emit decision: this route's declared floor, then its [isLoggable] filter. */
    protected fun accepts(event: LogEvent<LogError>): Boolean =
        event.level.priority >= minPriority && isLoggable(event)
}

/** Runs [LogRoute.onDropped] without letting a broken handler escape into the logging path. */
internal fun LogRoute.reportDropped(event: LogEvent<LogError>, cause: Throwable) {
    try {
        onDropped(event, cause)
    } catch (_: Throwable) {
        // A handler that throws is the dev's bug, not a reason to break logging further.
    }
}

/**
 * A synchronous destination. [emit] is a plain function that runs **inline on the caller's thread**, so
 * the write completes before the log call returns — exactly what you want for console output and for
 * crash lines that must be written before the process dies.
 *
 * The tradeoff: it runs on the caller's thread, with no mailbox, no per-route ordering buffer, and no
 * isolation. Keep [emit] **fast and non-blocking** — for slow or suspending I/O, use [AsyncLogRoute].
 *
 * Because a sync route only ever sees logs from the running process, its events carry a [LogError.Live]
 * and `event.error?.throwable` is the original exception.
 */
public abstract class SyncLogRoute(
    formatter: LogFormatter? = null,
) : LogRoute(formatter) {
    /**
     * The single entry point (bus or standalone). Filters, formats, then calls [emit]. Kept in one
     * place so route-level interceptors can slot in here later without touching individual routes.
     */
    public fun log(event: LogEvent<LogError.Live>) {
        if (!accepts(event)) return
        emit(format(event))
    }

    /** The one method you override: send the finished (already-formatted) event to your destination. */
    protected abstract fun emit(event: LogEvent<LogError.Live>)
}

/**
 * An asynchronous destination. [emit] is a `suspend` function run on the bus's background worker (one
 * mailbox + one consumer coroutine per route), so the caller never waits and a slow route only fills
 * its own mailbox — it can't hold up the other routes. Use this for file, network, or any suspending
 * I/O that doesn't need to survive a restart.
 *
 * The mailbox is in memory. Whatever is still queued when the process dies is lost — that's the
 * difference from [DurableLogRoute], which is fed from disk.
 *
 * Standalone (no bus), `log` is still a suspend function you can call from a coroutine.
 */
public abstract class AsyncLogRoute(
    formatter: LogFormatter? = null,
) : LogRoute(formatter) {
    /**
     * The single entry point (bus or standalone). Filters, formats, then calls [emit]. Kept in one
     * place so route-level interceptors can slot in here later without touching individual routes.
     */
    public suspend fun log(event: LogEvent<LogError.Live>) {
        if (!accepts(event)) return
        emit(format(event))
    }

    /** The one method you override: send the finished (already-formatted) event to your destination. */
    protected abstract suspend fun emit(event: LogEvent<LogError.Live>)
}

/**
 * A destination that survives a restart. Instead of a mailbox, a durable route is fed from the log
 * store: logs are written to disk first, then handed to the route in batches, and the route's position
 * is only advanced once it says the batch got through. Logs written during a session that crashed are
 * delivered on a later run.
 *
 * Batching is the point of the different shape — a durable route is usually an uploader, and shipping
 * two hundred lines in one request beats two hundred requests.
 *
 * Because its logs are read back from disk, a durable route receives a [LogError.Snapshot]: `type`, `message`
 * and `stackTrace` are all there, but there is no `throwable`, and `event.error?.throwable` will not
 * compile here. That's deliberate — the object cannot survive the process that threw it, and the type
 * says so rather than a doc comment.
 *
 * What a durable route guarantees is **at-least-once**: if the process dies after a successful send but
 * before the position is saved, that batch is sent again.
 */
public abstract class DurableLogRoute(
    /**
     * A stable identifier for this route, used to remember how far it has got through the log store.
     *
     * It must stay the same across app restarts **and across app versions** — change it and the route
     * starts again from the beginning of whatever is still on disk. It must also be unique within a
     * bus: two routes sharing an id would share one cursor, so each would see only the logs the other
     * had not already taken.
     *
     * Pick it yourself and write it as a literal:
     *
     * ```kotlin
     * class AcmeBackendRoute : DurableLogRoute(id = "acme-backend")
     * ```
     *
     * Don't derive it from the class name. R8 renames classes in release builds, so the id would
     * change between debug and release and between versions, silently resetting delivery.
     */
    public val id: String,
    formatter: LogFormatter? = null,
) : LogRoute(formatter) {

    init {
        require(id.isNotBlank()) {
            "${this::class.simpleName ?: "DurableLogRoute"} was created with a blank id. A durable " +
                "route's id keys its delivery cursor in the log store, so it must be a non-blank " +
                "string that stays the same across app versions. " +
                "Fix: pass one, e.g. class ${this::class.simpleName ?: "MyRoute"} : " +
                "DurableLogRoute(id = \"acme-backend\")."
        }
    }

    /**
     * The single entry point (bus or standalone). Filters and formats the batch, then calls [emit].
     * Returns what [emit] returned, or `true` when the filter left nothing to send.
     */
    public suspend fun log(events: List<LogEvent<LogError.Snapshot>>): Boolean {
        val accepted = events.filter { accepts(it) }.map { format(it) }
        return if (accepted.isEmpty()) true else emit(accepted)
    }

    /**
     * The one method you override: send this batch to your destination.
     *
     * Return `true` only when the whole batch is safely delivered — that's what advances this route's
     * position. Return `false` (or throw) and the same batch is offered again later, so a failed
     * upload is retried rather than dropped.
     */
    protected abstract suspend fun emit(events: List<LogEvent<LogError.Snapshot>>): Boolean
}
