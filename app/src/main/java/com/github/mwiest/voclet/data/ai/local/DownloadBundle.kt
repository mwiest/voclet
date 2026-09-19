package com.github.mwiest.voclet.data.ai.local

import com.github.mwiest.voclet.data.ai.ocr.PageReaderModels

/**
 * One file inside a [DownloadBundle].
 *
 * [sizeBytes] is the exact on-disk size, not an estimate: it is what the user
 * is shown before committing to a download on mobile data, and what weights
 * progress across a bundle's files.
 */
data class BundleFile(
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
)

/**
 * A set of files that is downloaded, kept and deleted as one thing.
 *
 * The download machinery was originally written against [AiModel], which meant
 * "weights, plus a projector for the vision models that have one". The OCR page
 * reader is four files and no language model at all — no tier, no RAM gate, no
 * prompt format — so widening [AiModel] to cover it would have made the model
 * catalog describe something that is not a model. This is the narrower thing
 * the machinery actually needs: an id, a name to put in a notification, and
 * some files to fetch.
 */
interface DownloadBundle {
    /** Unique across every bundle; names the WorkManager job and the status map. */
    val id: String

    /** Shown in the download notification. A model name, so not translated. */
    val displayName: String

    val files: List<BundleFile>

    /** What the whole download costs. */
    val totalSizeBytes: Long get() = files.sumOf { it.sizeBytes }
}

/**
 * Every bundle the app can download.
 *
 * [ModelRepository] watches all of these, and [ModelDownloadWorker] resolves an
 * id back to a bundle, so anything downloadable has to be reachable from here.
 */
object DownloadCatalog {

    val ALL: List<DownloadBundle> get() = AiModel.ALL + PageReaderModels

    fun byId(id: String): DownloadBundle? = ALL.firstOrNull { it.id == id }
}
