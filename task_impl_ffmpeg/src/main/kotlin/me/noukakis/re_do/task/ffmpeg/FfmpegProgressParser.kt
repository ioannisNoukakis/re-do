package me.noukakis.re_do.task.ffmpeg

private val DURATION_REGEX = Regex("""Duration:\s*(\d+):(\d+):(\d+\.?\d*)""")
private val TIME_REGEX = Regex("""time=(\d+):(\d+):(\d+\.?\d*)""")

internal object FfmpegProgressParser {
    fun parseDurationSeconds(line: String): Double? {
        val match = DURATION_REGEX.find(line) ?: return null
        val (h, m, s) = match.destructured
        return h.toDouble() * 3600 + m.toDouble() * 60 + s.toDouble()
    }

    fun parseCurrentSeconds(line: String): Double? {
        val match = TIME_REGEX.find(line) ?: return null
        val (h, m, s) = match.destructured
        return h.toDouble() * 3600 + m.toDouble() * 60 + s.toDouble()
    }
}
