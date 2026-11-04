package io.github.umangtapania.logbus

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The dispatcher a test hands the bus. A fresh dispatcher on the test's own scheduler: async delivery
 * still runs on virtual time, while the bus keeps its own job — the end of a test body can't reach into
 * the workers, and the bus can't reach back into the test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.busDispatcher(): CoroutineDispatcher = StandardTestDispatcher(testScheduler)

/**
 * Shut the bus down and wait for its queues to empty. [LogBus.close] returns immediately by design, so
 * any test that asserts on delivered events needs the join too.
 */
internal suspend fun LogBus.shutdown() {
    close()
    join()
}

/** An async route that records every (finished) event it receives, for inspection. */
internal class RecordingRoute(formatter: LogFormatter? = null) : AsyncLogRoute(formatter) {
    val events = mutableListOf<LogEvent<LogError.Live>>()
    override suspend fun emit(event: LogEvent<LogError.Live>) {
        events.add(event)
    }
}

/** An async route that always throws, to prove one broken route can't stop the others. */
internal class BrokenRoute : AsyncLogRoute() {
    val drops = mutableListOf<Pair<LogEvent<LogError>, Throwable>>()
    override suspend fun emit(event: LogEvent<LogError.Live>) {
        throw RuntimeException("boom")
    }

    override fun onDropped(event: LogEvent<LogError>, cause: Throwable) {
        drops.add(event to cause)
    }
}

/** A route whose emit blocks (virtually) for [delayMillis] — used to prove slow routes stay isolated. */
internal class SlowRoute(private val delayMillis: Long) : AsyncLogRoute() {
    val events = mutableListOf<LogEvent<LogError.Live>>()
    override suspend fun emit(event: LogEvent<LogError.Live>) {
        delay(delayMillis)
        events.add(event)
    }
}

/** An async route that only accepts events at or above [min] — exercises [LogRoute.isLoggable]. */
internal class LevelFilterRoute(private val min: LogLevel) : AsyncLogRoute() {
    val events = mutableListOf<LogEvent<LogError.Live>>()
    override fun isLoggable(event: LogEvent<LogError>): Boolean = event.level.priority >= min.priority
    override suspend fun emit(event: LogEvent<LogError.Live>) {
        events.add(event)
    }
}

/** An async route that declares a level floor up front — exercises the bus's pre-build gate. */
internal class FloorRoute(private val floor: LogLevel) : AsyncLogRoute() {
    val events = mutableListOf<LogEvent<LogError.Live>>()
    override val minPriority: Int get() = floor.priority
    override suspend fun emit(event: LogEvent<LogError.Live>) {
        events.add(event)
    }
}

/** A sync route that records inline, no worker — proves synchronous, on-caller-thread delivery. */
internal class SyncRecordingRoute(formatter: LogFormatter? = null) : SyncLogRoute(formatter) {
    val events = mutableListOf<LogEvent<LogError.Live>>()
    override fun emit(event: LogEvent<LogError.Live>) {
        events.add(event)
    }
}

/** A sync route that always throws, to prove a broken sync route can't crash the caller or block others. */
internal class BrokenSyncRoute : SyncLogRoute() {
    val drops = mutableListOf<Pair<LogEvent<LogError>, Throwable>>()
    override fun emit(event: LogEvent<LogError.Live>) {
        throw RuntimeException("boom")
    }

    override fun onDropped(event: LogEvent<LogError>, cause: Throwable) {
        drops.add(event to cause)
    }
}

/** An async route that works, and also remembers anything it was told it lost. */
internal class DropAwareRoute : AsyncLogRoute() {
    val events = mutableListOf<LogEvent<LogError.Live>>()
    val drops = mutableListOf<LogEvent<LogError>>()
    override suspend fun emit(event: LogEvent<LogError.Live>) {
        events.add(event)
    }

    override fun onDropped(event: LogEvent<LogError>, cause: Throwable) {
        drops.add(event)
    }
}

