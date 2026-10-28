package io.github.miclean.module.skip

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.*
import android.util.DisplayMetrics
import android.view.Display
import java.util.concurrent.TimeUnit

/** Dedicated Shizuku worker: foreground metadata and bounded touch only. No arbitrary commands. */
class SkipRuntimeService(private val context: Context) : Binder() {
    private val ownerUid = context.applicationInfo.uid
    private val lock = Any()
    private val thread = HandlerThread("NoAdMi-task-stack").apply { start() }
    private val handler = Handler(thread.looper)
    private var observer: IBinder? = null
    private var generation = 0L
    private var top: ActivityManager.RunningTaskInfo? = null
    private var listener: Any? = null
    private var listenerReady = false
    private var lastFront: Pair<Long, ComponentName>? = null
    private val manager: Any by lazy {
        val service = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "activity_task") as IBinder
        Class.forName("android.app.IActivityTaskManager\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, service)
    }
    init {
        require(context.packageName == "io.github.miclean.module")
        check(Process.myUid() == 0 || Process.myUid() == 2000)
        attachInterface(null, DESCRIPTOR)
    }
    private fun tasks(): List<ActivityManager.RunningTaskInfo> {
        val methods = Class.forName("android.app.IActivityTaskManager").methods.filter { it.name == "getTasks" }.sortedByDescending { it.parameterCount }
        for (method in methods) {
            val args: Array<Any> = when (method.parameterCount) {
                4 -> arrayOf(1, false, false, Display.DEFAULT_DISPLAY)
                3 -> arrayOf(1, false, false)
                1 -> arrayOf(1)
                else -> continue
            }
            try {
                @Suppress("UNCHECKED_CAST")
                return method.invoke(manager, *args) as List<ActivityManager.RunningTaskInfo>
            } catch (_: ReflectiveOperationException) { }
        }
        return emptyList()
    }
    private fun foreground(): ActivityManager.RunningTaskInfo? = tasks().firstOrNull()?.takeIf {
        it.javaClass.getField("displayId").getInt(it) == Display.DEFAULT_DISPLAY && it.javaClass.getField("userId").getInt(it) == ownerUid / 100000 && it.topActivity != null
    }
    private fun refresh(force: Boolean = false) {
        val current = foreground() ?: return
        synchronized(lock) {
            if (force || top?.topActivity != current.topActivity || top?.taskId != current.taskId) generation++
            top = current
        }
    }
    private fun registerListener() {
        if (listener != null) return
        val stub = Class.forName("android.app.ITaskStackListener\$Stub")
        fun code(name: String): Int = stub.getDeclaredField("TRANSACTION_$name").apply { isAccessible = true }.getInt(null)
        val stackChanged = code("onTaskStackChanged")
        val movedToFront = code("onTaskMovedToFront")
        val callback = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == INTERFACE_TRANSACTION) { reply?.writeString("android.app.ITaskStackListener"); return true }
                if (getCallingUid() != 1000) return false
                data.enforceInterface("android.app.ITaskStackListener")
                // Read fresh default-display metadata instead of decoding API-specific task parcels.
                if (code == stackChanged || code == movedToFront) handler.post {
                    runCatching {
                        val cpn = foreground()?.topActivity ?: return@runCatching
                        val now = SystemClock.elapsedRealtime()
                        if (code == movedToFront) { lastFront = now to cpn; refresh(true) }
                        else {
                            val previous = lastFront
                            if (previous != null && previous.second == cpn && now - previous.first > 200) lastFront = null
                            else refresh(true)
                        }
                        notifyObserver()
                    }
                }
                return true
            }
        }
        val proxy = stub.getMethod("asInterface", IBinder::class.java).invoke(null, callback)
        Class.forName("android.app.IActivityTaskManager").getMethod("registerTaskStackListener", Class.forName("android.app.ITaskStackListener")).invoke(manager, proxy)
        listener = proxy; listenerReady = true
    }
    private fun writeScene(reply: Parcel) = synchronized(lock) {
        reply.writeString(top?.topActivity?.packageName)
        reply.writeString(top?.topActivity?.className)
        reply.writeInt(top?.taskId ?: -1)
        reply.writeLong(generation)
        reply.writeInt(if (listenerReady) 1 else 0)
    }
    private fun notifyObserver() {
        val receiver = synchronized(lock) { observer } ?: return
        val data = Parcel.obtain()
        try { data.writeInterfaceToken(CALLBACK); writeScene(data); receiver.transact(FIRST_CALL_TRANSACTION, data, null, IBinder.FLAG_ONEWAY) }
        catch (_: RemoteException) { synchronized(lock) { if (observer === receiver) observer = null } }
        finally { data.recycle() }
    }
    // Same IInputManager route as GKD SafeInputManager. Shell command is a
    // compatibility fallback only if this API cannot be resolved before DOWN.
    private fun inputApi(x: Float, y: Float, endX: Float, endY: Float, duration: Long, virtual: Boolean): Int? {
        val target: Any
        val method: java.lang.reflect.Method
        val displayMethod: java.lang.reflect.Method
        try {
            val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, Context.INPUT_SERVICE) as IBinder
            target = Class.forName("android.hardware.input.IInputManager\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            val api = Class.forName("android.hardware.input.IInputManager")
            method = if (Build.VERSION.SDK_INT >= 33) api.getMethod("injectInputEventToTarget", android.view.InputEvent::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                else api.getMethod("injectInputEvent", android.view.InputEvent::class.java, Int::class.javaPrimitiveType)
            displayMethod = android.view.InputEvent::class.java.getDeclaredMethod("setDisplayId", Int::class.javaPrimitiveType).apply { isAccessible = true }
        } catch (_: ReflectiveOperationException) { return null }
        val source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        val device = if (virtual) 0 else android.view.InputDevice.getDeviceIds().firstOrNull { android.view.InputDevice.getDevice(it)?.supportsSource(source) == true } ?: 0
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, px: Float, py: Float): Boolean {
            val pointer = android.view.MotionEvent.PointerProperties().apply { id = 0; toolType = android.view.MotionEvent.TOOL_TYPE_FINGER }
            val coordinates = android.view.MotionEvent.PointerCoords().apply { this.x = px; this.y = py; pressure = if (action == android.view.MotionEvent.ACTION_UP) 0f else 1f; size = 1f }
            val event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
                arrayOf(pointer), arrayOf(coordinates), 0, 0, 1f, 1f, device, 0, source, 0)
            try {
                event.source = source; displayMethod.invoke(event, Display.DEFAULT_DISPLAY)
                return if (Build.VERSION.SDK_INT >= 33) method.invoke(target, event, 2, -1) == true else method.invoke(target, event, 2) == true
            } finally { event.recycle() }
        }
        return try {
            val accepted = send(android.view.MotionEvent.ACTION_DOWN, x, y)
            if (!accepted) { send(android.view.MotionEvent.ACTION_UP, x, y); 2 }
            else {
                if (duration > 0) {
                    var elapsed = 0L
                    while (elapsed < duration) {
                        Thread.sleep(minOf(16L, duration - elapsed)); elapsed = (SystemClock.uptimeMillis() - down).coerceAtMost(duration)
                        val fraction = elapsed.toFloat() / duration
                        if (!send(android.view.MotionEvent.ACTION_MOVE, x + (endX - x) * fraction, y + (endY - y) * fraction)) break
                    }
                }
                if (send(android.view.MotionEvent.ACTION_UP, endX, endY)) 1 else 2
            }
        } catch (_: Exception) { runCatching { send(android.view.MotionEvent.ACTION_UP, x, y) }; 2 }
    }
    // Return unavailable BEFORE delivery, accepted, or uncertain AFTER starting input.
    private fun touch(data: Parcel): Int {
        val expectedApp = data.readString() ?: return 0
        val expectedActivity = data.readString()
        val expectedTask = data.readInt(); val expectedGeneration = data.readLong()
        val x = data.readFloat(); val y = data.readFloat(); val endX = data.readFloat(); val endY = data.readFloat(); val duration = data.readLong(); val mode = data.readInt()
        if (SkipRuntimePolicy.protected(expectedApp) || duration !in 0..60000) return 0
        if (!context.getSystemService(PowerManager::class.java).isInteractive || context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return 0
        refresh()
        val current = synchronized(lock) { top }
        if (current?.topActivity != ComponentName(expectedApp, expectedActivity ?: return 0) || current.taskId != expectedTask || synchronized(lock) { generation } != expectedGeneration) return 0
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return 0
        val metrics = DisplayMetrics(); display.getRealMetrics(metrics)
        if (!SkipRuntimePolicy.coordinates(x, y, endX, endY, metrics.widthPixels, metrics.heightPixels)) return 0
        if (mode !in SkipInputMode.COMPAT..SkipInputMode.GKD_VIRTUAL) return 0
        if (mode != SkipInputMode.GKD_COMMAND) {
            val api = inputApi(x, y, endX, endY, duration, mode == SkipInputMode.GKD_VIRTUAL)
            if (api != null) return if (api == 1 && mode == SkipInputMode.GKD_VIRTUAL) 5 else api
            if (mode == SkipInputMode.GKD_VIRTUAL) return 0
        }
        val args = if (duration == 0L) arrayOf("/system/bin/input", "-d", "0", "tap", x.toString(), y.toString())
            else arrayOf("/system/bin/input", "-d", "0", "swipe", x.toString(), y.toString(), endX.toString(), endY.toString(), duration.toString())
        val process = try {
            if (mode == SkipInputMode.GKD_COMMAND) ProcessBuilder("/system/bin/sh").redirectErrorStream(true).start()
            else ProcessBuilder(*args).redirectErrorStream(true).start()
        } catch (_: Exception) { return 0 }
        return try {
            if (mode == SkipInputMode.GKD_COMMAND) {
                process.outputStream.bufferedWriter(Charsets.UTF_8).use {
                    it.write(SkipInputMode.gkdCommand(x,y,endX,endY,duration)); it.flush()
                }
            }
            // Fixed input command has bounded output; drain it without copying private app data.
            val drain = Thread { runCatching { process.inputStream.use { val buffer = ByteArray(1024); while (it.read(buffer) >= 0) { } } } }.apply { isDaemon = true; start() }
            if (!process.waitFor(duration + 4000, TimeUnit.MILLISECONDS)) { process.destroy(); 2 }
            else { drain.join(100); if (process.exitValue() == 0) { if (mode == SkipInputMode.GKD_COMMAND) 4 else 3 } else 2 }
        } catch (_: Exception) { process.destroy(); 2 }
    }
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == INTERFACE_TRANSACTION) { reply?.writeString(DESCRIPTOR); return true }
        if (code == 16777115) {
            if (getCallingUid() != ownerUid && getCallingUid() != Process.myUid()) throw SecurityException("Invalid worker owner")
            kotlin.system.exitProcess(0)
        }
        if (getCallingUid() != ownerUid) throw SecurityException("Only NoAdMi may call this worker")
        data.enforceInterface(DESCRIPTOR)
        val identity = clearCallingIdentity()
        try {
            when (code) {
                CONNECT -> { synchronized(lock) { observer = data.readStrongBinder() }; runCatching { registerListener() }; refresh(); reply!!.writeNoException(); writeScene(reply) }
                SNAPSHOT -> { refresh(); reply!!.writeNoException(); writeScene(reply) }
                TOUCH -> { val result = touch(data); reply!!.writeNoException(); reply.writeInt(result) }
                else -> return super.onTransact(code, data, reply, flags)
            }
        } catch (error: Exception) { reply?.writeException(IllegalStateException("Runtime operation unavailable", error)) }
        finally { restoreCallingIdentity(identity) }
        return true
    }
    companion object {
        const val DESCRIPTOR = "io.github.miclean.module.skip.Runtime"
        const val CALLBACK = "io.github.miclean.module.skip.RuntimeScene"
        const val CONNECT = FIRST_CALL_TRANSACTION
        const val SNAPSHOT = FIRST_CALL_TRANSACTION + 1
        const val TOUCH = FIRST_CALL_TRANSACTION + 2
    }
}
