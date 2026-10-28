package io.github.miclean.module.skip

import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SkipDiagnostic(val time: Long, val app: String, val reason: String,
    val candidates: Int, val nodes: Int, val elapsedMs: Long, val detail: String = "")

/** Local reason codes/counts only: no screen text, node trees, selectors or account data. */
class SkipDiagnostics(private val file: File) {
    private val gate = SkipDiagnosticGate { SystemClock.elapsedRealtime() }
    private val json = Json { ignoreUnknownKeys = true }
    private var entries = try {
        if (file.exists()) json.decodeFromString<List<SkipDiagnostic>>(file.readText()).takeLast(100) else emptyList()
    } catch (_: Exception) { emptyList() }
    fun record(app: String, reason: String, candidates: Int = 0, nodes: Int = 0, elapsedMs: Long = 0, detail: String = "") {
        if (!gate.claim()) return
        Log.i("NoAdMiSkip", "state=$reason app=$app candidates=$candidates nodes=$nodes elapsedMs=$elapsedMs detail=$detail")
        entries = (entries + SkipDiagnostic(System.currentTimeMillis(), app, reason, candidates, nodes, elapsedMs, detail)).takeLast(100)
        try {
            file.parentFile?.mkdirs()
            val atomic = AtomicFile(file); val stream = atomic.startWrite()
            try { stream.write(json.encodeToString(entries).toByteArray()); atomic.finishWrite(stream) }
            catch (e: Exception) { atomic.failWrite(stream) }
        } catch (_: Exception) { Log.w("NoAdMiSkip", "diagnostic storage unavailable") }
    }
}
