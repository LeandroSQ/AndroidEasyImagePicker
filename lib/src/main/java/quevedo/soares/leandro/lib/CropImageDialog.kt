package quevedo.soares.leandro.lib

import android.content.Context
import android.graphics.*
import android.support.v7.app.AppCompatActivity
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.dialog_crop_image.*
import kotlin.math.sqrt

/**
 * @author Leandro Soares Quevedo
 * @author leandro.soares@operacao.rcadigital.com.br
 * @since 2019-07-01
 */
class CropImageDialog : BaseDialog() {

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

        enum class DragType {
            Center,
            Top,
            Left,
            Right,
            Bottom,
            TopLeft,
            TopRight,
            BottomLeft,
            BottomRight
        }

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
                        initDrag(position)
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

        private fun initDrag(position: PointF) {
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
            } else if (distance(lastPosition.x, lastPosition.y, cropRect.left + cropRect.width() / 2, cropRect.top) <= threshold) {
                // Top
                this.dragType = DragType.Top
            } else if (distance(lastPosition.x, lastPosition.y, cropRect.left + cropRect.width() / 2, cropRect.bottom) <= threshold) {
                // Bottom
                this.dragType = DragType.Bottom
            } else if (distance(lastPosition.x, lastPosition.y, cropRect.left, cropRect.top + cropRect.height() / 2) <= threshold) {
                // Left
                this.dragType = DragType.Left
            } else if (distance(lastPosition.x, lastPosition.y, cropRect.right, cropRect.top + cropRect.height() / 2) <= threshold) {
                // Right
                this.dragType = DragType.Right
            } else if (distance(lastPosition.x, lastPosition.y, cropRect.centerX(), cropRect.centerY()) <= threshold) {
                // Center
                this.dragType = DragType.Center
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
                }
                DragType.Bottom -> {
                    Log.d("EasyImagePicker", "HandleDrag - Bottom")
                    this.cropRect.bottom = position.y.toInt()
                }
                DragType.Left -> {
                    Log.d("EasyImagePicker", "HandleDrag - Left")
                    this.cropRect.left = position.x.toInt()
                }
                DragType.Right -> {
                    Log.d("EasyImagePicker", "HandleDrag - Right")
                    this.cropRect.right = position.x.toInt()
                }
                DragType.TopLeft -> {
                    Log.d("EasyImagePicker", "HandleDrag - TopLeft")
                    this.cropRect.top = position.y.toInt()
                    this.cropRect.left = position.x.toInt()
                }
                DragType.TopRight -> {
                    Log.d("EasyImagePicker", "HandleDrag - TopRight")
                    this.cropRect.top = position.y.toInt()
                    this.cropRect.right = position.x.toInt()
                }
                DragType.BottomLeft -> {
                    Log.d("EasyImagePicker", "HandleDrag - BottomLeft")
                    this.cropRect.bottom = position.y.toInt()
                    this.cropRect.left = position.x.toInt()
                }
                DragType.BottomRight -> {
                    Log.d("EasyImagePicker", "HandleDrag - BottomRight")
                    this.cropRect.bottom = position.y.toInt()
                    this.cropRect.right = position.x.toInt()
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

    companion object {
        fun show(activity: AppCompatActivity, bitmap: Bitmap) {
            val dialog = CropImageDialog()
            dialog.bitmap = bitmap
            dialog.show(activity.supportFragmentManager, "CropImageDialog")
        }
    }

    private lateinit var bitmap: Bitmap

    override fun onPreload(inflater: LayoutInflater, parent: ViewGroup?): View {
        return inflater.inflate(R.layout.dialog_crop_image, parent, false)
    }

    override fun onInitValues() {
        (this.cropView as CropView).image = bitmap

        this.btnOk.setOnClickListener {
            (this.cropView as CropView).scaleToFit()
        }

        this.btnCancel.setOnClickListener {
            this.dismiss()
        }
    }

    override fun onDispose() {

    }

}