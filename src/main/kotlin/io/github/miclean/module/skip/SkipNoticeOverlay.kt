package io.github.miclean.module.skip

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/** Short accessibility feedback, using the service's own authorized, non-interactive window. */
class SkipNoticeOverlay(private val service: AccessibilityService) {
    private val main = Handler(Looper.getMainLooper())
    private val windows = service.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private val hide = Runnable { dismiss() }
    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
    fun show(message: String) {
        main.removeCallbacks(hide)
        val current = view
        if (current != null) current.text = message else {
            val text = TextView(service).apply {
                setTextColor(Color.rgb(36,44,40)); textSize = 14f; gravity = Gravity.CENTER
                setPadding(dp(18),dp(12),dp(18),dp(12))
                maxWidth = (service.resources.displayMetrics.widthPixels * .84f).toInt()
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                background = GradientDrawable().apply { setColor(Color.rgb(247,246,241)); cornerRadius = dp(14).toFloat(); setStroke(dp(1).coerceAtLeast(1),Color.rgb(219,228,212)) }
                text = message
            }
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = dp(72); packageName = service.packageName
            }
            try { windows.addView(text,params);view=text }
            catch (error: Exception) { android.util.Log.w("NoAdMiSkip","Skip feedback window unavailable",error);return }
        }
        main.postDelayed(hide,1800)
    }
    fun dismiss() {
        main.removeCallbacks(hide)
        view?.let { try { windows.removeView(it) } catch (_: Exception) {} }
        view = null
    }
}
