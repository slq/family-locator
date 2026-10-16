package pl.slqpl.rodzina.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import pl.slqpl.rodzina.cloud.MemberAppearance

/** The roster and map share the same marker drawing, including its distinct member number. */
@Composable
fun MemberMarkerImage(appearance: MemberAppearance, modifier: Modifier = Modifier) {
    val bitmap = rememberMemberMarkerBitmap(appearance)
    Image(bitmap.asImageBitmap(), contentDescription = "${appearance.icon.label}, znacznik ${appearance.number}",
        modifier = modifier.width(52.dp).height(64.dp))
}

@Composable
fun rememberMemberMarkerBitmap(appearance: MemberAppearance, stale: Boolean = false): Bitmap {
    val density = LocalDensity.current.density
    return remember(appearance, density, stale) { drawMemberMarker(appearance, density, stale) }
}

private fun drawMemberMarker(appearance: MemberAppearance, density: Float, stale: Boolean): Bitmap {
    val bitmap = createBitmap((52 * density).toInt().coerceAtLeast(1), (64 * density).toInt().coerceAtLeast(1))
    val canvas = Canvas(bitmap)
    canvas.scale(density, density)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val pin = Path().apply {
        moveTo(15f, 39f); lineTo(26f, 62f); lineTo(37f, 39f); close()
    }
    paint.color = Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 4f
    canvas.drawPath(pin, paint)
    canvas.drawCircle(26f, 26f, 23f, paint)
    paint.style = Paint.Style.FILL
    paint.color = appearance.colorArgb
    canvas.drawPath(pin, paint)
    canvas.drawCircle(26f, 26f, 23f, paint)
    paint.color = Color.WHITE
    canvas.drawCircle(26f, 26f, 19.5f, paint)
    paint.color = Color.BLACK
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT
    paint.textSize = 29f
    canvas.drawText(appearance.icon.glyph, 26f, 26f - (paint.ascent() + paint.descent()) / 2, paint)
    // An explicit choice may repeat a symbol. The number still identifies each person uniquely.
    val number = appearance.number.toString()
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 11f
    val badgeWidth = maxOf(18f, paint.measureText(number) + 8f)
    paint.color = Color.WHITE
    canvas.drawRoundRect(52 - badgeWidth, 1f, 52f, 19f, 9f, 9f, paint)
    paint.color = appearance.colorArgb
    canvas.drawText(number, 52 - badgeWidth / 2, 10f - (paint.ascent() + paint.descent()) / 2, paint)
    if (stale) {
        paint.color = Color.rgb(152, 96, 0)
        canvas.drawCircle(10f, 47f, 9f, paint)
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f
        canvas.drawCircle(10f, 47f, 6f, paint)
        canvas.drawLine(10f, 43f, 10f, 47f, paint)
        canvas.drawLine(10f, 47f, 13f, 49f, paint)
        paint.style = Paint.Style.FILL
    }
    return bitmap
}
