# LogBus

[![Maven Central](https://img.shields.io/maven-central/v/io.github.umangtapania/logbus)](https://central.sonatype.com/artifact/io.github.umangtapania/logbus)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](./LICENSE)
[![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS-lightgrey)](#install)

A small logging library for Kotlin Multiplatform, targeting Android and iOS.

LogBus is a **bus**. You build one with a list of routes, then log to it. The bus turns each call
into a `LogEvent`, runs it through your interceptors, and hands it to every route. A route decides
where the log actually goes — console, file, network, crash reporter, anywhere.

```kotlin
val bus = LogBus(routes = listOf(ConsoleRoute()))

bus.d("app started")
bus.i("user signed in", tag = "Auth")
bus.e("upload failed", tag = "Sync", error = exception)
```

## Install

```toml
# gradle/libs.versions.toml
[libraries]
logbus = { module = "io.github.umangtapania:logbus", version = "0.1.0" }
```

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.logbus)
        }
    }
}
```

Android `minSdk` 24. iOS targets: `iosX64`, `iosArm64`, `iosSimulatorArm64`.

## What it gives you

**Logs never crash your app.** A broken interceptor or a broken route is caught and contained. The
other routes still get the log.

**Slow routes can't block you.** Each async route has its own mailbox drained by its own coroutine,
so a stalled network route fills its own queue and nothing else.

**Order is preserved.** One worker per route, pulling one event at a time, so logs reach a route in
the order you sent them.

## Levels

Five ship by default, each with a priority and an emoji:

| Level | Priority | Shortcut |
|---|---|---|
| `VERBOSE` | 100 | `bus.v(...)` |
| `DEBUG` | 200 | `bus.d(...)` |
| `INFO` | 300 | `bus.i(...)` |
| `WARNING` | 400 | `bus.w(...)` |
| `ERROR` | 500 | `bus.e(...)` |

Levels are an interface, not a closed enum, so you can add your own:

```kotlin
object Analytics : LogLevel {
    override val name = "ANALYTICS"
    override val priority = 350
    override val emoji = "📊"
}

bus.log(Analytics, "checkout completed")
```

## Routes

A route is the one thing you implement. Pick the type that matches what your destination does, and
override `emit`.

**`SyncLogRoute`** — fast, non-suspending work. Runs inline on the calling thread, so the write
finishes before the log call returns. Use it for console output and for crash lines that must land
before the process dies.

```kotlin
class CrashRoute : SyncLogRoute() {
    override fun emit(event: LogEvent) {
        Crashlytics.log("${event.tag}: ${event.message}")
    }
}
```

**`AsyncLogRoute`** — suspending I/O. Runs on a background worker, so the caller never waits.

```kotlin
class NetworkRoute : AsyncLogRoute() {
    override suspend fun emit(event: LogEvent) {
        api.send(event.message)
    }
}
```

Keep a sync `emit` fast — it runs on your caller's thread. Anything that can block or suspend
belongs in an async route.

Every route gets a filter for free:

```kotlin
class WarningsOnly : AsyncLogRoute() {
    override fun isLoggable(event: LogEvent) =
        event.level.priority >= DefaultLevel.WARNING.priority

    override suspend fun emit(event: LogEvent) { /* ... */ }
}
```

### ConsoleRoute

The one route that ships with LogBus. It prints to each platform's native console:

- **Android** → `android.util.Log`, so logs land in Logcat with the right level and tag. Text past
  Logcat's ~4000 character limit is chunked instead of silently truncated.
- **iOS** → `NSLog`, which has no level or tag concept, so they are folded into the line:
  `ℹ️ INFO/Auth: user signed in`

An `error` is appended as a stack trace on the next line, so it is never lost.

## Formatters

A formatter turns an event into a string, any way you like. It belongs to the **route**, not the
bus, so different routes can format differently.

```kotlin
val bus = LogBus(routes = listOf(
    ConsoleRoute(formatter = { "[${it.tag}] ${it.message}" })
))
```

LogBus has no built-in formatters — you write the one your project needs. A route with no formatter
passes the raw message straight through.

## Interceptors

Interceptors run once, in order, before any route sees the event. Return a changed event, or `null`
to drop the log entirely.

```kotlin
val addUser = LogInterceptor { it.copy(metadata = it.metadata + ("userId" to currentUserId)) }
val hideTokens = LogInterceptor { it.copy(message = it.message.replace(tokenRegex, "***")) }

val bus = LogBus(
    routes = listOf(ConsoleRoute()),
    interceptors = listOf(addUser, hideTokens),
)
```

## Shutting down

```kotlin
bus.close()
```

Stops accepting new logs and returns straight away — it never waits and never cancels anything.
Everything already accepted is still delivered, including a log that is only halfway through the
interceptors: the async workers keep draining in the background until their queues are empty, and the
bus's scope then ends on its own. A log that arrives afterwards is simply refused, not reported as a
loss. Safe to call twice, and callable from anywhere, since it doesn't suspend — `onDestroy`, a
shutdown hook or a `finally` all work.

There is deliberately no `flush` or `await`. On a logger it would read as a promise that your logs are
now safe, and `close()` cannot make that promise: an async route's mailbox lives in memory, and most of
the ways a process ends — a native crash, an out-of-memory kill, the user swiping the app away — run no
code at all. Logs that have to survive that belong on a `DurableLogRoute`, which puts them on disk
before the route ever sees them.

The bus owns the coroutines it runs on. You choose *where* they run, with the optional `dispatcher`
parameter; *when* they end is `close()`'s business. That split is on purpose — a bus whose workers
could be cancelled from outside, or whose failure could cancel your own coroutines, would let logging
break the very thing it is there to report on.

```kotlin
val bus = LogBus(name = "main", routes = listOf(ConsoleRoute()), dispatcher = myTestDispatcher)
```

## How a log flows

```
bus.d("hi")
  └─ build LogEvent (default tag "LogBus", timestamp)
      └─ run interceptors in order ── any returns null? ─► dropped
          └─ hand to every route
              ├─ sync routes  → inline, on the calling thread
              └─ async routes → each route's own mailbox + worker
                  └─ isLoggable ─► formatter ─► emit
```

Async mailboxes are bounded at 1024 events and drop the **oldest** under a flood, so memory stays
bounded and the newest logs — the ones just before a hang or crash — are the ones kept.

## Building

```bash
./gradlew :logbus:assemble                                  # Android AAR
./gradlew :logbus:linkDebugFrameworkIosSimulatorArm64        # iOS framework
```

## Tests

```bash
./gradlew :logbus:testAndroidHostTest      # Android
./gradlew :logbus:iosSimulatorArm64Test    # iOS
./gradlew build                            # everything
```

## Design notes

[ARCHITECTURE.md](./ARCHITECTURE.md) explains why the pieces are shaped the way they are — in
particular why sync and async routes have to be two separate types rather than one interface.

## License

[Apache License 2.0](./LICENSE)
