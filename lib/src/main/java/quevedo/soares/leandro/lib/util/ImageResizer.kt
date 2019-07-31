package quevedo.soares.leandro.lib.util

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.SizeF
import kotlin.math.max
import kotlin.math.min

/**
 * @author Leandro Soares Quevedo
 * @author leandro.soares@operacao.rcadigital.com.br
 * @since 2019-07-30
 */
data class ImageSizeInfo(
	val originalImage: Bitmap,
	val scaledImage: Bitmap,
	val imageRect: RectF,
	val cropRect: Rect
)

data class CropSizeInfo(
	val isMaxSet: Boolean,
	val isMinSet: Boolean,
	val minWidth: Int,
	val maxWidth: Int,
	val minHeight: Int,
	val maxHeight: Int
)

internal class ImageResizer(
	screenDensity: Float,
	private val source: Bitmap,
	private val canvas: SizeF,
	private val cropSizeInfo: CropSizeInfo
) {

	// Create the temporary variables
	private var imageFinalX = 0f
	private var imageFinalY = 0f
	private var imageFinalW = 0f
	private var imageFinalH = 0f
	private val imagePadding = 7f * screenDensity

	private fun clampHorizontalImages() {
		if (source.width > canvas.width) {
			// If the image's width is greather than the view's width, resize it to fit
			imageFinalW = canvas.width
			imageFinalH = (imageFinalW * source.height) / source.width
			imageFinalY = (canvas.height - imageFinalH) / 2f + imageFinalH / 4f
		} else {
			// Just center the image horizontally
			imageFinalX = max(0f, (canvas.width - source.width) / 2f)
			imageFinalW = source.width.toFloat()

			if (source.height > canvas.height) {
				// If the image's height is greather than the view's height, resize it to fit
				imageFinalH = canvas.width
				imageFinalW = (imageFinalH * source.width) / source.height
			} else {
				// Just center the image vertically
				imageFinalY = (canvas.height - source.height) / 2f + (source.height / 2f)
				imageFinalH = source.height.toFloat()
			}
		}
	}

	private fun clampVerticalImages() {
		if (source.height >= canvas.height) {
			// If the image's width is greather than the view's width, resize it to fit
			imageFinalH = canvas.height
			imageFinalW = min((imageFinalH * source.width) / source.height, canvas.width)
			imageFinalX = max(0f, (canvas.width - imageFinalW) / 2f)
		} else {
			// Just center the image vertically
			imageFinalH = source.height.toFloat()
			imageFinalY = max(0f, (canvas.height - imageFinalH) / 2f)

			if (source.width > canvas.width) {
				// If the image's height is greather than the view's height, resize it to fit
				imageFinalW = canvas.height
				imageFinalH = (imageFinalW * source.height) / source.width
			} else {
				// Just center the image horizontally
				imageFinalW = source.width.toFloat()
				imageFinalX = max(0f, (canvas.width - imageFinalW) / 2f)
			}
		}
	}

	private fun appendPadding() {
		imageFinalX += imagePadding
		imageFinalY += imagePadding
		imageFinalW -= imagePadding * 2
		imageFinalH -= imagePadding * 2
	}

	fun shrinkImageToFit(): ImageSizeInfo {
		// For the ladscape images
		if (source.width > source.height) {
			this.clampHorizontalImages()
		} else {
			this.clampVerticalImages()
		}

		// Adds the padding to the rectangle
		this.appendPadding()

		// Creates a rectangle from the image
		val outputRect = RectF(
			imageFinalX,
			imageFinalY,
			imageFinalX + imageFinalW,
			imageFinalY + imageFinalH
		)

		// Calculate the scale from the original image size
		val scaledBitmap = Bitmap.createScaledBitmap(
			source,
			outputRect.width().toInt(),
			outputRect.height().toInt(),
			true
		)

		val outputCropRect = createCropRectangle(outputRect)

		return ImageSizeInfo(
			source,
			scaledBitmap,
			outputRect,
			outputCropRect
		)
	}

	private fun createCropRectangle(outputRect: RectF): Rect {
		val cropRect = Rect()

		val imageCenter = PointF(
			outputRect.centerX(),
			outputRect.centerY()
		)

		when {
			cropSizeInfo.isMaxSet -> {
				cropRect.left = (imageCenter.x - cropSizeInfo.maxWidth / 2).toInt()
				cropRect.right = (cropRect.left + cropSizeInfo.maxWidth)

				cropRect.top = (imageCenter.y - cropSizeInfo.maxHeight / 2).toInt()
				cropRect.bottom = (cropRect.top + cropSizeInfo.maxHeight)
			}
			cropSizeInfo.isMinSet -> {
				cropRect.left = (imageCenter.x - cropSizeInfo.minWidth / 2).toInt()
				cropRect.right = (cropRect.left + cropSizeInfo.minWidth)

				cropRect.top = (imageCenter.y - cropSizeInfo.minHeight / 2).toInt()
				cropRect.bottom = (cropRect.top + cropSizeInfo.minHeight)
			}
			else -> {
				cropRect.left = outputRect.left.toInt()
				cropRect.right = outputRect.right.toInt()

				cropRect.top = outputRect.top.toInt()
				cropRect.bottom = outputRect.bottom.toInt()
			}
		}

		return cropRect
	}

}