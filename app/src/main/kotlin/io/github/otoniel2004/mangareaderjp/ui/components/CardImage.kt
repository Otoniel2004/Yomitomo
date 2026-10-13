package io.github.otoniel2004.mangareaderjp.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The image saved with a card (a photo crop or a manga bubble), loaded off the main thread. */
@Composable
fun CardImage(path: String, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    }
    image?.let {
        Image(
            bitmap = it,
            contentDescription = "Image saved with this card",
            contentScale = ContentScale.Fit,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}
