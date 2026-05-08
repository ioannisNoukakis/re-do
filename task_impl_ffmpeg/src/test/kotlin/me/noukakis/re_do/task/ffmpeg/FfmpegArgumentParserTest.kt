package me.noukakis.re_do.task.ffmpeg

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FfmpegArgumentParserTest {

    @Nested
    inner class Tokenize {
        @Test
        fun `splits on single space`() {
            assertEquals(listOf("-i", "a.mp4", "b.mp3"), FfmpegArgumentParser.tokenize("-i a.mp4 b.mp3"))
        }

        @Test
        fun `splits on multiple spaces`() {
            assertEquals(listOf("-i", "a.mp4"), FfmpegArgumentParser.tokenize("-i  a.mp4"))
        }

        @Test
        fun `returns empty list for empty string`() {
            assertEquals(emptyList(), FfmpegArgumentParser.tokenize(""))
        }
    }

    @Nested
    inner class InputFilenames {
        @Test
        fun `returns token after -i`() {
            assertEquals(
                listOf("cat.mp4"),
                FfmpegArgumentParser.inputFilenames(listOf("-i", "cat.mp4", "out.mp3")),
            )
        }

        @Test
        fun `returns multiple tokens for multiple -i flags`() {
            assertEquals(
                listOf("a.mp4", "b.mp4"),
                FfmpegArgumentParser.inputFilenames(listOf("-i", "a.mp4", "-i", "b.mp4", "out.mp4")),
            )
        }

        @Test
        fun `returns empty list when no -i flag`() {
            assertEquals(
                emptyList(),
                FfmpegArgumentParser.inputFilenames(listOf("-f", "null", "-")),
            )
        }
    }

    @Nested
    inner class OutputFilenames {
        @Test
        fun `returns single output filename`() {
            assertEquals(
                listOf("out.mp3"),
                FfmpegArgumentParser.outputFilenames(listOf("-i", "cat.mp4", "out.mp3")),
            )
        }

        @Test
        fun `returns multiple outputs`() {
            assertEquals(
                listOf("audio.mp3", "video.mp4"),
                FfmpegArgumentParser.outputFilenames(listOf("-i", "input.mkv", "audio.mp3", "video.mp4")),
            )
        }

        @Test
        fun `excludes value tokens following known option flags`() {
            assertEquals(
                listOf("output.mp4"),
                FfmpegArgumentParser.outputFilenames(listOf("-i", "input.mp4", "-vf", "scale=640:480", "output.mp4")),
            )
        }

        @Test
        fun `excludes input file from outputs`() {
            assertEquals(
                listOf("out.wav"),
                FfmpegArgumentParser.outputFilenames(listOf("-stream_loop", "-1", "-i", "input.wav", "out.wav")),
            )
        }

        @Test
        fun `excludes parameters from outputs`() {
            assertEquals(
                listOf("out.wav"),
                FfmpegArgumentParser.outputFilenames(
                    listOf(
                        "-y",
                        "-i",
                        "input.mp4",
                        "-ar",
                        "16000",
                        "out.wav",
                    )
                )
            )
        }

        @Test
        fun `handles params that don't take an argument`() {
            assertEquals(
                listOf("out.wav"),
                FfmpegArgumentParser.outputFilenames(
                    listOf(
                        "-y",
                        "-i",
                        "input.mp4",
                        "-vn",
                        "-acodec",
                        "libmp3lame",
                        "out.wav",
                    )
                )
            )
        }
    }

    @Nested
    inner class IsFileReference {
        @Test
        fun `returns true for plain filename`() {
            assertEquals(true, FfmpegArgumentParser.isFileReference("cat.mp4"))
        }

        @Test
        fun `returns false for filter source with equals`() {
            assertEquals(false, FfmpegArgumentParser.isFileReference("anullsrc=r=44100"))
        }

        @Test
        fun `returns false for pipe source with colon`() {
            assertEquals(false, FfmpegArgumentParser.isFileReference("pipe:0"))
        }
    }
}
