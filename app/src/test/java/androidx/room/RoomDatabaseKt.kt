@file:Suppress("unused")

package androidx.room

/**
 * Test-only shadow for the Room `withTransaction` extension.
 *
 * The production use case imports `androidx.room.withTransaction` and calls it.
 * In pure JVM unit tests we don't have a fully initialized Room runtime, so we
 * provide a no-op transactional wrapper that just runs the block.
 */
suspend fun <T> RoomDatabase.withTransaction(block: suspend () -> T): T = block()
