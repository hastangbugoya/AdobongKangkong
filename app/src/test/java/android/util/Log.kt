package android.util

/**
 * Test-only stub for JVM unit tests.
 *
 * Your production code references android.util.Log in a few domain/usecase files.
 * In pure JVM tests we provide this minimal shim so those classes compile and run.
 */
object Log {
    @JvmStatic fun d(tag: String, msg: String): Int = 0
    @JvmStatic fun i(tag: String, msg: String): Int = 0
    @JvmStatic fun w(tag: String, msg: String): Int = 0
    @JvmStatic fun e(tag: String, msg: String): Int = 0
}
