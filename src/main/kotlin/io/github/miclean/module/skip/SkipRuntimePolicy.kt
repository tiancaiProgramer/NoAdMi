package io.github.miclean.module.skip

/** Shared by the matcher and privileged worker; never deliver touch to a permission/lock surface. */
object SkipRuntimePolicy {
    fun protected(app: String): Boolean = app.isEmpty() || app in setOf("io.github.miclean.module", "android", "com.android.systemui", "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.lbe.security.miui", "com.android.packageinstaller", "com.google.android.packageinstaller", "com.miui.packageinstaller")
    fun coordinates(x: Float, y: Float, endX: Float, endY: Float, width: Int, height: Int): Boolean =
        width > 0 && height > 0 && listOf(x, y, endX, endY).all { it.isFinite() } &&
            x >= 0 && y >= 0 && endX >= 0 && endY >= 0 && x < width && endX < width && y < height && endY < height
}
