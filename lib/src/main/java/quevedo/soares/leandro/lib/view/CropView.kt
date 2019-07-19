package quevedo.soares.leandro.lib.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.util.SizeF
import android.view.MotionEvent
import android.widget.FrameLayout
import quevedo.soares.leandro.lib.enumerator.DragType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * @author Leandro Soares Quevedo
 * @author leandro.soares@operacao.rcadigital.com.br
 * @since 2019-07-12
 */
class CropView : FrameLayout {

    var image: Bitmap? = null
    var imageRect: RectF = RectF()
    var imageMatrix: Matrix = Matrix()
	var scaledImage: Bitmap? = null

    var imageResized: Boolean = false

    var cropRect: Rect = Rect(256, 256, 256 + 256, 256 + 256)
    private lateinit var cropRectPaint: Paint
    private lateinit var cropRectLinePaint: Paint
    private lateinit var cropRectCornerPaint: Paint
    private lateinit var cropRectMaskPaint: Paint

    var pointerCount: Int = 0
    var dragging: Boolean = false
    var activePointerIndex: Int = -1

    var rectMinWidth = 128
    var rectMinHeight = 128
    var rectMaxWidth = 0
    var rectMaxHeight = 0
    private val hasMinimumSet: Boolean by lazy { rectMinWidth != 0 && rectMinHeight != 0 }
    private val hasMaximumSet: Boolean by lazy { rectMaxWidth != 0 && rectMaxHeight != 0 }

    var dragType: DragType? = null
    lateinit var lastPosition: PointF

	private var canvasTransform = Matrix()
	var scaleFactor = 1f

    //<editor-fold defaultstate="Collapsed" desc="Constructors">

    constructor(context: Context) : super(context) {
        this.onInitialized()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        this.onInitialized()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        this.onInitialized()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    ) {
        this.onInitialized()
    }

    //</editor-fold>

    private fun onInitialized() {
        // Default custom view configuration
        this.setWillNotDraw(false)

        // Android click enable stuff
        this.isFocusable = true
        this.isFocusableInTouchMode = true
        this.isClickable = true

        // Measure this view, so we'll know it's size
        this.measure(0, 0)

        this.setupPaintObjects()

        // Redirect the onTouchListener to the onTouch(event: MotionEvent)
        setOnTouchListener { _, event ->
            event?.let {
                onTouch(it)
            }

            return@setOnTouchListener true
        }
    }

    private fun setupPaintObjects() {
        this.cropRectPaint = Paint()
        this.cropRectPaint.color = Color.WHITE
        this.cropRectPaint.style = Paint.Style.STROKE
        this.cropRectPaint.strokeWidth = 1.5f * resources.displayMetrics.density
        this.cropRectPaint.strokeJoin = Paint.Join.MITER
        this.cropRectPaint.strokeCap = Paint.Cap.BUTT
        this.cropRectPaint.isAntiAlias = true

        this.cropRectLinePaint = Paint(this.cropRectPaint)
        this.cropRectLinePaint.strokeWidth = 1f * resources.displayMetrics.density
		this.cropRectLinePaint.color = Color.argb(255, 200, 200, 200)

        this.cropRectCornerPaint = Paint(this.cropRectLinePaint)
		this.cropRectCornerPaint.strokeWidth = 2.5f * resources.displayMetrics.density
        this.cropRectCornerPaint.style = Paint.Style.FILL
        /*this.cropRectCornerPaint.colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -1.0f, 0f, 0f, 0f, 255f, // Red
                    0f, -1.0f, 0f, 0f, 255f, // Green
                    0f, 0f, -1.0f, 0f, 255f, // Blue
                    0f, 0f, 0f, 1.0f, 0f // Alpha
                )
            )
        )*/
		this.cropRectCornerPaint.color = Color.argb(255, 255, 255, 255)

        this.cropRectMaskPaint = Paint()
        this.cropRectMaskPaint.color = Color.BLACK
        this.cropRectMaskPaint.alpha = 150
        this.cropRectMaskPaint.style = Paint.Style.FILL
    }

