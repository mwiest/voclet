package com.github.mwiest.voclet.ui.components

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a multi-frame image resource (animated WebP, GIF) and loops it forever.
 *
 * Compose has no painter for animated images, so the platform decoder produces an
 * [AnimatedImageDrawable] which is hosted in an [ImageView]. Decoding happens off the main
 * thread. Note that [AnimatedImageDrawable] needs hardware acceleration, so the animation
 * stands still in `@Preview`.
 */
@Composable
fun AnimatedImage(
    @DrawableRes resId: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(initialValue = null, resId, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, resId))
            }.getOrNull()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { view ->
            view.contentDescription = contentDescription
            if (view.drawable !== drawable) {
                view.setImageDrawable(drawable)
                (drawable as? AnimatedImageDrawable)?.apply {
                    repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    start()
                }
            }
        }
    )
}
