package com.kb129.rssr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kb129.rssr.data.OgImageResolver

/**
 * 記事のサムネイル。
 * フィード内の画像 [feedImage] があればそれを、無ければ記事ページ [pageUrl] の og:image を取りに行く。
 * 読み込み中・画像が無いときは媒体の頭文字を媒体色で表示し、一覧の並びを崩さない。
 */
@Composable
fun Thumbnail(
    feedImage: String?,
    pageUrl: String,
    source: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val imageUrl by produceState(initialValue = feedImage, feedImage, pageUrl) {
        if (value == null && pageUrl.isNotBlank()) value = OgImageResolver.resolve(pageUrl)
    }
    val color = sourceColor(source)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(minOf(8f, size.value / 8f).dp))
            .background(color.copy(alpha = 0.18f)),
    ) {
        Text(
            source.take(1),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp,
        )
        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // 失敗時は透明になり、下の頭文字がそのまま見える
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** 媒体ごとに安定した色を割り当てる (どの媒体が報じているか一目で分かるように)。 */
private val sourcePalette = listOf(
    Color(0xFF1E88E5), Color(0xFFE53935), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFF6D4C41), Color(0xFF3949AB),
)

fun sourceColor(source: String): Color = sourcePalette[Math.floorMod(source.hashCode(), sourcePalette.size)]
