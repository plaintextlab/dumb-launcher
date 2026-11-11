package com.plaintextlab.dumblauncher

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import java.util.concurrent.Executors

/** Two background threads for icon and app-list loading, plus a way back to the UI thread. */
object Bg {
    private val pool = Executors.newFixedThreadPool(2) { r -> Thread(r, "dumb-bg").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    fun <T> run(work: () -> T, then: (T) -> Unit = {}) {
        pool.execute {
            val result = try { work() } catch (e: Exception) { null }
            @Suppress("UNCHECKED_CAST")
            main.post { then(result as T) }
        }
    }
}

/** The app list, kept in memory between drawer openings and refreshed in the background. */
object AppList {
    @Volatile var cached: List<AppEntry>? = null
        private set

    /** Re-read the installed apps (background thread). Returns true if anything changed. */
    fun refresh(c: Context): Boolean {
        val fresh = Apps.all(c.applicationContext)
        fresh.forEach { it.t9 }          // work out the T9 codes here, not while typing
        val old = cached
        cached = fresh
        return old == null || old.map { it.target to it.label } != fresh.map { it.target to it.label }
    }

    @Volatile private var lastWarm = 0L

    /** Load the list and every icon ahead of time, so the drawer opens instantly (at most every 30 s). */
    fun warmUp(c: Context) {
        val now = SystemClock.elapsedRealtime()
        if (cached != null && now - lastWarm < 30_000) return
        lastWarm = now
        val app = c.applicationContext
        Bg.run({ refresh(app); cached.orEmpty().forEach { Icons.get(app, it) } })
    }

}
