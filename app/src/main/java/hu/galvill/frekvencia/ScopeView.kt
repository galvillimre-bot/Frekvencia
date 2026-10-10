package hu.galvill.frekvencia

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/** Egyszerű oszcilloszkóp az Engine pufferéből */
class ScopeView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val grid = Paint().apply {
        color = 0x33B7902E
        strokeWidth = 1f
    }
    private val trace = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0B64A.toInt()
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 0..10) {
            val x = w * i / 10f
            canvas.drawLine(x, 0f, x, h, grid)
        }
        canvas.drawLine(0f, h / 2, w, h / 2, grid)

        val buf = Engine.scope
        val n = buf.size
        val start = Engine.scopePos
        path.reset()
        for (i in 0 until n) {
            val v = buf[(start + i) % n].coerceIn(-1.2f, 1.2f)
            val x = w * i / (n - 1)
            val y = h / 2 - v * h * 0.42f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, trace)

        if (Engine.running) postInvalidateOnAnimation()
    }
}
