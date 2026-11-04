package io.github.umangtapania.logbus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Wraps one [AsyncLogRoute] with its own mailbox (a channel) and a single worker coroutine that drains
 * it. Because one coroutine pulls events one at a time and finishes each `route.log()` before taking the
 * next, this route sees logs in the exact order they were sent — and a slow route only fills its own
 * mailbox, it never blocks the other routes.
 *
 * This is the bus's async delivery layer — LogBus's equivalent of Logback's `AsyncAppender`. Only async
 * routes get a worker; [SyncLogRoute]s run inline in the bus, and [DurableLogRoute]s are fed from the
 * log store instead. The route itself knows nothing about channels or scopes.
 */
internal class RouteWorker(
    private val route: AsyncLogRoute,
    scope: CoroutineScope,
    capacity: Int = DEFAULT_CAPACITY,
) {
    // A bounded FIFO mailbox. On overflow (a route stalls under a flood) we drop the OLDEST events and
    // keep the newest — the ones right before a hang/crash are the useful ones. A drop shows up as a
    // gap in the sequence numbers this route received, so it is visible rather than silent.
    private val mailbox = Channel<LogEvent<LogError.Live>>(capacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // THE single consumer. The for-loop suspends when the mailbox is empty and resumes when an event
    // arrives; it runs route.log() to completion before taking the next event, so delivery stays in
    // order. try/catch keeps one bad log from killing the worker (a broken route can't stop its own
    // future logs, or the other routes).
    private val job: Job = scope.launch {
        for (event in mailbox) {
            try {
                route.log(event)
            } catch (cause: Throwable) {
                // Keep draining, but report the loss — this event reaches the route nowhere else.
                route.reportDropped(event, cause)
            }
        }
    }

    init {
        // A clean shutdown leaves nothing behind: close() shuts the mailbox and the loop above drains
        // what is left before ending, so this finds an empty channel and does nothing. It matters for
        // the endings we did not plan — a dispatcher that rejects work, an Error the loop can't catch.
        // Those leave this route's queue with no consumer, and these events reach it nowhere else.
        job.invokeOnCompletion { cause ->
            while (true) {
                val stranded = mailbox.tryReceive().getOrNull() ?: break
                route.reportDropped(stranded, cause ?: endedWithoutDraining())
            }
        }
    }

    // Hand an event to this route's mailbox. Never blocks the caller: with DROP_OLDEST, trySend succeeds
    // unless the mailbox has been closed (it evicts the oldest queued event rather than failing).
    fun send(event: LogEvent<LogError.Live>) {
        // This route's own floor, applied before the event takes a slot. The bus's pre-build gate uses
        // the MINIMUM floor across all routes, so an event only one route wants is still offered to
        // every mailbox — and a route that doesn't want it would discard it in accepts() only after its
        // worker had pulled it out. That isn't merely wasted work: a slot spent on an event this route
        // will refuse is a slot that DROP_OLDEST can evict an event it wants from.
        //
        // Read live rather than cached, so this can never filter harder than the route's own log() does.
        if (event.level.priority < route.minPriority) return
        if (mailbox.trySend(event).isFailure) {
            // Shouldn't happen: the bus closes mailboxes only once no log call is still running. If it
            // ever does, the event is gone, and a reported loss beats a silent one.
            route.reportDropped(event, sentAfterClose())
        }
    }

    // Stop taking new logs. Closing the channel makes the for-loop finish its remaining queued events,
    // then end — so nothing already queued is lost.
    fun close() {
        mailbox.close()
    }

    // Wait for the worker to finish draining and end. Used by LogBus's internal join() and tests.
    suspend fun join() {
        job.join()
    }

    private fun endedWithoutDraining() = IllegalStateException(
        "LogBus: the worker for ${route::class.simpleName} ended with events still queued, so they " +
            "were never delivered.",
    )

    private fun sentAfterClose() = IllegalStateException(
        "LogBus: an event reached the mailbox of ${route::class.simpleName} after it was closed.",
    )

    companion object {
        // Big enough that normal apps never drop; small enough to bound memory if a route stalls.
        const val DEFAULT_CAPACITY = 1024
    }
}
