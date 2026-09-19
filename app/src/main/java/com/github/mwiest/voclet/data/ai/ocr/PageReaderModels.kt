package com.github.mwiest.voclet.data.ai.ocr

import com.github.mwiest.voclet.data.ai.local.BundleFile
import com.github.mwiest.voclet.data.ai.local.DownloadBundle

/**
 * The two PP-OCRv5 models [PageReader] needs, as one download.
 *
 * Not an `AiModel`: nothing here is a language model. There is no tier to
 * choose, no RAM gate to clear and no prompt format — one download, and either
 * the app can read a photo or it cannot.
 *
 * **These files are converted**, by `pnnx` from the ONNX weights PaddlePaddle
 * publishes, so they cannot be fetched from PaddlePaddle's own repo. They are
 * hosted as a GitHub release on this project, tagged independently of the app
 * so that shipping a new version of Voclet does not mean re-uploading 12.7 MB.
 * Sizes are the exact bytes of the published assets.
 *
 * The character dictionary is deliberately *not* here: at 3.4 KB it ships in
 * `assets`, because recovering it on device would mean parsing PaddleOCR's YAML.
 * [PageReader] checks it against the model's own class count before reading
 * anything, which is what catches the two drifting apart.
 */
object PageReaderModels : DownloadBundle {

    private const val BASE =
        "https://github.com/mwiest/voclet/releases/download/ocr-models-v1/"

    override val id = "ppocrv5-latin"

    /** Shown in the download notification, beside model names like "LFM2 700M". */
    override val displayName = "PP-OCRv5"

    val detectorParam = BundleFile(BASE + "det.ncnn.param", "det.ncnn.param", 24_256L)
    val detectorWeights = BundleFile(BASE + "det.ncnn.bin", "det.ncnn.bin", 4_685_856L)
    val recognizerParam = BundleFile(BASE + "latin_rec.ncnn.param", "latin_rec.ncnn.param", 19_648L)
    val recognizerWeights = BundleFile(BASE + "latin_rec.ncnn.bin", "latin_rec.ncnn.bin", 7_949_480L)

    /** Weights first, so the progress bar spends its time where the bytes are. */
    override val files = listOf(
        detectorWeights,
        recognizerWeights,
        detectorParam,
        recognizerParam,
    )
}
