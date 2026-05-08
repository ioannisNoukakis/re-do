package me.noukakis.re_do.task.ffmpeg

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FfmpegProgressParserTest {

    @Nested
    inner class ParseDurationSeconds {
        @Test
        fun `returns null when line contains no Duration`() {
            assertNull(FfmpegProgressParser.parseDurationSeconds("frame=  12 fps=0.0 time=00:00:01.00"))
        }

        @Test
        fun `parses hours minutes and seconds`() {
            assertEquals(
                3661.5,
                FfmpegProgressParser.parseDurationSeconds("  Duration: 01:01:01.50, start: 0.000000"),
            )
        }

        @Test
        fun `parses zero duration`() {
            assertEquals(0.0, FfmpegProgressParser.parseDurationSeconds("  Duration: 00:00:00.00, start: 0"))
        }
    }

    @Nested
    inner class ParseCurrentSeconds {
        @Test
        fun `returns null when line contains no time=`() {
            assertNull(FfmpegProgressParser.parseCurrentSeconds("  Duration: 00:01:00.00, start: 0"))
        }

        @Test
        fun `parses time= segment correctly`() {
            assertEquals(
                9.81,
                FfmpegProgressParser.parseCurrentSeconds("frame=234 fps=60 time=00:00:09.81 bitrate="),
            )
        }

        @Test
        fun `handles sub-second precision`() {
            assertEquals(
                0.5,
                FfmpegProgressParser.parseCurrentSeconds("time=00:00:00.50"),
            )
        }
    }
}
