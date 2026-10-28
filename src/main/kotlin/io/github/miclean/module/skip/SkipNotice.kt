package io.github.miclean.module.skip

object SkipNotice {
    const val ENABLED = "skip_notice_enabled"
    const val TEXT = "skip_notice_text"
    const val DEFAULT_TEXT = "已发送跳过操作"
    const val MAX_LENGTH = 80
    @JvmStatic fun text(value: String?): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return DEFAULT_TEXT
        val end = normalized.offsetByCodePoints(0, normalized.codePointCount(0, normalized.length).coerceAtMost(MAX_LENGTH))
        return normalized.substring(0, end)
    }
}
