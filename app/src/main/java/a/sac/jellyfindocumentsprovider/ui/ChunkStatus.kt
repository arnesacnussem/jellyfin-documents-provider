package a.sac.jellyfindocumentsprovider.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class ChunkStatus(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val paint = Paint()
    var chunksStatusList: List<String> = listOf()
        set(value) {
            field = value
            invalidate() // Redraw the view after setting a new list of statuses
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val squareSize = width / chunksStatusList.size.toFloat()
        chunksStatusList.forEachIndexed { index, status ->
            paint.color = when (status) {
                "pending" -> Color.YELLOW
                "running" -> Color.BLUE
                "ok" -> Color.GREEN
                else -> Color.GRAY
            }
            canvas.drawRect(
                index * squareSize,
                0f,
                (index + 1) * squareSize,
                height.toFloat(),
                paint
            )
        }
    }
}