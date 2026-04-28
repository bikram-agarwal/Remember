package dev.bikram.remember.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders the app icon like the launcher preview (including adaptive icons on API 26+).
 *
 * The bitmap rasterization runs on a background dispatcher to keep the first frame jank-free.
 * Until it is ready, an empty Box with the requested modifier is shown so the layout slot is
 * already reserved.
 */
@Composable
fun AppIconImage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val packageName = context.applicationContext.packageName
    val packageManager = context.packageManager
    val imageBitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value =
            withContext(Dispatchers.Default) {
                val drawable = packageManager.getApplicationIcon(packageName)
                val size = 256
                val bitmap =
                    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
                        val canvas = Canvas(bmp)
                        drawable.setBounds(0, 0, size, size)
                        drawable.draw(canvas)
                    }
                bitmap.asImageBitmap()
            }
    }
    val bitmap = imageBitmap
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier)
    }
}
