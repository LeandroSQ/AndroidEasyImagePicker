package quevedo.soares.leandro.easyimagepicker

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import quevedo.soares.leandro.lib.EasyImagePicker

class MainActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
	}

	fun onClickCamera(v: View) {
		EasyImagePicker.with(this)
				.listener { error, bitmap ->
					if (error != null) {
						Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
					} else {
						image.setImageBitmap(bitmap)
					}
				}
				.pick(EasyImagePicker.ImageSource.CAMERA)

	}

	fun onClickGallery(v: View) {
		EasyImagePicker.with(this)
				.listener { error, bitmap ->
					if (error != null) {
						Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
					} else {
						image.setImageBitmap(bitmap)
					}
				}
				.pick(EasyImagePicker.ImageSource.GALLERY)
	}

	fun onClickAny(v: View) {
		EasyImagePicker.with(this)
				.listener { error, bitmap ->
					if (error != null) {
						Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
					} else {
						image.setImageBitmap(bitmap)
					}
				}
				.pick(EasyImagePicker.ImageSource.ANY)
	}

}
