package io.github.umangtapania.logbus

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A hand-built live event, for the standalone (no bus) paths — the public constructor. */
private fun liveEvent(
    message: String = "hi",
    level: LogLevel = DefaultLevel.INFO,
    error: Throwable? = null,
    sessionId: String = LogEvent.NO_SESSION,
) = LogEvent(
    level = level,
    tag = "T",
    message = message,
    error = error,
    sessionId = sessionId,
)

/** An event standing in for one read back from the store, so it carries a real position. */
private fun storedEvent(
    message: String = "hi",
    level: LogLevel = DefaultLevel.INFO,
    sequence: Long = 1L,
) = LogEvent.fromStore(
    sessionId = "s1",
    sequence = sequence,
    level = level,
    tag = "T",
    message = message,
    timeMillis = 0L,
    error = LogError.Snapshot("IOException", "disk full", "IOException: disk full\n\tat Foo.bar(Foo.kt:1)"),
)

@OptIn(ExperimentalCoroutinesApi::class)
class LogRouteTest {

    @Test
    fun formatterReplacesMessageBeforeEmit() = runTest(StandardTestDispatcher()) {
        val route = RecordingRoute(formatter = LogFormatter { "F:${it.message}" })
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("hi")
        bus.shutdown()

        assertEquals("F:hi", route.events.single().message)
    }

    @Test
    fun noFormatterLeavesMessageUnchanged() = runTest(StandardTestDispatcher()) {
        val route = RecordingRoute() // no formatter
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("hi")
        bus.shutdown()

        assertEquals("hi", route.events.single().message)
    }

    @Test
    fun asyncRouteWorksStandaloneWithoutBus() = runTest {
        // No bus at all — the async route's own suspend log() runs its pipeline (filter → formatter → emit).
        val route = RecordingRoute(formatter = LogFormatter { "F:${it.message}" })

        route.log(liveEvent())

        assertEquals("F:hi", route.events.single().message)
    }

    @Test
    fun syncRouteWorksStandaloneWithoutBus() {
        // Same pipeline, non-suspend path: a sync route's log() runs standalone with no coroutine at all.
        val route = SyncRecordingRoute(formatter = LogFormatter { "F:${it.message}" })

        route.log(liveEvent())

        assertEquals("F:hi", route.events.single().message)
    }

    @Test
    fun isLoggableFiltersOutLowerLevels() = runTest(StandardTestDispatcher()) {
        val route = LevelFilterRoute(min = DefaultLevel.WARNING)
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("dropped")   // below threshold
        bus.w("kept")      // at threshold
        bus.e("kept too")  // above threshold
        bus.shutdown()

        assertEquals(listOf("kept", "kept too"), route.events.map { it.message })
    }

    @Test
    fun oneFormatterServesBothLiveAndDurableRoutes() = runTest {
        // A formatter takes the widened LogEvent<LogError>, so the same instance works either side.
        val formatter = LogFormatter { "[${it.level.name}] ${it.message} (${it.error?.type ?: "-"})" }
        val live = SyncRecordingRoute(formatter)
        val durable = RecordingDurableRoute(formatter = formatter)

        live.log(liveEvent(message = "boom", error = IllegalStateException("bad")))
        durable.log(listOf(storedEvent(message = "boom")))

        assertEquals("[INFO] boom (IllegalStateException)", live.events.single().message)
        assertEquals("[INFO] boom (IOException)", durable.batches.single().single().message)
    }

    @Test
    fun durableRouteCanBeDrivenWithNoBusThroughPublicApiOnly() {
        // Everything a third-party route author has: the public factory plus the route's own log().
        val event = LogEvent.fromStore(
            sessionId = "s1",
            sequence = 42L,
            level = DefaultLevel.ERROR,
            tag = "Checkout",
            message = "upload failed",
            timeMillis = 1_700_000_000_000L,
            error = LogError.Snapshot("IOException", "disk full", "IOException: disk full"),
        )

        assertEquals(42L, event.sequence)
        assertEquals("IOException", event.error?.type)
        assertEquals(1_700_000_000_000L, event.timeMillis)
    }

