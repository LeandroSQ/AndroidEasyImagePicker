package quevedo.soares.leandro.lib.util

/**
 * @author Leandro Soares Quevedo
 * @author leandrosoaresquevedo@gmail.com
 * @since 2019-07-22
 */
internal object Log {

	var enabled = true

	fun d(msg: String) {
		if (enabled)
			android.util.Log.d("EasyImagePicker", msg)
	}

	fun e(msg: String) {
		if (enabled)
			android.util.Log.e("EasyImagePicker", msg)
	}

	fun e(msg: String, e: Exception) {
		if (enabled)
			android.util.Log.e("EasyImagePicker", msg, e)
	}

}