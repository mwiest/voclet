package com.github.mwiest.voclet.data.ai.ocr

import java.io.Closeable
import java.io.File

/**
 * One ncnn model, loaded from a `.param` / `.bin` pair.
 *
 * The image crosses the boundary as raw RGB and ncnn normalizes it, because
 * that is the path measured against onnxruntime on the host: the converted
 * models reproduce the ONNX detector's boxes exactly and all 293 recognizer
 * lines. Handing a float tensor over instead would be four times the copy and
 * a second place for the normalization to drift.
 */
class NcnnNet private constructor(private var handle: Long) : Closeable {

    /** A net's output: [values] laid out channel by channel, row-major. */
    class Output(val values: FloatArray, val width: Int, val height: Int, val channels: Int) {
        /** The value at ([x], [y]) of [channel]. */
        operator fun get(channel: Int, y: Int, x: Int): Float =
            values[channel * width * height + y * width + x]
    }

    /**
     * Runs one RGB image, [width] x [height], three interleaved bytes a pixel.
     *
     * [mean] and [norm] are ncnn's own convention: the result is
     * `(value - mean) * norm`, per channel.
     */
    fun run(rgb: ByteArray, width: Int, height: Int, mean: FloatArray, norm: FloatArray): Output {
        check(handle != 0L) { "net is closed" }
        require(rgb.size == width * height * 3) {
            "expected ${width * height * 3} bytes for ${width}x$height, got ${rgb.size}"
        }
        val shape = IntArray(3)
        val values = nativeRun(handle, rgb, width, height, mean, norm, shape)
            ?: error("ncnn refused a ${width}x$height input")
        return Output(values, shape[0], shape[1], shape[2])
    }

    override fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    companion object {
        init {
            System.loadLibrary("voclet_ocr")
        }

        fun open(param: File, bin: File): NcnnNet {
            require(param.isFile) { "no ncnn param file at $param" }
            require(bin.isFile) { "no ncnn weights at $bin" }
            val handle = nativeOpen(param.path, bin.path)
            check(handle != 0L) { "ncnn could not load $param" }
            return NcnnNet(handle)
        }

        @JvmStatic
        private external fun nativeOpen(param: String, bin: String): Long

        @JvmStatic
        private external fun nativeClose(handle: Long)

        @JvmStatic
        private external fun nativeRun(
            handle: Long,
            rgb: ByteArray,
            width: Int,
            height: Int,
            mean: FloatArray,
            norm: FloatArray,
            outShape: IntArray,
        ): FloatArray?
    }
}
