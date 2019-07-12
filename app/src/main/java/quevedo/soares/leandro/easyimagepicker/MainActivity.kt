package quevedo.soares.leandro.easyimagepicker

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import quevedo.soares.leandro.lib.EasyImagePicker
import quevedo.soares.leandro.lib.enumerator.ImageSource

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }


    fun onClickCamera(v: View) {
        Toast.makeText(this, "FIXED_SIZE 128x128", Toast.LENGTH_SHORT).show()

        EasyImagePicker.with(this)
            .crop()
            .size(128, 128)
            .listener { error, bitmap ->
                if (error != null) {
                    Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
                } else {
                    image.setImageBitmap(bitmap)
                }
            }
            .pick(ImageSource.CAMERA)

    }

    fun onClickGallery(v: View) {
        Toast.makeText(this, "MINIMUM 256x256", Toast.LENGTH_SHORT).show()

        EasyImagePicker.with(this)
            .minimumSize(256, 256)
            .listener { error, bitmap ->
                if (error != null) {
                    Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
                } else {
                    image.setImageBitmap(bitmap)
                }
            }
            .pick(ImageSource.GALLERY)
    }

    fun onClickAny(v: View) {
        Toast.makeText(this, "ASPECT RATIO 2x1", Toast.LENGTH_SHORT).show()

        EasyImagePicker.with(this)
            .aspectRatio(2, 1)
            .listener { error, bitmap ->
                if (error != null) {
                    Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
                } else {
                    image.setImageBitmap(bitmap)
                }
            }
            .pick(ImageSource.ANY)
    }

}
