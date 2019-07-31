package quevedo.soares.leandro.lib.util

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * @author Leandro Soares Quevedo
 * @author leandrosoaresquevedo@gmail.com
 * @since 2019-07-22
 */
fun lerp(current: Float, target: Float, initialStep: Float): Float {
	val step = if (initialStep < 0.1f) 0.1f else initialStep

	val diff = target - current

	return when {
		diff >= step -> step
		-step > diff -> -step
		else -> diff
	}
}

fun lerp(current: PointF, target: PointF, step: Float = 10f): PointF {
	return PointF(
		current.x + lerp(current.x, target.x, abs(target.x - current.x) / step),
		current.y + lerp(current.y, target.y, abs(target.y - current.y) / step)
	)
}

fun distance(x1: Float, y1: Float, x2: Int, y2: Int): Float {
	return sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1))
}