    private fun onTouch(event: MotionEvent) {
        val pointer = event.getPointerId(0)

        when (event.action.and(MotionEvent.ACTION_MASK)) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                Log.d("EasyImagePicker", "New action down -> $pointerCount pointers active")
                pointerCount++

                if (!dragging) {
                    activePointerIndex = pointer
                    this.lastPosition = PointF(event.getX(activePointerIndex), event.getY(activePointerIndex))
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                Log.d("EasyImagePicker", "New action up -> $pointerCount pointers active")
                pointerCount--

                if (activePointerIndex == pointer) {
                    // Resets the dragging indicators
                    dragging = false
                    activePointerIndex = -1

                    // If we were dragging
                    if (this.dragType != null) {
                        this.onDragEnd()
                    }

                    // Resets the drag type identifier
                    dragType = null
                }
            }
            MotionEvent.ACTION_MOVE -> {
                Log.d("EasyImagePicker", "Action move from pointer $pointer")

                if (activePointerIndex == pointer) {
                    dragging = true
                } else return

                val position = PointF(event.getX(activePointerIndex), event.getY(activePointerIndex))

                if (this.dragType == null) {
                    onDragStart()
                } else {
                    onDragUpdate(position)
                }

                this.lastPosition = position

                invalidate()
            }
            else -> {

            }
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Int, y2: Int): Float {
        return sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1))
    }

    private fun onDragStart() {
        Log.d("EasyImagePicker", "InitDrag")

        val cornerSize = calculateCornerSize()
        val threshold = (cornerSize.width + cornerSize.height) / 2f

        // Diagonals
        if (distance(this.lastPosition.x, this.lastPosition.y, cropRect.left, cropRect.top) <= threshold) {
            // Top left box
            this.dragType = DragType.TopLeft
        } else if (distance(lastPosition.x, lastPosition.y, cropRect.left, cropRect.bottom) <= threshold) {
            // Bottom left box
            this.dragType = DragType.BottomLeft
        } else if (distance(lastPosition.x, lastPosition.y, cropRect.right, cropRect.top) <= threshold) {
            // Top right box
            this.dragType = DragType.TopRight
        } else if (distance(lastPosition.x, lastPosition.y, cropRect.right, cropRect.bottom) <= threshold) {
            // Bottom right box
            this.dragType = DragType.BottomRight
        } else {
            val halfThreshold = threshold * 1.5f

            if (abs(lastPosition.y - cropRect.top) <= threshold && lastPosition.x >= cropRect.left + halfThreshold && lastPosition.x <= cropRect.right - halfThreshold) {
                // Top
                this.dragType = DragType.Top
            } else if (abs(lastPosition.y - cropRect.bottom) <= threshold && lastPosition.x >= cropRect.left + halfThreshold && lastPosition.x <= cropRect.right - halfThreshold) {
                // Bottom
                this.dragType = DragType.Bottom
            } else if (abs(lastPosition.x - cropRect.left) <= threshold && lastPosition.y >= cropRect.top + halfThreshold && lastPosition.y <= cropRect.bottom - halfThreshold) {
                // Left
                this.dragType = DragType.Left
            } else if (abs(lastPosition.x - cropRect.right) <= threshold && lastPosition.y >= cropRect.top + halfThreshold && lastPosition.y <= cropRect.bottom - halfThreshold) {
                // Right
                this.dragType = DragType.Right
            } else if (lastPosition.x >= cropRect.left + threshold && lastPosition.x <= cropRect.right - threshold && lastPosition.y >= cropRect.top + halfThreshold && lastPosition.y <= cropRect.bottom - halfThreshold) {
                // Center
                this.dragType = DragType.Center
            }
        }

        // When a fixed size is provided, ignore any corners and only allow dragging
        if (this.dragType != null && this.hasMinimumSet && this.hasMaximumSet && this.rectMinHeight == this.rectMaxHeight && this.rectMinWidth == this.rectMaxWidth) {
            // Center
            this.dragType = DragType.Center
        }

    }

    private fun onDragUpdate(position: PointF) {
        Log.d("EasyImagePicker", "HandleDrag")

        when (this.dragType!!) {
            DragType.Center -> {
                Log.d("EasyImagePicker", "HandleDrag - Top")

                val diff = PointF(position.x - lastPosition.x, position.y - lastPosition.y)
                this.cropRect.top += diff.y.toInt()
                this.cropRect.left += diff.x.toInt()
                this.cropRect.bottom += diff.y.toInt()
                this.cropRect.right += diff.x.toInt()
            }
            DragType.Top -> {
                Log.d("EasyImagePicker", "HandleDrag - Top")
                // Sets the crop rect top to follow the touch position (And clamps it to the image top)
                this.cropRect.top = max(this.imageRect.top, position.y).toInt()

                // Clipping
                if (hasMinimumSet && cropRect.height() < rectMinHeight) {
                    this.cropRect.top = cropRect.bottom - rectMinHeight
                } else if (hasMaximumSet && cropRect.height() > rectMaxHeight) {
                    this.cropRect.top = cropRect.bottom - rectMaxHeight
                }
            }
            DragType.Bottom -> {
                Log.d("EasyImagePicker", "HandleDrag - Bottom")
                // Sets the crop rect bottom to follow the touch position (And clamps it to the image bottom)
                this.cropRect.bottom = min(this.imageRect.bottom, position.y).toInt()

                // Clipping
                if (hasMinimumSet && cropRect.height() < rectMinHeight) {
                    this.cropRect.bottom = cropRect.top + rectMinHeight
                } else if (hasMaximumSet && cropRect.height() > rectMaxHeight) {
                    this.cropRect.bottom = cropRect.top + rectMaxHeight
                }
            }
            DragType.Left -> {
                Log.d("EasyImagePicker", "HandleDrag - Left")
                // Sets the crop rect left to follow the touch position (And clamps it to the image left)
                this.cropRect.left = max(this.imageRect.left, position.x).toInt()

                // Clipping
                if (hasMinimumSet && cropRect.width() < rectMinWidth) {
                    this.cropRect.left = cropRect.right - rectMinWidth
                } else if (hasMaximumSet && cropRect.width() > rectMaxWidth) {
                    this.cropRect.left = cropRect.right - rectMaxWidth
                }
            }
            DragType.Right -> {
                Log.d("EasyImagePicker", "HandleDrag - Right")
                // Sets the crop rect right to follow the touch position (And clamps it to the image right)
                this.cropRect.right = min(this.imageRect.right, position.x).toInt()

                // Clipping
                if (hasMinimumSet && cropRect.width() < rectMinWidth) {
                    this.cropRect.right = cropRect.left + rectMinWidth
                } else if (hasMaximumSet && cropRect.width() > rectMaxWidth) {
                    this.cropRect.right = cropRect.left + rectMaxWidth
                }
            }
            DragType.TopLeft -> {
                Log.d("EasyImagePicker", "HandleDrag - TopLeft")
                // Sets the crop rect top to follow the touch position (And clamps it to the image top)
                this.cropRect.top = max(this.imageRect.top, position.y).toInt()
                // Sets the crop rect left to follow the touch position (And clamps it to the image left)
                this.cropRect.left = max(this.imageRect.left, position.x).toInt()

                // Clipping
                if (hasMinimumSet && cropRect.width() < rectMinWidth) {
                    this.cropRect.left = cropRect.right - rectMinWidth
                } else if (hasMaximumSet && cropRect.width() > rectMaxWidth) {
                    this.cropRect.left = cropRect.right - rectMaxWidth
                }
                if (hasMinimumSet && cropRect.height() < rectMinHeight) {
                    this.cropRect.top = cropRect.bottom - rectMinHeight
                } else if (hasMaximumSet && cropRect.height() > rectMaxHeight) {
                    this.cropRect.top = cropRect.bottom - rectMaxHeight
                }
            }
            DragType.TopRight -> {
                Log.d("EasyImagePicker", "HandleDrag - TopRight")
                // Sets the crop rect top to follow the touch position (And clamps it to the image top)
                this.cropRect.top = max(this.imageRect.top, position.y).toInt()
                // Sets the crop rect right to follow the touch position (And clamps it to the image right)
                this.cropRect.right = min(this.imageRect.right, position.x).toInt()

                // Clipping
                if (hasMinimumSet && cropRect.width() <= rectMinWidth) {
                    this.cropRect.right = cropRect.left + rectMinWidth
                } else if (hasMaximumSet && cropRect.width() > rectMaxWidth) {
                    this.cropRect.right = cropRect.left + rectMaxWidth
                }
                if (hasMinimumSet && cropRect.height() <= rectMinHeight) {
                    this.cropRect.top = cropRect.bottom - rectMinHeight
                } else if (hasMaximumSet && cropRect.height() > rectMaxHeight) {
                    this.cropRect.top = cropRect.bottom - rectMaxHeight
                }
            }
            DragType.BottomLeft -> {
                Log.d("EasyImagePicker", "HandleDrag - BottomLeft")
                // Sets the crop rect bottom to follow the touch position (And clamps it to the image bottom)
                this.cropRect.bottom = min(this.imageRect.bottom, position.y).toInt()
                // Sets the crop rect left to follow the touch position (And clamps it to the image left)
                this.cropRect.left = max(this.imageRect.left, position.x).toInt()

                // Clipping
                if (hasMinimumSet && cropRect.width() <= rectMinWidth) {
                    this.cropRect.left = cropRect.right - rectMinWidth
                } else if (hasMaximumSet && cropRect.width() > rectMaxWidth) {
                    this.cropRect.right = cropRect.left - rectMaxWidth
                }
                if (hasMinimumSet && cropRect.height() <= rectMinHeight) {
                    this.cropRect.bottom = cropRect.top + rectMinHeight
                } else if (hasMaximumSet && cropRect.height() > rectMaxHeight) {
                    this.cropRect.bottom = cropRect.top - rectMaxHeight
                }
            }
            DragType.BottomRight -> {
                Log.d("EasyImagePicker", "HandleDrag - BottomRight")
                // Sets the crop rect bottom to follow the touch position (And clamps it to the image bottom)
                this.cropRect.bottom = min(this.imageRect.bottom, position.y).toInt()
                // Sets the crop rect right to follow the touch position (And clamps it to the image right)
                this.cropRect.right = min(this.imageRect.right, position.x).toInt()

                // Clipping
                if (hasMinimumSet && cropRect.width() <= rectMinWidth) {
                    this.cropRect.right = cropRect.left + rectMinWidth
                } else if (hasMaximumSet && cropRect.width() > rectMaxWidth) {
                    this.cropRect.right = cropRect.left + rectMaxWidth
                }
                if (hasMinimumSet && cropRect.height() <= rectMinHeight) {
                    this.cropRect.bottom = cropRect.top + rectMinHeight
                } else if (hasMaximumSet && cropRect.height() > rectMaxHeight) {
                    this.cropRect.bottom = cropRect.top - rectMaxHeight
                }
            }
        }

        this.clipCorners()

        onDragEnd()
    }

    private fun onDragEnd() {


		// Compute the scale
		// imageRect 1
		// cropRect x
		// x = (cropRect / imageRect)
		/*var scaleFactor = 0f

		if (cropRect.width() > cropRect.height()) {
			scaleFactor = imageRect.width() / cropRect.width()
		} else {
			scaleFactor = imageRect.height() / cropRect.height()
		}


		// Translate to the position
		canvasTransform.preTranslate(cropRect.exactCenterX (), cropRect.exactCenterY())
		canvasTransform.preScale(scaleFactor, scaleFactor)
		canvasTransform.postTranslate(-cropRect.exactCenterX (), -cropRect.exactCenterY())*/

		/*canvasTransform.setTranslate(cropRect.exactCenterX(), cropRect.exactCenterY())
		canvasTransform.setScale(scaleFactor, scaleFactor)
		canvasTransform.postTranslate(-imageRect.width() / 4f, imageRect.height() / 4f)*/

    }

    private fun clipCorners() {
        // Horizontal clipping
        if (cropRect.right > imageRect.right) {
            var w = cropRect.width()
            if (w > imageRect.width()) w = imageRect.width().toInt()

            cropRect.right = imageRect.right.toInt()
            cropRect.left = cropRect.right - w
        } else if (cropRect.left < imageRect.left) {
            var w = cropRect.width()
            if (w > imageRect.width()) w = imageRect.width().toInt()

            cropRect.left = imageRect.left.toInt()
            cropRect.right = cropRect.left + w
        }

        // Vertical clipping
        if (cropRect.top < imageRect.top) {
            var h = cropRect.height()
            if (h > imageRect.height()) h = imageRect.height().toInt()

            cropRect.top = imageRect.top.toInt()
            cropRect.bottom = cropRect.top + h
        } else if (cropRect.bottom > imageRect.bottom) {
            var h = cropRect.height()
            if (h > imageRect.height()) h = imageRect.height().toInt()

            cropRect.bottom = imageRect.bottom.toInt()
            cropRect.top = cropRect.bottom - h
        }
    }

    fun scaleToFit() {
        // Reset the image matrix
        imageMatrix = Matrix()
        imageMatrix.reset()

        image?.let {
            // Create the temporary variables
            var imageFinalX = 0f
            var imageFinalY = 0f
            var imageFinalW = 0f
            var imageFinalH = 0f
			val imagePadding = 7f * resources.displayMetrics.density

            // For the ladscape images
            if (it.width > it.height) {
                if (it.width > this.width) {
                    // If the image's width is greather than the view's width, resize it to fit
                    imageFinalW = this.width.toFloat()
                    imageFinalH = (imageFinalW * it.height) / it.width
                    imageFinalX = 0f
                    imageFinalY = (this.height - imageFinalH) / 2f + imageFinalH / 4f
                } else {
                    // Just center the image horizontally
                    imageFinalX = max(0f, (this.width - it.width) / 2f)
                    imageFinalW = it.width.toFloat()

                    if (it.height > this.height) {
                        // If the image's height is greather than the view's height, resize it to fit
                        imageFinalY = 0f
                        imageFinalH = this.width.toFloat()
                        imageFinalW = (imageFinalH * it.width) / it.height
                    } else {
                        // Just center the image vertically
                        imageFinalY = (this.height - it.height) / 2f + (it.height / 2f)
                        imageFinalH = it.height.toFloat()
                    }
                }
            } else {
                if (it.height >= this.height) {
                    // If the image's width is greather than the view's width, resize it to fit
                    imageFinalH = this.height.toFloat()
                    imageFinalW = min((imageFinalH * it.width) / it.height, this.width.toFloat())
                    imageFinalX = max(0f, (this.width - imageFinalW) / 2f)
                    imageFinalY = 0f
                } else {
                    // Just center the image vertically
                    imageFinalH = it.height.toFloat()
                    imageFinalY =
                        max(0f, (this.height - imageFinalH) / 2f) //(this.height - it.height) / 2f + (it.height / 4f)

                    if (it.width > this.width) {
                        // If the image's height is greather than the view's height, resize it to fit
                        imageFinalY = 0f
                        imageFinalW = this.height.toFloat()
                        imageFinalH = (imageFinalW * it.height) / it.width
                    } else {
                        // Just center the image horizontally
                        imageFinalW = it.width.toFloat()
                        imageFinalX = max(0f, (this.width - imageFinalW) / 2f)
                    }
                }
            }

			imageFinalX += imagePadding
			imageFinalY += imagePadding
			imageFinalW -= imagePadding * 2
			imageFinalH -= imagePadding * 2

            // Creates a rectangle from the image
            this.imageRect = RectF(
                imageFinalX,
					imageFinalY,
                imageFinalX + imageFinalW,
					imageFinalY + imageFinalH
            )

            // Translate the image
			imageMatrix.preTranslate(imageRect.left + imagePadding, imageRect.top + imagePadding)

            // Calculate the scale from the original image size
			val scaleX = imageRect.width() / it.width
			val scaleY = imageRect.height() / it.height
            imageMatrix.postScale(scaleX, scaleY)

			this.scaledImage = Bitmap.createScaledBitmap(image!!, imageRect.width().toInt(), imageRect.height().toInt(), false)

			this.centerCropRect()
        }

        // Post delay a re-paint
        invalidate()
    }

	private fun centerCropRect() {
		val imageCenter = PointF(
				imageRect.centerX(),
				imageRect.centerY()
		)

		when {
			hasMaximumSet -> {
				cropRect.left = (imageCenter.x - rectMaxWidth / 2).toInt()
				cropRect.right = (cropRect.left + rectMaxWidth)

				cropRect.top = (imageCenter.y - rectMaxHeight / 2).toInt()
				cropRect.bottom = (cropRect.top + rectMaxHeight)
			}
			hasMinimumSet -> {
				cropRect.left = (imageCenter.x - rectMinWidth / 2).toInt()
				cropRect.right = (cropRect.left + rectMinWidth)

				cropRect.top = (imageCenter.y - rectMinHeight / 2).toInt()
				cropRect.bottom = (cropRect.top + rectMinHeight)
			}
			else -> {
				cropRect.left = imageRect.left.toInt()
				cropRect.right = imageRect.right.toInt()

				cropRect.top = imageRect.top.toInt()
				cropRect.bottom = imageRect.bottom.toInt()
			}
		}
	}

    private fun drawCropRectangle(canvas: Canvas) {
        // Draw the outline
        canvas.drawRect(cropRect, this.cropRectPaint)


        val rectW = cropRect.width() / 3f
        val rectH = cropRect.height() / 3f
        for (i in 0 until 3) {
            canvas.drawLine(
                cropRect.left + i * rectW, cropRect.top.toFloat(),
                cropRect.left + i * rectW, cropRect.bottom.toFloat(),
                this.cropRectLinePaint
            )

            canvas.drawLine(
                cropRect.left.toFloat(), cropRect.top + i * rectH,
                cropRect.right.toFloat(), cropRect.top + i * rectH,
                this.cropRectLinePaint
            )
        }


        val offset = this.calculateCornerSize()
		val handleDistance = this.cropRectCornerPaint.strokeWidth + resources.displayMetrics.density / 1.5f

        // Top handle
        canvas.drawLine(
				cropRect.left + cropRect.width() / 2f - offset.width / 2f,
				cropRect.top + handleDistance,
				cropRect.left + cropRect.width() / 2f + offset.width / 2f,
				cropRect.top + handleDistance,
            this.cropRectCornerPaint
        )

        // Left handle
        canvas.drawLine(
				cropRect.left + handleDistance,
				cropRect.top + cropRect.height() / 2f - offset.height / 2f,
				cropRect.left + handleDistance,
				cropRect.top + cropRect.height() / 2f + offset.height / 2f,
            this.cropRectCornerPaint
        )

        // Right handle
        canvas.drawLine(
				cropRect.right - handleDistance,
				cropRect.top + cropRect.height() / 2f - offset.height / 2f,
				cropRect.right - handleDistance,
				cropRect.top + cropRect.height() / 2f + offset.height / 2f,
            this.cropRectCornerPaint
        )

        // Bottom handle
        canvas.drawLine(
				cropRect.left + cropRect.width() / 2f - offset.width / 2f,
				cropRect.bottom - handleDistance,
				cropRect.left + cropRect.width() / 2f + offset.width / 2f,
				cropRect.bottom - handleDistance,
            this.cropRectCornerPaint
        )

        // Top-left corner
        canvas.drawLines(
            floatArrayOf(
					cropRect.left.toFloat() + handleDistance,
					cropRect.top + offset.height / 1.5f + handleDistance,

					cropRect.left.toFloat() + handleDistance,
					cropRect.top.toFloat() + handleDistance - cropRectCornerPaint.strokeWidth / 2f,

					cropRect.left + offset.width / 1.5f + handleDistance,
					cropRect.top.toFloat() + handleDistance,

					cropRect.left.toFloat() + handleDistance,
					cropRect.top.toFloat() + handleDistance
            ),
            this.cropRectCornerPaint
        )

        // Bottom-left corner
        canvas.drawLines(
            floatArrayOf(
					cropRect.left.toFloat() + handleDistance,
					cropRect.bottom - offset.height / 1.5f - handleDistance,

					cropRect.left.toFloat() + handleDistance,
					cropRect.bottom.toFloat() - handleDistance + cropRectCornerPaint.strokeWidth / 2f,

					cropRect.left + offset.width / 1.5f + handleDistance,
					cropRect.bottom.toFloat() - handleDistance,

					cropRect.left.toFloat() + handleDistance,
					cropRect.bottom.toFloat() - handleDistance
            ),
            this.cropRectCornerPaint
        )

        // Top-right corner
        canvas.drawLines(
            floatArrayOf(
					cropRect.right.toFloat() - handleDistance,
					cropRect.top + offset.height / 1.5f + handleDistance,

					cropRect.right.toFloat() - handleDistance,
					cropRect.top.toFloat() + handleDistance - cropRectCornerPaint.strokeWidth / 2f,

					cropRect.right - offset.width / 1.5f - handleDistance,
					cropRect.top.toFloat() + handleDistance,

					cropRect.right.toFloat() - handleDistance,
					cropRect.top.toFloat() + handleDistance
            ),
            this.cropRectCornerPaint
        )

		// Bottom-right corner
        canvas.drawLines(
            floatArrayOf(
					cropRect.right.toFloat() - handleDistance,
					cropRect.bottom - offset.height / 1.5f - handleDistance,

					cropRect.right.toFloat() - handleDistance,
					cropRect.bottom.toFloat() - handleDistance + cropRectCornerPaint.strokeWidth / 2f,

					cropRect.right - offset.width / 1.5f - handleDistance,
					cropRect.bottom.toFloat() - handleDistance,

					cropRect.right.toFloat() - handleDistance,
					cropRect.bottom.toFloat() - handleDistance
            ),
            this.cropRectCornerPaint
        )

    }

    private fun drawCornersOutline(canvas: Canvas) {
        val offset = this.calculateCornerSize()

        // Center
        canvas.drawRect(
            cropRect.centerX() - offset.width / 2,
            cropRect.centerY() - offset.height / 2,
            cropRect.centerX() + offset.width / 2,
            cropRect.centerY() + offset.height / 2,
            this.cropRectCornerPaint
        )
        // Top
        canvas.drawRect(
            cropRect.centerX() - offset.width / 2,
            cropRect.top - offset.height / 2,
            cropRect.centerX() + offset.width / 2,
            cropRect.top + offset.height / 2,
            this.cropRectCornerPaint
        )
        // Bottom
        canvas.drawRect(
            cropRect.centerX() - offset.width / 2,
            cropRect.bottom - offset.height / 2,
            cropRect.centerX() + offset.width / 2,
            cropRect.bottom + offset.height / 2,
            this.cropRectCornerPaint
        )
        // Left
        canvas.drawRect(
            cropRect.left - offset.width / 2,
            cropRect.centerY() - offset.height / 2,
            cropRect.left + offset.width / 2,
            cropRect.centerY() + offset.height / 2,
            this.cropRectCornerPaint
        )
        // Left
        canvas.drawRect(
            cropRect.right - offset.width / 2,
            cropRect.centerY() - offset.height / 2,
            cropRect.right + offset.width / 2,
            cropRect.centerY() + offset.height / 2,
            this.cropRectCornerPaint
        )
        // Top-left corner
        canvas.drawRect(
            cropRect.left - offset.width / 2,
            cropRect.top - offset.height / 2,
            cropRect.left + offset.width / 2,
            cropRect.top + offset.height / 2,
            this.cropRectCornerPaint
        )
        // Top-right corner
        canvas.drawRect(
            cropRect.right - offset.width / 2,
            cropRect.top - offset.height / 2,
            cropRect.right + offset.width / 2,
            cropRect.top + offset.height / 2,
            this.cropRectCornerPaint
        )
        // Bottom-left corner
        canvas.drawRect(
            cropRect.left - offset.width / 2,
            cropRect.bottom - offset.height / 2,
            cropRect.left + offset.width / 2,
            cropRect.bottom + offset.height / 2,
            this.cropRectCornerPaint
        )
        // Bottom-right corner
        canvas.drawRect(
            cropRect.right - offset.width / 2,
            cropRect.bottom - offset.height / 2,
            cropRect.right + offset.width / 2,
            cropRect.bottom + offset.height / 2,
            this.cropRectCornerPaint
        )
    }

    private fun calculateCornerSize(): SizeF {
        val x = 30f * context.resources.displayMetrics.density
        val w = cropRect.width() / 4f
        val h = cropRect.height() / 4f

        return SizeF(
            if (w < x) w else x,
            if (h < x) h else x
        )
    }

    override fun onDraw(originCanvas: Canvas?) {
        super.onDraw(originCanvas)

        if (image == null) return
        else if (!imageResized) {
            this.scaleToFit()

            this.clipCorners()

            this.imageResized = true
        }

        originCanvas?.let { canvas ->
            canvas.save()

			// Translate to the position
			canvas.translate(cropRect.exactCenterX(), cropRect.exactCenterY())
			canvas.scale(scaleFactor, scaleFactor)
			canvas.translate(-cropRect.exactCenterX(), -cropRect.exactCenterY())

			//canvas.drawBitmap(image, imageMatrix, null)
			//canvas.drawBitmap(image, null, imageRect, null)
			canvas.drawBitmap(scaledImage, imageRect.left, imageRect.top, null)

			canvas.save()
            canvas.clipRect(cropRect, Region.Op.DIFFERENCE)
            canvas.drawRect(Rect(0, 0, this.width, this.height), cropRectMaskPaint)
            canvas.restore()

			this.drawCropRectangle(canvas)

			canvas.restore()

        }
    }

}