package io.github.miclean.module.skip

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import li.gkd.app.subscription.RawSubscription

@Serializable
data class SkipSource(
    val id: Long, val document: String, val url: String = "", val enabled: Boolean = false,
    val apps: Map<String, Boolean> = emptyMap(), val groups: Map<String, Boolean> = emptyMap(),
    val checkedAt: Long = 0,
) {
    val subscription: RawSubscription by lazy { RawSubscription.parse(document) }
    val name: String get() = subscription.name
    val version: Int get() = subscription.version
    val local: Boolean get() = url.isEmpty()
    fun groupKey(app: String, key: Int): String = "$app/$key"
    fun groupEnabled(app: String, group: RawSubscription.RawGroupProps): Boolean =
        enabled && apps[app] != false && (groups[groupKey(app, group.key)]
            ?: group.enable ?: subscription.getCategory(group.name)?.enable ?: true)
}

/** One atomic file owns subscriptions and their overrides; an unsuccessful update never replaces it. */
data class SkipSourceSnapshot(val sources: List<SkipSource>, val revision: Long)
class SkipSources(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Volatile private var state = SkipSourceSnapshot(load(), 0)
    private val current: List<SkipSource> get() = state.sources
    val revision: Long get() = state.revision
    fun snapshot(): SkipSourceSnapshot = state
    fun list(): List<SkipSource> = current
    fun find(id: Long): SkipSource = current.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("订阅已被删除")
    private fun load(): List<SkipSource> {
        if (!file.exists()) return emptyList()
        val data = json.decodeFromString<List<SkipSource>>(file.readText())
        require(data.map { it.id }.distinct().size == data.size) { "订阅 ID 重复" }
        data.forEach { require(it.subscription.id == it.id) { "订阅 ID 不一致" } }
        return data
    }
    private fun save(data: List<SkipSource>) {
        file.parentFile!!.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.outputStream().use { stream ->
            stream.write(json.encodeToString(data).toByteArray(Charsets.UTF_8)); stream.fd.sync()
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        state = SkipSourceSnapshot(data, state.revision + 1)
    }
    @Synchronized fun add(document: String, url: String, enabled: Boolean): SkipSource {
        require(document.toByteArray().size <= MAX_BYTES) { "订阅文件超过 10 MB" }
        val raw = RawSubscription.parse(document)
        require(raw.name.isNotBlank()) { "订阅名称不能为空" }
        if (url.isNotEmpty()) { requireHttps(url); require(raw.id >= 0) { "远程订阅 ID 应大于等于 0" } }
        require(current.none { it.id == raw.id }) { "订阅 ID 已存在，请在原订阅内更新" }
        val source = SkipSource(raw.id, document, url, enabled)
        save(current + source); return source
    }
    @Synchronized fun replace(id: Long, document: String, remote: Boolean): Boolean {
        val old = find(id)
        val raw = RawSubscription.parse(document)
        require(raw.id == id) { "更新返回了不同的订阅 ID，原订阅已保留" }
        if (remote) {
            require(raw.version >= old.version) { "服务器版本较旧，原订阅已保留" }
            if (raw.version == old.version) { checked(id); return false }
        } else require(old.local) { "远程订阅不能直接编辑，请导入为本地订阅" }
        save(current.map { if (it.id == id) old.copy(document = document, checkedAt = System.currentTimeMillis()) else it })
        return true
    }
    @Synchronized fun checked(id: Long) = modify(id) { it.copy(checkedAt = System.currentTimeMillis()) }
    @Synchronized fun enable(id: Long, value: Boolean) = modify(id) { it.copy(enabled = value) }
    @Synchronized fun enableApp(id: Long, app: String, value: Boolean) = modify(id) { it.copy(apps = it.apps + (app to value)) }
    @Synchronized fun enableGroup(id: Long, app: String, key: Int, value: Boolean) = modify(id) {
        it.copy(groups = it.groups + (it.groupKey(app, key) to value))
    }
    @Synchronized fun remove(id: Long) = save(current.filter { it.id != id })
    private fun modify(id: Long, change: (SkipSource) -> SkipSource) {
        find(id); save(current.map { if (it.id == id) change(it) else it })
    }
    companion object {
        const val MAX_BYTES = 10 * 1024 * 1024
        @JvmStatic fun requireHttps(value: String): URI {
            val uri = URI(value.trim())
            require(uri.scheme.equals("https", true) && !uri.host.isNullOrEmpty() && uri.userInfo == null) { "请填写 HTTPS 订阅直链" }
            return uri
        }
        @JvmStatic fun download(url: String): String {
            var next = requireHttps(url).toURL()
            repeat(6) {
                val connection = next.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000; connection.readTimeout = 20000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "NoAdMi/0.12.0")
                try {
                    val status = connection.responseCode
                    if (status in listOf(301, 302, 303, 307, 308)) {
                        val location = connection.getHeaderField("Location") ?: error("订阅重定向缺少地址")
                        next = requireHttps(next.toURI().resolve(location).toString()).toURL()
                    } else {
                        require(status in 200..299) { "订阅服务器返回 HTTP $status" }
                        require(connection.contentLengthLong <= MAX_BYTES) { "订阅文件超过 10 MB" }
                        return connection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                            while (true) {
                                val n = input.read(buffer); if (n < 0) break
                                require(output.size() + n <= MAX_BYTES) { "订阅文件超过 10 MB" }; output.write(buffer, 0, n)
                            }
                            output.toString("UTF-8")
                        }
                    }
                } finally { connection.disconnect() }
            }
            error("订阅重定向次数过多")
        }
    }
}
