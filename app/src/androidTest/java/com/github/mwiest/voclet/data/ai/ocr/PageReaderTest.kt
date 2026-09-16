package com.github.mwiest.voclet.data.ai.ocr

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Reads the bench pages on a real device and compares with what the host read.
 *
 * This is the one thing the JVM tests cannot cover: every step they pin is
 * arithmetic, and everything left — decoding the JPEG, scaling the page,
 * warping each quad — is the platform's own pixel handling, which is not
 * OpenCV's. Measured on the host, swapping OpenCV's bicubic warp for the
 * bilinear one `Canvas` does costs exactly one line in 291, and it is a spacing
 * difference. [MIN_EXACT_FRACTION] leaves room for that plus whatever Android's
 * JPEG decoder does differently, which cannot be measured off the device.
 *
 * Fixtures are pushed rather than bundled — 12 MB of weights does not belong in
 * the repo — and live outside the app's own storage so that reinstalling for
 * the next test run does not delete them:
 *
 * ```
 * adb shell mkdir -p /data/local/tmp/voclet-ocr
 * adb push tools/llm-bench/data/ppocr/det.onnx        /data/local/tmp/voclet-ocr/
 * adb push tools/llm-bench/data/ppocr/latin_rec.onnx  /data/local/tmp/voclet-ocr/
 * adb push tools/llm-bench/data/ppocr/latin_dict.txt  /data/local/tmp/voclet-ocr/
 * # the pages as the app would send them: scaled to 1600 px and stood upright,
 * # which is what `ocrbench.py` caches under data/scaled and data/upright
 * adb push <scaled page>.jpg                          /data/local/tmp/voclet-ocr/<name>.jpg
 * adb push app/src/test/resources/ocr/<name>.tsv      /data/local/tmp/voclet-ocr/
 * ```
 *
 * Without them the test skips, the way the bench-backed JVM tests do.
 */
@RunWith(AndroidJUnit4::class)
class PageReaderTest {

    /** How many lines must read exactly as the host read them. */
    private val MIN_EXACT_FRACTION = 0.95

    private val fixtures = File("/data/local/tmp/voclet-ocr")

    @Test
    fun readsTheBenchPagesTheWayTheHostDoes() {
        val detectorModel = File(fixtures, "det.onnx")
        val recognizerModel = File(fixtures, "latin_rec.onnx")
        val dictionary = File(fixtures, "latin_dict.txt")
        assumeTrue(
            "push the models to $fixtures first - see this test's comment",
            detectorModel.isFile && recognizerModel.isFile && dictionary.isFile,
        )

        val pages = fixtures.listFiles { file -> file.name.endsWith(".jpg") }.orEmpty().sorted()
        assumeTrue("no pages pushed to $fixtures", pages.isNotEmpty())

        val reader = PageReader.open(
            detectorModel,
            recognizerModel,
            dictionary.readLines().dropLastWhile { it.isEmpty() },
        )

        val report = StringBuilder()
        var exact = 0
        var total = 0

        reader.use {
            for (page in pages) {
                val name = page.nameWithoutExtension
                val expected = File(fixtures, "$name.tsv")
                assumeTrue("no $name.tsv beside $name.jpg", expected.isFile)

                val bitmap = requireNotNull(BitmapFactory.decodeFile(page.path)) {
                    "could not decode $page"
                }
                val started = System.currentTimeMillis()
                val got = reader.read(bitmap).sortedWith(readingOrder)
                val took = System.currentTimeMillis() - started

                val want = expected.readLines()
                    .filter { it.isNotBlank() }
                    .map { line ->
                        val (text, x, y, width, height) = line.split("\t")
                        TextBox(text, x.toInt(), y.toInt(), width.toInt(), height.toInt())
                    }
                    .sortedWith(readingOrder)

                val matched = want.map { it.text }.toSet()
                    .intersect(got.map { it.text }.toSet()).size
                exact += matched
                total += want.size
                report.append(
                    "\n  %-16s %3dx%-4d  host %3d lines, device %3d, %3d identical, %d ms"
                        .format(name, bitmap.width, bitmap.height, want.size, got.size, matched, took),
                )

                for ((a, b) in want.zip(got)) {
                    if (a.text != b.text) report.append("\n      host %-40s device %s".format(a.text, b.text))
                }
            }
        }

        println("PageReader against the host:$report")
        assertTrue("no lines read at all$report", total > 0)
        assertTrue(
            "only $exact of $total lines match the host, floor is " +
                "${(MIN_EXACT_FRACTION * 100).toInt()}%$report",
            exact >= total * MIN_EXACT_FRACTION,
        )
    }

    @Test
    fun aDictionaryTheModelDisagreesWithIsRejected() {
        val detectorModel = File(fixtures, "det.onnx")
        val recognizerModel = File(fixtures, "latin_rec.onnx")
        val dictionary = File(fixtures, "latin_dict.txt")
        assumeTrue(
            "push the models to $fixtures first",
            detectorModel.isFile && recognizerModel.isFile && dictionary.isFile,
        )
        val pages = fixtures.listFiles { file -> file.name.endsWith(".jpg") }.orEmpty().sorted()
        assumeTrue("no pages pushed to $fixtures", pages.isNotEmpty())

        val short = dictionary.readLines().dropLastWhile { it.isEmpty() }.dropLast(1)
        val bitmap = BitmapFactory.decodeFile(pages.first().path)

        val failure = runCatching {
            PageReader.open(detectorModel, recognizerModel, short).use { it.read(bitmap) }
        }.exceptionOrNull()

        assertEquals(IllegalStateException::class.java, failure?.javaClass)
        assertTrue(
            "unhelpful message: ${failure?.message}",
            failure?.message.orEmpty().contains("dictionary has"),
        )
    }

    /** Top to bottom, then left to right, so two runs line up regardless of order. */
    private val readingOrder = compareBy<TextBox>({ it.y / 10 }, { it.x }, { it.text })
}
