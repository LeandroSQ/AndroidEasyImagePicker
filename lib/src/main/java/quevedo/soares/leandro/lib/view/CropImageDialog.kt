package quevedo.soares.leandro.lib.view

import android.graphics.Bitmap
import android.support.v4.app.FragmentManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.dialog_crop_image.*
import quevedo.soares.leandro.lib.EasyImagePicker
import quevedo.soares.leandro.lib.R
import quevedo.soares.leandro.lib.enumerator.CropImageType

/**
 * @author Leandro Soares Quevedo
 * @author leandro.soares@operacao.rcadigital.com.br
 * @since 2019-07-01
 */
class CropImageDialog : BaseDialog() {
    private lateinit var options: EasyImagePicker.ImagePickerRequest
    private lateinit var bitmap: Bitmap

    companion object {
        fun show(fragmentManager: FragmentManager, imagePickerRequest: EasyImagePicker.ImagePickerRequest, bitmap: Bitmap) {
            val dialog = CropImageDialog()
            dialog.bitmap = bitmap
            dialog.options = imagePickerRequest
            dialog.show(fragmentManager, "CropImageDialog")
        }
    }

    override fun onPreload(inflater: LayoutInflater, parent: ViewGroup?): View {
        return inflater.inflate(R.layout.dialog_crop_image, parent, false)
    }

    override fun onInitValues() {
        this.cropView.apply {
            // Sets the image
            this.image = bitmap

            // When fixed size, set the minimum and maximum size to the fixed size
            if (options.optionCropType == CropImageType.FIXED_SIZE) {
                this.rectMinWidth = options.cropSizeWidth
                this.rectMinHeight = options.cropSizeHeight
                this.rectMaxWidth = options.cropSizeWidth
                this.rectMaxHeight = options.cropSizeHeight
            } else {
                // Sets the minimum size
                if (options.cropMinimumSizeWidth != null && options.cropMinimumSizeHeight != null) {
                    this.rectMinWidth = options.cropMinimumSizeWidth!!
                    this.rectMinHeight = options.cropMinimumSizeHeight!!
                }

                // Sets the maximum size
                if (options.cropMaximumSizeWidth != null && options.cropMaximumSizeHeight != null) {
                    this.rectMaxWidth = options.cropMaximumSizeWidth!!
                    this.rectMaxHeight = options.cropMaximumSizeHeight!!
                }
            }
        }

        this.btnOk.setOnClickListener {
            this.cropView.scaleToFit()
        }

        this.btnCancel.setOnClickListener {
            this.dismiss()
        }
    }

    override fun onDispose() {

    }

}