/** A sync route whose own drop handler throws — a broken handler must not escape into the caller. */
internal class BrokenHandlerRoute : SyncLogRoute() {
    override fun emit(event: LogEvent<LogError.Live>) {
        throw RuntimeException("boom")
    }

    override fun onDropped(event: LogEvent<LogError>, cause: Throwable) {
        throw IllegalStateException("handler is broken too")
    }
}



/** A durable route that records the batches handed to it. Not yet fed by the bus — store comes later. */
internal class RecordingDurableRoute(
    private val succeed: Boolean = true,
    formatter: LogFormatter? = null,
    id: String = "recording",
) : DurableLogRoute(id, formatter) {
    val batches = mutableListOf<List<LogEvent<LogError.Snapshot>>>()
    override suspend fun emit(events: List<LogEvent<LogError.Snapshot>>): Boolean {
        batches.add(events)
        return succeed
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LogBusTest {

    @Test
    fun defaultTagAppliedWhenNoneGiven() = runTest(StandardTestDispatcher()) {
        val route = RecordingRoute()
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("no tag here")
        bus.shutdown()

        assertEquals(1, route.events.size)
        assertEquals("LogBus", route.events.single().tag)
    }

    @Test
    fun explicitTagPreserved() = runTest(StandardTestDispatcher()) {
        val route = RecordingRoute()
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("with tag", tag = "Checkout")
        bus.shutdown()

        assertEquals("Checkout", route.events.single().tag)
    }

    @Test
    fun shortcutsMapToRightLevelsAndErrorThreadsThrough() = runTest(StandardTestDispatcher()) {
        val route = RecordingRoute()
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())
        val boom = IllegalStateException("bad")

        bus.v("v")
        bus.d("d")
        bus.i("i")
        bus.w("w")
        bus.e("e", error = boom)
        bus.shutdown()

        assertEquals(
            listOf(
                DefaultLevel.VERBOSE,
                DefaultLevel.DEBUG,
                DefaultLevel.INFO,
                DefaultLevel.WARNING,
                DefaultLevel.ERROR,
            ),
            route.events.map { it.level },
        )
        // A live route gets the original object, not a copy or a rebuild.
        assertSame(boom, route.events.last().error?.throwable)
    }

    @Test
    fun liveErrorExposesTypeMessageAndStackTrace() = runTest(StandardTestDispatcher()) {
        val route = RecordingRoute()
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.e("failed", error = IllegalStateException("bad"))
        bus.shutdown()

        val error = route.events.single().error!!
        assertEquals("IllegalStateException", error.type)
        assertEquals("bad", error.message)
        assertTrue(error.stackTrace.contains("bad"))
    }

    @Test
    fun eventsWithoutAnErrorHaveNullError() = runTest(StandardTestDispatcher()) {
        val route = RecordingRoute()
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("nothing wrong")
        bus.shutdown()

        assertNull(route.events.single().error)
    }

    @Test
    fun sequenceStartsAtOneAndIncrements() = runTest(StandardTestDispatcher()) {
        val route = RecordingRoute()
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("a")
        bus.i("b")
        bus.i("c")
        bus.shutdown()

        assertEquals(listOf(1L, 2L, 3L), route.events.map { it.sequence })
        // The bus counts from 1, so UNSEQUENCED (0) can never collide with a real position.
        assertTrue(route.events.none { it.sequence == LogEvent.UNSEQUENCED })
    }

    @Test
    fun everyRouteSeesTheSameSequenceForTheSameLog() = runTest(StandardTestDispatcher()) {
        // The whole point of a bus-assigned sequence: line routes up against each other.
        val async = RecordingRoute()
        val sync = SyncRecordingRoute()
        val bus = LogBus("test", listOf(async, sync), emptyList(), busDispatcher())

        bus.i("a")
        bus.i("b")
        bus.shutdown()

        assertEquals(sync.events.map { it.sequence }, async.events.map { it.sequence })
        assertEquals(listOf(1L, 2L), sync.events.map { it.sequence })
    }

    @Test
    fun everyEventCarriesTheSessionId() = runTest(StandardTestDispatcher()) {
        val route = RecordingRoute()
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("a")
        bus.i("b")
        bus.shutdown()

        assertTrue(bus.sessionId.isNotBlank())
        assertTrue(route.events.all { it.sessionId == bus.sessionId })
    }

    @Test
    fun sessionIdIsRandomCharsPlusTheStartTimeAndDiffersPerBus() = runTest(StandardTestDispatcher()) {
        val a = LogBus("a", listOf(RecordingRoute()), emptyList(), busDispatcher())
        val b = LogBus("b", listOf(RecordingRoute()), emptyList(), busDispatcher())

        // <random>-<timeMillis>: two runs can't collide even started in the same millisecond.
        val (random, time) = a.sessionId.split("-").let { it[0] to it[1] }
        assertEquals(8, random.length)
        assertTrue(time.toLong() > 0L)
        assertTrue(a.sessionId != b.sessionId)
        assertTrue(a.sessionId != LogEvent.NO_SESSION)

        a.shutdown()
        b.shutdown()
    }

    @Test
    fun asyncRouteIsToldWhenItsEmitThrows() = runTest(StandardTestDispatcher()) {
        val broken = BrokenRoute()
        val bus = LogBus("test", listOf(broken), emptyList(), busDispatcher())

        bus.i("lost")
        bus.shutdown()

        val (event, cause) = broken.drops.single()
        assertEquals("lost", event.message)
        assertEquals("boom", cause.message)
    }

    @Test
    fun syncRouteIsToldWhenItsEmitThrows() = runTest(StandardTestDispatcher()) {
        val broken = BrokenSyncRoute()
        val bus = LogBus("test", listOf(broken), emptyList(), busDispatcher())

        bus.i("lost")

        val (event, cause) = broken.drops.single()
        assertEquals("lost", event.message)
        assertEquals("boom", cause.message)

        bus.shutdown()
    }


    @Test
    fun aDropHandlerThatThrowsCannotEscapeIntoTheCaller() = runTest(StandardTestDispatcher()) {
        // The first route's emit throws, and then its onDropped throws too. Neither may reach the
        // caller, and the route after it must still be delivered to.
        val good = SyncRecordingRoute()
        val bus = LogBus("test", listOf(BrokenHandlerRoute(), good), emptyList(), busDispatcher())

        bus.i("survives")

        assertEquals(listOf("survives"), good.events.map { it.message })
        bus.shutdown()
    }

    @Test
    fun busNeedsANonBlankName() = runTest(StandardTestDispatcher()) {
        val failure = assertFailsWith<IllegalArgumentException> {
            LogBus(" ", listOf(RecordingRoute()), emptyList(), busDispatcher())
        }

        assertTrue(failure.message!!.contains("Fix:"))
    }

    @Test
    fun minPriorityGateDropsBelowEveryRoutesFloor() = runTest(StandardTestDispatcher()) {
        val route = FloorRoute(DefaultLevel.WARNING)
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("below the floor")
        bus.w("at the floor")
        bus.shutdown()

        assertEquals(listOf("at the floor"), route.events.map { it.message })
    }

    @Test
    fun gatedLogsDoNotBurnSequenceNumbers() = runTest(StandardTestDispatcher()) {
        // The gate runs before the event is built, so a gated log never reaches the sequencer.
        val route = FloorRoute(DefaultLevel.WARNING)
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("gated")
        bus.w("first real log")
        bus.shutdown()

        assertEquals(1L, route.events.single().sequence)
    }

    @Test
    fun aRoutesOwnFloorStillFiltersWhenAnotherRouteWantsMore() = runTest(StandardTestDispatcher()) {
        // The open route drags the bus-wide gate down to "everything", so the event does get built.
        // The strict route must then drop it itself — a declared floor filters, it isn't only a hint.
        val strict = FloorRoute(DefaultLevel.ERROR)
        val open = RecordingRoute()
        val bus = LogBus("test", listOf(strict, open), emptyList(), busDispatcher())

        bus.i("quiet")
        bus.shutdown()

        assertEquals(1, open.events.size)
        assertTrue(strict.events.isEmpty())
    }

    @Test
    fun aFloodForOneRouteCannotEvictAnotherRoutesLogs() = runTest(StandardTestDispatcher()) {
        // The open route drags the bus-wide gate down, so every INFO below is built and offered around.
        // If they took slots in the strict route's mailbox, they would push the one ERROR it actually
        // wants off the front of its queue — DROP_OLDEST evicts the oldest, and the ERROR is first in.
        // Checking the route's own floor before it is queued is what keeps that log alive.
        val strict = FloorRoute(DefaultLevel.ERROR)
        val open = FloorRoute(DefaultLevel.VERBOSE)
        val bus = LogBus("test", listOf(strict, open), emptyList(), busDispatcher())

        bus.e("the one that matters")
        repeat(RouteWorker.DEFAULT_CAPACITY + 10) { bus.i("flood $it") }
        bus.shutdown()

        assertEquals(listOf("the one that matters"), strict.events.map { it.message })
    }

    @Test
    fun floorFiltersAStandaloneRouteWithNoBus() = runTest {
        val route = FloorRoute(DefaultLevel.ERROR)

        route.log(
            LogEvent(level = DefaultLevel.INFO, tag = "T", message = "quiet"),
        )

        assertTrue(route.events.isEmpty())
    }

    @Test
    fun interceptorCanModifyEvent() = runTest(StandardTestDispatcher()) {
        val route = RecordingRoute()
        val addUser = LogInterceptor { event -> event.copy(metadata = event.metadata + ("userId" to "42")) }
        val bus = LogBus("test", listOf(route), listOf(addUser), busDispatcher())

        bus.i("hi")
        bus.shutdown()

        assertEquals("42", route.events.single().metadata["userId"])
    }

    @Test
    fun interceptorSeesSequenceAndSession() = runTest(StandardTestDispatcher()) {
        val route = RecordingRoute()
        val stamp = LogInterceptor { event ->
            event.copy(metadata = event.metadata + ("seq" to event.sequence.toString()))
        }
        val bus = LogBus("test", listOf(route), listOf(stamp), busDispatcher())

        bus.i("hi")
        bus.shutdown()

        assertEquals("1", route.events.single().metadata["seq"])
    }

    @Test
    fun interceptorReturningNullDropsLog() = runTest(StandardTestDispatcher()) {
        val route = RecordingRoute()
        val dropAll = LogInterceptor { null }
        val bus = LogBus("test", listOf(route), listOf(dropAll), busDispatcher())

        bus.i("should vanish")
        bus.shutdown()

        assertTrue(route.events.isEmpty())
    }

    @Test
    fun noRoutesIsANoOp() = runTest(StandardTestDispatcher()) {
        val bus = LogBus("test", emptyList(), emptyList(), busDispatcher())

        // Must not throw and must simply do nothing.
        bus.i("into the void")
        bus.shutdown()
    }

    @Test
    fun brokenRouteDoesNotStopOthers() = runTest(StandardTestDispatcher()) {
        val good = RecordingRoute()
        val bus = LogBus("test", listOf(BrokenRoute(), good), emptyList(), busDispatcher())

        bus.i("survives")
        bus.shutdown()

        assertEquals(1, good.events.size)
        assertEquals("survives", good.events.single().message)
    }

    @Test
    fun customLevelWorks() = runTest(StandardTestDispatcher()) {
        val analytics = object : LogLevel {
            override val name = "ANALYTICS"
            override val priority = 350
            override val emoji: String? = null
        }
        val route = RecordingRoute()
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.log(analytics, "checkout done")
        bus.shutdown()

        val event = route.events.single()
        assertEquals(analytics, event.level)
        assertEquals(350, event.level.priority)
        assertNull(event.level.emoji)
    }

    @Test
    fun logsArriveInOrder() = runTest(StandardTestDispatcher()) {
        val route = RecordingRoute()
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        // Under StandardTestDispatcher the worker doesn't run until we suspend, so all 500 sends land
        // in the mailbox (capacity 1024) before any draining — none are dropped.
        val sent = (0 until 500).map { "msg-$it" }
        sent.forEach { bus.i(it) }
        bus.shutdown()

        assertEquals(sent, route.events.map { it.message })
    }

    @Test
    fun slowRouteDoesNotHoldUpFastRoute() = runTest(StandardTestDispatcher()) {
        val slow = SlowRoute(delayMillis = 10_000)
        val fast = RecordingRoute()
        val bus = LogBus("test", listOf(slow, fast), emptyList(), busDispatcher())

        bus.i("x")
        // Run only what's ready at the current virtual time: the fast worker records immediately; the
        // slow worker enters emit() and parks on its 10s delay. No time is advanced.
        runCurrent()

        assertEquals(1, fast.events.size)      // fast route delivered without waiting for slow
        assertTrue(slow.events.isEmpty())      // slow route still mid-delay

        // Now drain everything (close advances virtual time through the slow delay).
        bus.shutdown()
        assertEquals(1, slow.events.size)
    }

    @Test
    fun syncRouteDeliversInlineBeforeCallReturns() = runTest(StandardTestDispatcher()) {
        val route = SyncRecordingRoute()
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("now")
        // No bus.shutdown(), no dispatcher advance: a sync route runs inline on the calling thread, so the
        // event is already recorded. (An AsyncLogRoute would still be sitting in its mailbox here.)
        assertEquals(1, route.events.size)
        assertEquals("now", route.events.single().message)

        bus.shutdown()
    }

    @Test
    fun asyncRouteDoesNotDeliverInline() = runTest(StandardTestDispatcher()) {
        // Counterpart to the test above: proves the sync route's inline delivery is a real difference,
        // not something every route does. The async worker hasn't run yet at this point.
        val route = RecordingRoute()
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("later")
        assertTrue(route.events.isEmpty())

        bus.shutdown()
        assertEquals(1, route.events.size)
    }

    @Test
    fun brokenSyncRouteDoesNotCrashCallerOrStopOthers() = runTest(StandardTestDispatcher()) {
        val good = SyncRecordingRoute()
        val bus = LogBus("test", listOf(BrokenSyncRoute(), good), emptyList(), busDispatcher())

        bus.i("survives") // must not throw, even though the first sync route throws inline
        assertEquals(1, good.events.size)
        assertEquals("survives", good.events.single().message)

        bus.shutdown()
    }

    @Test
    fun syncRouteIgnoresLogsAfterClose() = runTest(StandardTestDispatcher()) {
        val route = SyncRecordingRoute()
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("before")
        bus.shutdown()
        bus.i("after") // refused by the gate → ignored

        assertEquals(listOf("before"), route.events.map { it.message })
    }

    @Test
    fun aLogInFlightWhenCloseHappensIsStillDelivered() = runTest(StandardTestDispatcher()) {
        // The race, made deterministic. An interceptor runs after the log has been accepted and before
        // it reaches any route — exactly the window close() has to respect, so closing from inside one
        // is the whole bug in a single thread. If the gate were a plain flag, this log would be built,
        // filtered, numbered, and then handed to a mailbox that had already been shut.
        val route = RecordingRoute()
        lateinit var bus: LogBus
        val closeMidFlight = LogInterceptor { event ->
            bus.close()
            event
        }
        bus = LogBus("test", listOf(route), listOf(closeMidFlight), busDispatcher())

        bus.i("in flight")
        bus.join()

        assertEquals(listOf("in flight"), route.events.map { it.message })
    }

    @Test
    fun closeDoesNotWaitButTheQueueStillDrains() = runTest(StandardTestDispatcher()) {
        val route = RecordingRoute()
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("one")
        bus.i("two")
        bus.close()

        // close() neither suspends nor dispatches, so the worker has not run at this point.
        assertTrue(route.events.isEmpty())

        bus.join() // the queue drains on its own; join only waits for it to finish
        assertEquals(listOf("one", "two"), route.events.map { it.message })
    }

    @Test
    fun closeIsSafeToCallTwice() = runTest(StandardTestDispatcher()) {
        val route = RecordingRoute()
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("one")
        bus.close()
        bus.close()
        bus.join()

        assertEquals(listOf("one"), route.events.map { it.message })
    }

    @Test
    fun logsAfterCloseAreRefusedNotReportedAsDropped() = runTest(StandardTestDispatcher()) {
        // A refused log was never accepted, so it is not a loss and the route is not told about it.
        val route = DropAwareRoute()
        val bus = LogBus("test", listOf(route), emptyList(), busDispatcher())

        bus.i("before")
        bus.close()
        bus.i("after")
        bus.join()

        assertEquals(listOf("before"), route.events.map { it.message })
        assertTrue(route.drops.isEmpty())
    }

    @Test
    fun anInterceptorThatThrowsDoesNotStrandTheShutdown() = runTest(StandardTestDispatcher()) {
        // The in-flight count is given up in a finally. Miss it once and the bus could never finish
        // closing: this test would hang rather than fail.
        val route = RecordingRoute()
        val broken = LogInterceptor { throw RuntimeException("boom") }
        val bus = LogBus("test", listOf(route), listOf(broken), busDispatcher())

        bus.i("swallowed")
        bus.shutdown()

        assertTrue(route.events.isEmpty())
    }

    @Test
    fun aBusWithNoAsyncRoutesStillFinishesItsShutdown() = runTest(StandardTestDispatcher()) {
        // No workers to drain, so the job has no children — shutting down must still complete it
        // rather than wait for something that was never launched.
        val bus = LogBus("test", listOf(SyncRecordingRoute()), emptyList(), busDispatcher())

        bus.i("inline")
        bus.shutdown()
    }

    @Test
    fun durableRouteNeedsANonBlankId() {
        val failure = assertFailsWith<IllegalArgumentException> { RecordingDurableRoute(id = "  ") }

        // Names the offending class and says what to do, so no source-reading is needed.
        assertTrue(failure.message!!.contains("RecordingDurableRoute"))
        assertTrue(failure.message!!.contains("Fix:"))
    }

    @Test
    fun durableRoutesSharingAnIdAreRejected() = runTest(StandardTestDispatcher()) {
        // Two routes with one id would share a cursor, so each would miss what the other consumed.
        val failure = assertFailsWith<IllegalArgumentException> {
            LogBus("test", listOf(RecordingDurableRoute(id = "backend"), RecordingDurableRoute(id = "backend")),
                emptyList(),
                busDispatcher(),
            )
        }
        // The message must name the id AND the classes using it, or he is left hunting.
        val message = failure.message!!
        assertTrue(message.contains("backend"))
        assertTrue(message.contains("RecordingDurableRoute"))
        assertTrue(message.contains("Fix:"))
    }

    @Test
    fun busNameIsReportedInConfigurationErrors() = runTest(StandardTestDispatcher()) {
        val failure = assertFailsWith<IllegalArgumentException> {
            LogBus("analytics", listOf(RecordingDurableRoute(id = "dup"), RecordingDurableRoute(id = "dup")), emptyList(), busDispatcher())
        }
        assertTrue(failure.message!!.contains("analytics"))
    }

    @Test
    fun durableRouteIsAcceptedAndContributesItsFloor() = runTest(StandardTestDispatcher()) {
        // The store isn't built yet, so nothing is delivered to a durable route — but the bus must
        // accept one alongside the others without disturbing them.
        val durable = RecordingDurableRoute()
        val live = RecordingRoute()
        val bus = LogBus("test", listOf(durable, live), emptyList(), busDispatcher())

        bus.i("still reaches the live route")
        bus.shutdown()

        assertEquals(1, live.events.size)
        assertTrue(durable.batches.isEmpty())
    }
}
