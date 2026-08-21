package `in`.pcncloud.hotel.ui.components

import android.content.Context
import android.util.Log
import coil.request.ImageRequest

/**
 * Coil request used for hotel logo / wallpaper URLs from Firestore.
 *
 * Android TV hardware bitmaps + CDN 403s (ImgBB, Wikimedia) previously left
 * the flavor gold flower in place even when [Hotels/{id}.logoUrl] was set.
 */
fun hotelImageRequest(
    context: Context,
    url: String,
    logTag: String,
): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .crossfade(true)
        .allowHardware(false)
        .listener(
            onSuccess = { _, _ ->
                Log.i(logTag, "Remote image loaded OK")
            },
            onError = { _, result ->
                Log.e(
                    logTag,
                    "Remote image FAILED url=${url.take(160)}: ${result.throwable.message}",
                    result.throwable,
                )
            },
        )
        .build()
