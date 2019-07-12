package quevedo.soares.leandro.lib.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import quevedo.soares.leandro.lib.enumerator.DragType
import kotlin.math.abs
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

    var imageResized: Boolean = false

    var cropRect: Rect = Rect(256, 256, 256 + 256, 256 + 256)
    private lateinit var cropRectPaint: Paint
    private lateinit var cropRectLinePaint: Paint
    private lateinit var cropRectCornerPaint: Paint

    var pointerCount: Int = 0
    var dragging: Boolean = false
    var activePointerIndex: Int = -1

    var rectMinWidth = 128
    var rectMinHeight = 128
    var rectMaxWidth = 0
    var rectMaxHeight = 0

    var dragType: DragType? = null
    lateinit var lastPosition: PointF

    private val cornerSize: Float
        get() {
            val x = 30f * context.resources.displayMetrics.density
            val w = cropRect.width() / 4f
            val h = cropRect.height() / 4f


            return when {
                w < x -> w
                h < x -> h
                else -> x
            }
        }

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

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        this.onInitialized()
    }

    //</editor-fold>

    private fun onInitialized() {
        this.setWillNotDraw(false)
        this.isFocusable = true
        this.isFocusableInTouchMode = true
        this.isClickable = true

        this.measure(0, 0)

        //<editor-fold defaultstate="Collapsed" desc="Paint definition">
        this.cropRectPaint = Paint()
        this.cropRectPaint.color = Color.WHITE
        this.cropRectPaint.style = Paint.Style.STROKE
        this.cropRectPaint.strokeWidth = 1.5f * resources.displayMetrics.density
        this.cropRectPaint.strokeJoin = Paint.Join.MITER
        this.cropRectPaint.strokeCap = Paint.Cap.BUTT
        this.cropRectPaint.isAntiAlias = true

        this.cropRectLinePaint = Paint(this.cropRectPaint)
        this.cropRectLinePaint.strokeWidth = 1f * resources.displayMetrics.density
        this.cropRectLinePaint.color = Color.argb(100, 255, 255, 255)

        this.cropRectCornerPaint = Paint(this.cropRectLinePaint)
        this.cropRectCornerPaint.strokeWidth = 4f * resources.displayMetrics.density
        this.cropRectCornerPaint.style = Paint.Style.FILL
        this.cropRectCornerPaint.color = Color.argb(255, 175, 175, 175)
        //</editor-fold>

        setOnTouchListener { _, event ->
            event?.let {
                onTouch(it)
            }

            return@setOnTouchListener true
        }
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
                    dragging = false
                    activePointerIndex = -1
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
                    initDrag()
                } else {
                    handleDrag(position)
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

    private fun initDrag() {
        Log.d("EasyImagePicker", "InitDrag")

        val threshold = cornerSize

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

    }

    private fun handleDrag(position: PointF) {
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
                this.cropRect.top = position.y.toInt()

                // Clipping
                if (cropRect.height() <= rectMinHeight) {
                    this.cropRect.top = cropRect.bottom - rectMinHeight
                }
            }
            DragType.Bottom -> {
                Log.d("EasyImagePicker", "HandleDrag - Bottom")
                this.cropRect.bottom = position.y.toInt()

                // Clipping
                if (cropRect.height() <= rectMinHeight) {
                    this.cropRect.bottom = cropRect.top + rectMinHeight
                }
            }
            DragType.Left -> {
                Log.d("EasyImagePicker", "HandleDrag - Left")
                this.cropRect.left = position.x.toInt()

                // Clipping
                if (cropRect.width() <= rectMinWidth) {
                    this.cropRect.left = cropRect.right - rectMinWidth
                }
            }
            DragType.Right -> {
                Log.d("EasyImagePicker", "HandleDrag - Right")
                this.cropRect.right = position.x.toInt()

                // Clipping
                if (cropRect.width() <= rectMinWidth) {
                    this.cropRect.right = cropRect.left + rectMinWidth
                }
            }
            DragType.TopLeft -> {
                Log.d("EasyImagePicker", "HandleDrag - TopLeft")
                this.cropRect.top = position.y.toInt()
                this.cropRect.left = position.x.toInt()

                // Clipping
                if (cropRect.width() <= rectMinWidth) {
                    this.cropRect.left = cropRect.right - rectMinWidth
                }
                if (cropRect.height() <= rectMinHeight) {
                    this.cropRect.top = cropRect.bottom - rectMinHeight
                }
            }
            DragType.TopRight -> {
                Log.d("EasyImagePicker", "HandleDrag - TopRight")
                this.cropRect.top = position.y.toInt()
                this.cropRect.right = position.x.toInt()

                // Clipping
                if (cropRect.width() <= rectMinWidth) {
                    this.cropRect.right = cropRect.left + rectMinWidth
                }
                if (cropRect.height() <= rectMinHeight) {
                    this.cropRect.top = cropRect.bottom - rectMinHeight
                }
            }
            DragType.BottomLeft -> {
                Log.d("EasyImagePicker", "HandleDrag - BottomLeft")
                this.cropRect.bottom = position.y.toInt()
                this.cropRect.left = position.x.toInt()

                // Clipping
                if (cropRect.width() <= rectMinWidth) {
                    this.cropRect.left = cropRect.right - rectMinWidth
                }
                if (cropRect.height() <= rectMinHeight) {
                    this.cropRect.bottom = cropRect.top + rectMinHeight
                }
            }
            DragType.BottomRight -> {
                Log.d("EasyImagePicker", "HandleDrag - BottomRight")
                this.cropRect.bottom = position.y.toInt()
                this.cropRect.right = position.x.toInt()

                // Clipping
                if (cropRect.width() <= rectMinWidth) {
                    this.cropRect.right = cropRect.left + rectMinWidth
                }
                if (cropRect.height() <= rectMinHeight) {
                    this.cropRect.bottom = cropRect.top + rectMinHeight
                }
            }
        }

        this.clipCorners()
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
                    imageFinalX = (this.width - it.width) / 2f + (it.width / 2f)
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
            }

            // Creates a rectangle from the image
            this.imageRect = RectF(
                imageFinalX,
                imageFinalY - imageFinalH / 4f,
                imageFinalX + imageFinalW,
                imageFinalY + imageFinalH - imageFinalH / 4f
            )

            // Translate the image
            imageMatrix.postTranslate(imageFinalX, imageFinalY)

            // Calculate the scale from the original image size
            val scaleX = imageFinalW / it.width
            val scaleY = imageFinalH / it.height
            imageMatrix.postScale(scaleX, scaleY)
        }

        // Post delay a re-paint
        invalidate()
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

        val offset = cornerSize


        // Top-left corner
        canvas.drawLines(
            floatArrayOf(
                cropRect.left.toFloat(),
                cropRect.top + offset,

                cropRect.left.toFloat(),
                cropRect.top.toFloat() - this.cropRectCornerPaint.strokeWidth / 2f,

                cropRect.left + offset,
                cropRect.top.toFloat(),

                cropRect.left.toFloat(),
                cropRect.top.toFloat()
            ),
            this.cropRectCornerPaint
        )

        // Bottom-left corner
        canvas.drawLines(
            floatArrayOf(
                cropRect.left.toFloat(),
                cropRect.bottom - offset,

                cropRect.left.toFloat(),
                cropRect.bottom.toFloat() + this.cropRectCornerPaint.strokeWidth / 2f,

                cropRect.left + offset,
                cropRect.bottom.toFloat(),

                cropRect.left.toFloat(),
                cropRect.bottom.toFloat()
            ),
            this.cropRectCornerPaint
        )

        // Top-right corner
        canvas.drawLines(
            floatArrayOf(
                cropRect.right.toFloat(),
                cropRect.top + offset,

                cropRect.right.toFloat(),
                cropRect.top.toFloat() - this.cropRectCornerPaint.strokeWidth / 2f,

                cropRect.right - offset,
                cropRect.top.toFloat(),

                cropRect.right.toFloat(),
                cropRect.top.toFloat()
            ),
            this.cropRectCornerPaint
        )

        // Bottom-left corner
        canvas.drawLines(
            floatArrayOf(
                cropRect.right.toFloat(),
                cropRect.bottom - offset,

                cropRect.right.toFloat(),
                cropRect.bottom.toFloat() + this.cropRectCornerPaint.strokeWidth / 2f,

                cropRect.right - offset,
                cropRect.bottom.toFloat(),

                cropRect.right.toFloat(),
                cropRect.bottom.toFloat()
            ),
            this.cropRectCornerPaint
        )

    }

    private fun drawCornersOutline(canvas: Canvas) {
        val offset = cornerSize

        // Center
        canvas.drawRect(
            cropRect.centerX() - offset / 2,
            cropRect.centerY() - offset / 2,
            cropRect.centerX() + offset / 2,
            cropRect.centerY() + offset / 2,
            this.cropRectCornerPaint
        )
        // Top
        canvas.drawRect(
            cropRect.centerX() - offset / 2,
            cropRect.top - offset / 2,
            cropRect.centerX() + offset / 2,
            cropRect.top + offset / 2,
            this.cropRectCornerPaint
        )
        // Bottom
        canvas.drawRect(
            cropRect.centerX() - offset / 2,
            cropRect.bottom - offset / 2,
            cropRect.centerX() + offset / 2,
            cropRect.bottom + offset / 2,
            this.cropRectCornerPaint
        )
        // Left
        canvas.drawRect(
            cropRect.left - offset / 2,
            cropRect.centerY() - offset / 2,
            cropRect.left + offset / 2,
            cropRect.centerY() + offset / 2,
            this.cropRectCornerPaint
        )
        // Left
        canvas.drawRect(
            cropRect.right - offset / 2,
            cropRect.centerY() - offset / 2,
            cropRect.right + offset / 2,
            cropRect.centerY() + offset / 2,
            this.cropRectCornerPaint
        )
        // Top-left corner
        canvas.drawRect(
            cropRect.left - offset / 2,
            cropRect.top - offset / 2,
            cropRect.left + offset / 2,
            cropRect.top + offset / 2,
            this.cropRectCornerPaint
        )
        // Top-right corner
        canvas.drawRect(
            cropRect.right - offset / 2,
            cropRect.top - offset / 2,
            cropRect.right + offset / 2,
            cropRect.top + offset / 2,
            this.cropRectCornerPaint
        )
        // Bottom-left corner
        canvas.drawRect(
            cropRect.left - offset / 2,
            cropRect.bottom - offset / 2,
            cropRect.left + offset / 2,
            cropRect.bottom + offset / 2,
            this.cropRectCornerPaint
        )
        // Bottom-right corner
        canvas.drawRect(
            cropRect.right - offset / 2,
            cropRect.bottom - offset / 2,
            cropRect.right + offset / 2,
            cropRect.bottom + offset / 2,
            this.cropRectCornerPaint
        )
    }

    override fun onDraw(originCanvas: Canvas?) {
        super.onDraw(originCanvas)

        if (image == null) return
        else if (!imageResized) {
            this.scaleToFit()

            val s = (this.imageRect.height() + this.imageRect.width()) / 4f
            this.cropRect.top = (this.imageRect.centerY() - s / 2f).toInt()
            this.cropRect.left = (this.imageRect.centerX() - s / 2f).toInt()
            this.cropRect.right = (this.cropRect.left + s).toInt()
            this.cropRect.bottom = (this.cropRect.top + s).toInt()

            this.clipCorners()
            this.imageResized = true
        }

        originCanvas?.let { canvas ->

            canvas.drawBitmap(image, imageMatrix, null)

            this.drawCropRectangle(canvas)

        }
    }

}