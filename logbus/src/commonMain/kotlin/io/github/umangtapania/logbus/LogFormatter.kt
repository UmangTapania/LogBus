package io.github.umangtapania.logbus

/**
 * A route's tool for turning an event into a string — any way the dev wants (plain, JSON, boxed,
 * one-line). A formatter **belongs to the route, not the bus**: a route holds one and uses it inside
 * its own `log()` (see [LogRoute]). The bus never touches formatting.
 *
 * It takes the widened `LogEvent<LogError>`, so one formatter serves every kind of route — a live one
 * and a durable one can share the same timestamp style. [LogError.type], [LogError.message] and
 * [LogError.stackTrace] are available whichever route it was handed to.
 *
 * Only the interface exists for now; concrete formatters (like box + emoji) are add-ons shipped later.
 */
public fun interface LogFormatter {
    public fun format(event: LogEvent<LogError>): String
}
