package me.noukakis.re_do.task.ffmpeg

internal object FfmpegArgumentParser {

    /**
     * FFmpeg flags that are standalone switches and do NOT consume the next token as their value.
     * Source: https://ffmpeg.org/ffmpeg.html (global options, per-stream specifiers)
     */
    private val NO_ARG_FLAGS: Set<String> = setOf(
        // global switches
        "-y", "-n", "-hide_banner", "-nostdin", "-nostats", "-re",
        // stream disabling
        "-vn", "-an", "-sn", "-dn",
        // misc per-stream
        "-accurate_seek", "-noaccurate_seek",
        "-autoscale", "-noautoscale",
        "-copyts", "-notransfer",
        "-shortest",
    )

    fun tokenize(rawArgs: String): List<String> = rawArgs.split(Regex("""\s+""")).filter { it.isNotEmpty() }

    fun inputFilenames(tokens: List<String>): List<String> {
        val inputs = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            if (tokens[i] == "-i" && i + 1 < tokens.size) {
                inputs += tokens[i + 1]
                i += 2
            } else {
                i++
            }
        }
        return inputs
    }

    fun outputFilenames(tokens: List<String>): List<String> {
        val inputSet = inputFilenames(tokens).toSet()
        val outputs = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token in NO_ARG_FLAGS -> i += 1

                token.startsWith("-") -> i += 2

                token in inputSet -> i++

                else -> {
                    outputs += token
                    i++
                }
            }
        }
        return outputs
    }

    fun isFileReference(token: String): Boolean = !token.contains(':') && !token.contains('=')
}
