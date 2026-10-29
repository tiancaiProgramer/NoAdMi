package io.github.miclean.module.skip

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.*
import rikka.shizuku.Shizuku

/** Runtime bridge is separate from the short-lived accessibility settings worker. */
data class SkipRuntimeScene(val app: String, val activity: String, val task: Int, val generation: Long, val listening: Boolean)
class SkipRuntimeClient(private val context: Context, private val onScene: (SkipRuntimeScene) -> Unit) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val io = SkipReadExecutor()
    private val handshake = SkipReadExecutor()
    private val control = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "NoAdMi-runtime-connect") }
    @Volatile private var binder: IBinder? = null
    @Volatile var scene: SkipRuntimeScene? = null
        private set
    @Volatile private var closed = false
    @Volatile private var epoch = 0L
    private var selected = false
    private var connection: ServiceConnection? = null
    private var args: Shizuku.UserServiceArgs? = null
    private var death: IBinder.DeathRecipient? = null
    private val recovery = SkipRuntimeRecovery { SystemClock.elapsedRealtime() }
    private var acceptedGeneration = Long.MIN_VALUE
    @Volatile var connectionGeneration = 0L
        private set
    private val received = Shizuku.OnBinderReceivedListener { main.post { if (!closed) { recovery.reset(); refresh() } } }
    private val died = Shizuku.OnBinderDeadListener { main.post { if (!closed) { unbind(); status = "Shizuku 已断开，等待服务恢复；使用无障碍输入" } } }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> main.post { if (!closed) { recovery.reset(); refresh() } } }
    private val retry = Runnable { if (!closed && selected) refresh() }
    private val timeout = Runnable { if (!closed && selected && connection != null) failed(epoch, "Shizuku 增强连接超时") }
    init { Shizuku.addBinderReceivedListenerSticky(received); Shizuku.addBinderDeadListener(died); Shizuku.addRequestPermissionResultListener(permission) }
    fun enabled(value: Boolean) { main.post { if (!closed) { if (selected != value) recovery.reset(); selected = value; refresh() } } }
    private data class Snapshot(val scene: SkipRuntimeScene?, val listening: Boolean, val generation: Long)
    private fun readScene(reply: Parcel): Snapshot {
        val app = reply.readString(); val activity = reply.readString(); val task = reply.readInt(); val generation = reply.readLong(); val listener = reply.readInt() != 0
        return Snapshot(if (app.isNullOrEmpty() || activity.isNullOrEmpty() || task < 0) null else SkipRuntimeScene(app, activity, task, generation, listener), listener, generation)
    }
    private fun accept(token: Long, snapshot: Snapshot) {
        if (closed || !selected || token != epoch) return
        if (snapshot.generation < acceptedGeneration) return
        acceptedGeneration = snapshot.generation
        val next = snapshot.scene
        val previous = scene
        scene = next
        status = if (snapshot.listening) "Shizuku 增强已连接" else "Shizuku 输入已连接；前台监听不可用"
        // An empty foreground snapshot is a successful connection, not an
        // indefinitely pending bind. Never retain an old privileged target.
        if (next != null && next != previous) onScene(next)
    }
    private fun failed(token: Long, reason: String) {
        if (closed || !selected || token != epoch) return
        unbind()
        val delay = recovery.failed()
        status = if (delay == null) "$reason，自动重连已暂停；请切换方式重试，使用无障碍输入"
            else "$reason，${delay / 1000}秒后自动重连；使用无障碍输入"
        if (delay != null) main.postDelayed(retry, delay)
    }
    private fun refresh() {
        if (closed) return
        if (!selected) { unbind(); status = ""; return }
        val granted = runCatching { Shizuku.pingBinder() && Shizuku.getVersion() >= 13 && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        if (!granted) { unbind(); status = "Shizuku 未运行或未授权，使用无障碍输入"; return }
        if (connection != null) return
        main.removeCallbacks(retry)
        val token = ++epoch
        val callback = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code != FIRST_CALL_TRANSACTION || getCallingUid() !in listOf(0, 2000)) return false
                data.enforceInterface(SkipRuntimeService.CALLBACK)
                val next = readScene(data); main.post { accept(token, next) }; return true
            }
        }
        val request = Shizuku.UserServiceArgs(ComponentName(context, SkipRuntimeService::class.java)).tag("noadmi_skip_runtime")
            .version(context.packageManager.getPackageInfo(context.packageName, 0).versionCode).daemon(false).processNameSuffix("skip_runtime")
        args = request
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                main.post { connect(token, service, callback) }
            }
            override fun onServiceDisconnected(name: ComponentName) { main.post { failed(token, "Shizuku 增强已断开") } }
            override fun onBindingDied(name: ComponentName) { main.post { failed(token, "Shizuku 增强绑定失效") } }
            override fun onNullBinding(name: ComponentName) { main.post { failed(token, "Shizuku 增强服务不可用") } }
        }
        status = "正在连接 Shizuku 增强…"
        try { Shizuku.bindUserService(request, connection!!); main.postDelayed(timeout, 25000) }
        catch (_: Exception) { failed(token, "Shizuku 增强启动失败") }
    }
    private fun connect(token: Long, service: IBinder, callback: IBinder) {
        if (closed || token != epoch) return
        if (binder === service) return
        binder = service
        val recipient = IBinder.DeathRecipient { main.post { failed(token, "Shizuku 增强进程已退出") } }
        death = recipient
        try { service.linkToDeath(recipient, 0) }
        catch (_: RemoteException) { failed(token, "Shizuku 增强进程已退出"); return }
        control.execute {
            try {
                val next = handshake.read(2500) {
                    val data = Parcel.obtain(); val reply = Parcel.obtain()
                    try {
                        data.writeInterfaceToken(SkipRuntimeService.DESCRIPTOR); data.writeStrongBinder(callback)
                        if (!service.transact(SkipRuntimeService.CONNECT, data, reply, 0)) error("Worker unavailable")
                        reply.readException(); readScene(reply)
                    } finally { data.recycle(); reply.recycle() }
                }
                main.post { if (!closed && selected && token == epoch) {
                    main.removeCallbacks(timeout); recovery.connected(); connectionGeneration++
                    accept(token, next)
                } }
            } catch (_: Exception) { main.post { failed(token, "Shizuku 增强握手失败") } }
        }
    }
    /** 0 means no input started, 1 accepted, 2 delivery uncertain; never double-tap on uncertainty. */
    fun touch(expected: SkipRuntimeScene, x: Float, y: Float, endX: Float, endY: Float, duration: Long): Int {
        val remote = binder ?: return 0
        if (scene != expected || closed) return 0
        return try {
            io.read((duration + 4500).coerceAtMost(64500)) {
                val data = Parcel.obtain(); val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(SkipRuntimeService.DESCRIPTOR); data.writeString(expected.app); data.writeString(expected.activity)
                    data.writeInt(expected.task); data.writeLong(expected.generation)
                    data.writeFloat(x); data.writeFloat(y); data.writeFloat(endX); data.writeFloat(endY); data.writeLong(duration)
                    val mode = runCatching { SkipRepository.get(context).preferences.getInt(SkipInputMode.KEY, SkipInputMode.COMPAT) }.getOrDefault(SkipInputMode.COMPAT)
                    data.writeInt(SkipInputMode.normalize(mode))
                    if (!remote.transact(SkipRuntimeService.TOUCH, data, reply, 0)) return@read 0
                    reply.readException(); reply.readInt().let { if (it == 0) -1 else it }
                } finally { data.recycle(); reply.recycle() }
            }
        } catch (_: Exception) { 2 }
    }
    private fun unbind() {
        epoch++
        val oldBinder = binder; val oldDeath = death
        binder = null; death = null; scene = null; acceptedGeneration = Long.MIN_VALUE
        if (oldBinder != null && oldDeath != null) runCatching { oldBinder.unlinkToDeath(oldDeath, 0) }
        main.removeCallbacks(retry); main.removeCallbacks(timeout)
        val request = args; val prior = connection; args = null; connection = null
        if (request != null) runCatching { Shizuku.unbindUserService(request, prior, true) }
    }
    override fun close() {
        closed = true; main.removeCallbacksAndMessages(null); unbind(); io.close(); handshake.close(); control.shutdownNow()
        Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(died); Shizuku.removeRequestPermissionResultListener(permission); status = ""
    }
    companion object { @JvmField @Volatile var status = "" }
}
