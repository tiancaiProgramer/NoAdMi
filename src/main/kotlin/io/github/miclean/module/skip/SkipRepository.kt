package io.github.miclean.module.skip

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.io.File
import java.util.concurrent.Executors
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class SkipLog(val time: Long, val subscription: String, val app: String, val group: String, val rule: String, val result: String, val sourceId: Long? = null)

class SkipRepository private constructor(context: Context) {
    private val app = context.applicationContext
    val preferences: SharedPreferences = app.getSharedPreferences("smart_skip", Context.MODE_PRIVATE)
    private val usage = app.getSharedPreferences("smart_skip_usage", Context.MODE_PRIVATE)
    private val directory = File(app.filesDir, "smart_skip")
    val sources = SkipSources(File(directory, "sources.json"))
    private val logFile = File(directory, "actions.json")
    private val json = Json { ignoreUnknownKeys = true }
    private var logs: List<SkipLog> = if (logFile.exists()) try {
        json.decodeFromString<List<SkipLog>>(logFile.readText()).takeLast(200)
    } catch (e: Exception) { emptyList() } else emptyList()
    val enabled: Boolean get() = preferences.getBoolean("enabled", false)
    fun setEnabled(value: Boolean) { preferences.edit().putBoolean("enabled", value).apply() }
    @Synchronized fun logs(): List<SkipLog> = logs.reversed()
    @Synchronized fun clearLogs() { logFile.delete(); logs = emptyList() }
    @Synchronized fun record(rule: SkipRule, app: String, result: String) {
        logs = (logs + SkipLog(System.currentTimeMillis(), rule.source.name, app, rule.group.name,
            rule.raw.name ?: "规则 ${rule.index + 1}", result, rule.source.id)).takeLast(200)
        directory.mkdirs()
        val atomic = android.util.AtomicFile(logFile)
        val stream = atomic.startWrite()
        try { stream.write(json.encodeToString(logs).toByteArray()); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); android.util.Log.w("NoAdMiSkip", "执行记录保存失败", e) }
    }
    /** Package IDs and visit times only, observed on confirmed foreground transitions. */
    fun noteAppVisit(packageName: String) {
        usage.edit().putLong(packageName, System.currentTimeMillis()).apply()
    }
    fun recentVisits(): Map<String, Long> = usage.all.mapNotNull { (key, value) ->
        (value as? Long)?.let { key to it }
    }.toMap()
    @Synchronized fun recentTriggers(id: Long): Map<String, Long> {
        val name = sources.find(id).name
        return logs.filter { (it.sourceId == id || (it.sourceId == null && it.subscription == name)) &&
            it.result.startsWith("已执行 ") }.groupBy { it.app }.mapValues { (_, entries) -> entries.maxOf { it.time } }
    }
    fun update(id: Long): Boolean {
        val source = sources.find(id)
        require(!source.local) { "本地订阅请使用编辑或重新导入" }
        // The URL explicitly added by the user remains the trusted update origin.
        return sources.replace(id, SkipSources.download(source.url), true)
    }
    fun updateAll(): String {
        var changed = 0; var checked = 0; val errors = mutableListOf<String>()
        sources.list().filter { !it.local && it.enabled }.forEach { source ->
            try { if (update(source.id)) changed++; checked++ }
            catch (e: Exception) { errors.add("${source.name}：${e.message}") }
        }
        val result = "已检查 $checked 个订阅，更新 $changed 个"
        val message = result + if (errors.isEmpty()) "" else "\n" + errors.joinToString("\n")
        preferences.edit().putString("update_result", message).putLong("update_time", System.currentTimeMillis()).apply()
        return message
    }
    fun schedule() {
        val scheduler = app.getSystemService(JobScheduler::class.java)
        if (!preferences.getBoolean("auto_update", false)) { scheduler.cancel(JOB_ID); return }
        scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(app, SkipUpdateJob::class.java))
            .setPeriodic(24 * 60 * 60 * 1000L)
            .setRequiredNetworkType(if (preferences.getBoolean("wifi_only", true)) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true).build())
    }
    fun serviceEnabled(): Boolean {
        if (Settings.Secure.getInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        val expected = ComponentName(app.packageName, "com.google.android.accessibility.selecttospeak.SelectToSpeakService")
        val enabled = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
    companion object {
        private const val JOB_ID = 0x4e41444d
        @Volatile private var instance: SkipRepository? = null
        @JvmStatic fun get(context: Context): SkipRepository = instance ?: synchronized(this) {
            instance ?: SkipRepository(context).also { instance = it }
        }
        @JvmField val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "NoAdMi-subscriptions") }
    }
}

class SkipUpdateJob : JobService() {
    private var future: java.util.concurrent.Future<*>? = null
    override fun onStartJob(params: JobParameters): Boolean {
        future = SkipRepository.executor.submit {
            try { SkipRepository.get(this).updateAll() }
            finally { jobFinished(params, false) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { future?.cancel(true); return true }
}
