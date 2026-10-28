package io.github.miclean.module.skip

/** Explicit, reversible input comparisons; existing installations retain their route. */
object SkipInputMode {
    const val KEY = "enhanced_input_mode"
    const val COMPAT = 0
    const val GKD_COMMAND = 1
    const val GKD_VIRTUAL = 2
    const val ACCESSIBILITY = 3
    @JvmStatic fun normalize(value: Int): Int = if (value in COMPAT..ACCESSIBILITY) value else COMPAT
    @JvmStatic fun label(value: Int): String = when (normalize(value)) {
        GKD_COMMAND -> "GKD 命令输入"
        GKD_VIRTUAL -> "GKD 虚拟触屏"
        ACCESSIBILITY -> "无障碍手势"
        else -> "当前兼容输入"
    }
    /** Typed and finite coordinates only; no rule text is inserted into a command. */
    fun gkdCommand(x: Float, y: Float, endX: Float, endY: Float, duration: Long): String {
        require(listOf(x,y,endX,endY).all { it.isFinite() && it >= 0 } && duration in 0..60000)
        return if (duration == 0L) "input tap $x $y\nexit\n"
            else "input swipe $x $y $endX $endY $duration\nexit\n"
    }
}