    @Test
    fun durableRouteReceivesWholeBatch() = runTest {
        val route = RecordingDurableRoute()

        val sent = route.log(listOf(storedEvent("a", sequence = 1), storedEvent("b", sequence = 2)))

        assertTrue(sent)
        assertEquals(listOf("a", "b"), route.batches.single().map { it.message })
        assertEquals(listOf(1L, 2L), route.batches.single().map { it.sequence })
    }

    @Test
    fun durableRouteReportsFailureSoTheBatchCanBeRetried() = runTest {
        val route = RecordingDurableRoute(succeed = false)

        assertFalse(route.log(listOf(storedEvent("a"))))
    }

    @Test
    fun durableRouteSucceedsTriviallyWhenItsFilterRejectsEverything() = runTest {
        val route = object : DurableLogRoute(id = "filter-all") {
            var called = false
            override fun isLoggable(event: LogEvent<LogError>): Boolean = false
            override suspend fun emit(events: List<LogEvent<LogError.Snapshot>>): Boolean {
                called = true
                return false
            }
        }

        assertTrue(route.log(listOf(storedEvent("a"))))
        assertFalse(route.called) // nothing to send → emit never runs, and nothing is retried
    }

    @Test
    fun handBuiltEventIsStampedWithTheCurrentTime() {
        // The whole form: level, tag, message. The log is happening now, so it is stamped now.
        val event = LogEvent(level = DefaultLevel.INFO, tag = "T", message = "hi")

        assertTrue(event.timeMillis > 0L)
        assertEquals(LogEvent.UNSEQUENCED, event.sequence)
    }

    @Test
    fun copyPinsTheTimestampWhenATestNeedsAFixedOne() {
        val event = LogEvent(level = DefaultLevel.INFO, tag = "T", message = "hi")
            .copy(timeMillis = 0L)

        assertEquals(0L, event.timeMillis)
    }

    @Test
    fun factoryWrapsAThrowableSoTheCallerDoesNotHaveTo() {
        val boom = IllegalStateException("bad")

        val event = LogEvent(level = DefaultLevel.ERROR, tag = "T", message = "failed", error = boom)

        assertSame(boom, event.error?.throwable)
        assertEquals("IllegalStateException", event.error?.type)
    }

    @Test
    fun handBuiltEventIsUnsequencedAndSessionless() {
        // No bus handed this out, so it claims no position and no run — both 0, both meaning "none".
        val event = liveEvent()

        assertEquals(LogEvent.UNSEQUENCED, event.sequence)
        assertEquals(LogEvent.NO_SESSION, event.sessionId)
        assertEquals(0L, LogEvent.UNSEQUENCED)
    }

    @Test
    fun handBuiltEventCanStillCarryASession() {
        // Attributing a hand-driven log to the current run is allowed; inventing a position is not.
        val event = liveEvent(sessionId = "s99")

        assertEquals("s99", event.sessionId)
        assertEquals(LogEvent.UNSEQUENCED, event.sequence)
    }

    @Test
    fun interceptorStyleCopyKeepsThePosition() {
        // copy() stays public so interceptors work, and it carries sessionId/sequence through.
        val original = storedEvent(sequence = 7L)

        val changed = original.copy(message = "rewritten")

        assertEquals(7L, changed.sequence)
        assertEquals("s1", changed.sessionId)
        assertEquals("rewritten", changed.message)
    }

    @Test
    fun storedErrorCarriesEverythingButTheThrowable() = runTest {
        val route = RecordingDurableRoute()

        route.log(listOf(storedEvent()))

        val error = route.batches.single().single().error!!
        assertEquals("IOException", error.type)
        assertEquals("disk full", error.message)
        assertTrue(error.stackTrace.startsWith("IOException: disk full"))
        // There is deliberately no `error.throwable` here — it would not compile.
    }
}
