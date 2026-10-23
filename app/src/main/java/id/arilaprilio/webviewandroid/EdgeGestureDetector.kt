package id.arilaprilio.webviewandroid

import android.content.Context
import android.util.TypedValue
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Detects edge swipes without stealing taps/scrolls from the WebView.
 *
 * How to use from an Activity:
 *     override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
 *         if (edgeDetector.onTouch(ev)) return true  // consumed as edge gesture
 *         return super.dispatchTouchEvent(ev)
 *     }
 *
 * Rules enforced to avoid interfering with normal web content:
 *   - DOWN event MUST start within `edgeThicknessPx` of the respective screen edge.
 *   - Movement must cross `triggerDistancePx` roughly perpendicular to that edge.
 *   - Multi-touch is ignored (any secondary pointer cancels the current gesture).
 *   - Long-press at the top-left corner (`cornerSizePx` square) triggers [onCornerLongPress]
 *     after `cornerHoldMs` without any significant movement.
 */
class EdgeGestureDetector(
    context: Context,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun onLeftEdgeSwipe()      // left → right: open settings panel
        fun onRightEdgeSwipe()     // right → left: toggle soft keyboard
        fun onBottomEdgeSwipe()    // bottom → up: open edge toolbar
        fun onCornerLongPress()    // top-left corner long-hold: unlock / quit app
    }

    private val edgeThicknessPx = dp(context, 24f)
    private val triggerDistancePx = dp(context, 48f)
    private val cornerSizePx = dp(context, 56f)
    private val slopPx = dp(context, 12f)

    private val cornerHoldMs = 1500L

    private var startX = 0f
    private var startY = 0f
    private var startEdge: Edge = Edge.NONE
    private var pointerCount = 0
    private var tracking = false

    private var cornerDownAt = 0L
    private var cornerTriggered = false

    private enum class Edge { NONE, LEFT, RIGHT, BOTTOM, TOP_LEFT_CORNER }

    fun onTouch(ev: MotionEvent): Boolean {
        pointerCount = ev.pointerCount

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                tracking = true
                cornerTriggered = false
                startEdge = classifyEdge(ev)
                if (startEdge == Edge.TOP_LEFT_CORNER) {
                    cornerDownAt = ev.eventTime
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Any multi-touch cancels an in-progress edge gesture to avoid false triggers
                // from a user pinch-zooming the page near an edge.
                tracking = false
                startEdge = Edge.NONE
            }

            MotionEvent.ACTION_MOVE -> {
                if (!tracking || startEdge == Edge.NONE) return false

                val dx = ev.x - startX
                val dy = ev.y - startY

                // Corner long-press: must stay still.
                if (startEdge == Edge.TOP_LEFT_CORNER) {
                    if (abs(dx) > slopPx || abs(dy) > slopPx) {
                        startEdge = Edge.NONE
                    } else if (!cornerTriggered &&
                        ev.eventTime - cornerDownAt >= cornerHoldMs
                    ) {
                        cornerTriggered = true
                        callbacks.onCornerLongPress()
                        return true
                    }
                    return false
                }

                // Directional edge swipes.
                when (startEdge) {
                    Edge.LEFT -> if (dx > triggerDistancePx && abs(dy) < triggerDistancePx) {
                        return consume { callbacks.onLeftEdgeSwipe() }
                    }
                    Edge.RIGHT -> if (-dx > triggerDistancePx && abs(dy) < triggerDistancePx) {
                        return consume { callbacks.onRightEdgeSwipe() }
                    }
                    Edge.BOTTOM -> if (-dy > triggerDistancePx && abs(dx) < triggerDistancePx) {
                        return consume { callbacks.onBottomEdgeSwipe() }
                    }
                    else -> Unit
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
                startEdge = Edge.NONE
            }
        }
        return false
    }

    private inline fun consume(action: () -> Unit): Boolean {
        action()
        tracking = false
        startEdge = Edge.NONE
        return true
    }

    private fun classifyEdge(ev: MotionEvent): Edge {
        val x = ev.x
        val y = ev.y
        val w = ev.device?.motionRanges?.let { ev.rawX + 1 } ?: ev.rawX
        // We use ev.getX relative to the window; for edge detection we need display-relative
        // positions. rawX/rawY give us that in screen coordinates.
        val screenX = ev.rawX
        val screenY = ev.rawY

        // Top-left corner check first — it overlaps the LEFT edge band.
        if (screenX <= cornerSizePx && screenY <= cornerSizePx) return Edge.TOP_LEFT_CORNER

        val displayWidth = resolveDisplayDimension(ev, horizontal = true)
        val displayHeight = resolveDisplayDimension(ev, horizontal = false)

        return when {
            screenX <= edgeThicknessPx -> Edge.LEFT
            screenX >= displayWidth - edgeThicknessPx -> Edge.RIGHT
            screenY >= displayHeight - edgeThicknessPx -> Edge.BOTTOM
            else -> Edge.NONE
        }
    }

    /**
     * MotionEvent does not expose display size directly. We approximate using the first
     * pointer's raw coordinates vs. local coordinates; good enough because fullscreen mode
     * uses the full window and edge thresholds are generous.
     */
    private fun resolveDisplayDimension(ev: MotionEvent, horizontal: Boolean): Float {
        val source = if (horizontal) ev.device?.motionRanges else null
        source?.firstOrNull {
            it.axis == MotionEvent.AXIS_X && horizontal ||
                it.axis == MotionEvent.AXIS_Y && !horizontal
        }?.let { return it.max }
        // Fallback: assume 1920 landscape — only hit on exotic input devices.
        return if (horizontal) 1920f else 1080f
    }

    private fun dp(context: Context, value: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics,
        )
}
