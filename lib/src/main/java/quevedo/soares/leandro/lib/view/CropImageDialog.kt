package quevedo.soares.leandro.lib.view

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.dialog_crop_image.*
import kotlinx.android.synthetic.main.dialog_crop_image.view.*
import quevedo.soares.leandro.lib.EasyImagePicker
import quevedo.soares.leandro.lib.R
import quevedo.soares.leandro.lib.enumerator.CropImageType
import quevedo.soares.leandro.lib.util.CropSizeInfo


/**
 * @author Leandro Soares Quevedo
 * @author leandrosoaresquevedo@gmail.com
 * @since 2019-07-01
 */
class CropImageDialog : BaseDialog() {

	private lateinit var callback: (Bitmap?) -> Unit
	private lateinit var options: EasyImagePicker.ImagePickerRequest
	private lateinit var bitmap: Bitmap
	private var requestedOritentation: Int? = null

	companion object {
		fun show(activity: AppCompatActivity, imagePickerRequest: EasyImagePicker.ImagePickerRequest, bitmap: Bitmap, callback: (Bitmap?) -> Unit) {
			val fragmentManager = activity.supportFragmentManager
			activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

			Handler(Looper.getMainLooper()).post {
				val dialog = CropImageDialog()
				dialog.bitmap = bitmap
				dialog.options = imagePickerRequest
				dialog.requestedOritentation = activity.requestedOrientation
				dialog.callback = callback
				dialog.show(fragmentManager, "CropImageDialog")
			}
		}

		fun show(fragment: Fragment, imagePickerRequest: EasyImagePicker.ImagePickerRequest, bitmap: Bitmap, callback: (Bitmap?) -> Unit) {
			val fragmentManager = fragment.fragmentManager!!
			if (fragment.activity != null)
				fragment.activity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

			Handler(Looper.getMainLooper()).post {
				val dialog = CropImageDialog()
				dialog.bitmap = bitmap
				dialog.options = imagePickerRequest
				if (fragment.activity != null) dialog.requestedOritentation = fragment.activity!!.requestedOrientation
				dialog.callback = callback

				dialog.show(fragmentManager, "CropImageDialog")
			}
		}
	}

	override fun onPreload(inflater: LayoutInflater, parent: ViewGroup?): View = inflater.inflate(R.layout.dialog_crop_image, parent, false)

	override fun onInitValues() {
		this.cropView.apply {
			// Sets the image
			this.image = bitmap

			if (!options.optionRotate) {
				btnRotate.visibility = View.GONE
				dividerRotateView.visibility = View.GONE
			}

			// When fixed size, set the minimum and maximum size to the fixed size
			if (options.optionCropType == CropImageType.FIXED_SIZE) {
				this.cropView.cropSizeInfo = CropSizeInfo(
					isMaxSet = true,
					isMinSet = true,
					minWidth = options.cropSizeWidth,
					maxWidth = options.cropSizeHeight,
					minHeight = options.cropSizeWidth,
					maxHeight = options.cropSizeHeight
				)
			} else {
				var minWidth = 0
				var minHeight = 0
				var maxWidth = 0
				var maxHeight = 0

				// Sets the minimum size
				if (options.cropMinimumSizeWidth != null && options.cropMinimumSizeHeight != null) {
					minWidth = options.cropMinimumSizeWidth!!
					minHeight = options.cropMinimumSizeHeight!!
				}

				// Sets the maximum size
				if (options.cropMaximumSizeWidth != null && options.cropMaximumSizeHeight != null) {
					maxWidth = options.cropMaximumSizeWidth!!
					maxHeight = options.cropMaximumSizeHeight!!
				}

				this.cropView.cropSizeInfo = CropSizeInfo(
					isMaxSet = maxWidth != 0 || maxHeight != 0,
					isMinSet = minWidth != 0 || minHeight != 0,
					minWidth = minWidth,
					maxWidth = maxWidth,
					minHeight = minHeight,
					maxHeight = maxHeight
				)
			}
		}

		this.btnRotate.setOnClickListener {
			this.cropView.rotate()
		}

		this.btnOk.setOnClickListener {
			callback.invoke(this.cropView.croppedImage)
			dismiss()
		}

		this.btnCancel.setOnClickListener {
			this.dismiss()
		}
	}

	override fun onDispose() {
		Handler(Looper.getMainLooper()).post {
			requestedOritentation?.let {
				activity?.requestedOrientation = it
			}
		}
	}

	override fun setUserVisibleHint(isVisibleToUser: Boolean) {
		super.setUserVisibleHint(isVisibleToUser)
		if (isVisibleToUser) {
			activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
		}
	}

}