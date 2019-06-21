package quevedo.soares.leandro.lib

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.util.Log
import java.io.File
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.collections.ArrayList

/**
 * @author Leandro Soares Quevedo
 * @since 2019-06-18
 */
object EasyImagePicker {

	const val FRAGMENT_ID: String = "EasyImagePickerEventFragment"
	const val PERMISSION_REQUEST_CODE: Int = 1039
	const val INTENT_CHOOSER_REQUEST_CODE: Int = 1040
	const val GALLERY_PICKER_REQUEST_CODE: Int = 1041
	const val CAMERA_CAPTURE_REQUEST_CODE: Int = 1042

	enum class ImageSource {
		GALLERY,
		CAMERA,
		ANY
	}

	enum class CropImageType {
		FREE_CROPPING,
		ASPECT_RATIO,
		FIXED_SIZE
	}

	@SuppressLint("ValidFragment")
	class EventStealerFragment : Fragment {

		private lateinit var onPermissionsResultListener: (Int, Array<out String>, IntArray) -> Unit
		private lateinit var onActivityResultListener: (Int, Int, Intent?) -> Unit

		constructor(onPermissionsResultListener: (Int, Array<out String>, IntArray) -> Unit, onActivityResultListener: (Int, Int, Intent?) -> Unit) {
			this.onPermissionsResultListener = onPermissionsResultListener
			this.onActivityResultListener = onActivityResultListener
		}

		fun setup(onPermissionsResultListener: (Int, Array<out String>, IntArray) -> Unit, onActivityResultListener: (Int, Int, Intent?) -> Unit) {
			this.onPermissionsResultListener = onPermissionsResultListener
			this.onActivityResultListener = onActivityResultListener
		}

		override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults)
			this.onPermissionsResultListener(requestCode, permissions, grantResults)
		}

		override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
			super.onActivityResult(requestCode, resultCode, data)
			this.onActivityResultListener(requestCode, resultCode, data)
		}

	}

	class Picker {

		//<editor-fold defaultstate="Collapsed" desc="Context variables">
		private var activity: AppCompatActivity? = null
		private var fragment: Fragment? = null
		private val context: Context by lazy { activity ?: fragment!!.context!! }
		private val fragmentManager: FragmentManager by lazy {
			if (activity != null) {
				return@lazy activity!!.supportFragmentManager!!
			} else {
				return@lazy fragment!!.fragmentManager!!
			}
		}

		private var eventListenerFragment: EventStealerFragment? = null
		//</editor-fold>

		private var imageSource: ImageSource = EasyImagePicker.ImageSource.ANY

		private var temporaryFile: Uri? = null
		private val fileProviderAuthority: String by lazy { this.context.applicationContext.packageName + ".provider" }

		//<editor-fold defaultstate="Collapsed" desc="Callbacks">
		private var successListener: ((Bitmap?) -> Unit)? = null
		private var errorListener: ((Throwable) -> Unit)? = null
		private var handled: Boolean = false
		//</editor-fold>

		//<editor-fold defaultstate="Collapsed" desc="Cropping options">
		private var optionCrop: Boolean = false
		private var optionCropType: CropImageType = EasyImagePicker.CropImageType.FREE_CROPPING

		private var cropAspectRatioHorizontal: Int = 0
		private var cropAspectRatioVertical: Int = 0

		private var cropSizeWidth: Int = 0
		private var cropSizeHeight: Int = 0
		//</editor-fold>

		constructor(activity: AppCompatActivity) {
			if (activity.isDestroyed) throw IllegalArgumentException("The provided activity is destroyed!")

			this.activity = activity

			this.createEventListener()
		}

		constructor(fragment: Fragment) {
			if (fragment.isDetached) throw IllegalArgumentException("The provided fragment is detached!")

			this.fragment = fragment

			this.createEventListener()
		}

		private fun createEventListener() {
			eventListenerFragment = fragmentManager.findFragmentByTag(FRAGMENT_ID) as? EventStealerFragment
			if (eventListenerFragment == null) {
				eventListenerFragment = EventStealerFragment(
						onActivityResultListener = { request: Int, response: Int, intent: Intent? ->
							this.handleActivityResult(request, response, intent)
						},
						onPermissionsResultListener = { request: Int, permissions: Array<out String>, results: IntArray ->
							this.handlePermissionResult(request, permissions, results.toTypedArray())
						}
				)

				fragmentManager.beginTransaction()
						.add(this.eventListenerFragment!!, FRAGMENT_ID)
						.commit()
			} else {
				eventListenerFragment!!.setup(
						onActivityResultListener = { request: Int, response: Int, intent: Intent? ->
							this.handleActivityResult(request, response, intent)
						},
						onPermissionsResultListener = { request: Int, permissions: Array<out String>, results: IntArray ->
							this.handlePermissionResult(request, permissions, results.toTypedArray())
						}
				)
			}
		}

		//<editor-fold defaultstate="Collapsed" desc="Exposed image picking methods">

		fun crop(): Picker {
			this.optionCrop = true
			this.optionCropType = EasyImagePicker.CropImageType.FREE_CROPPING

			return this
		}

		fun size(width: Int, height: Int): Picker {
			this.optionCrop = true
			this.optionCropType = EasyImagePicker.CropImageType.FIXED_SIZE

			return this
		}

		fun aspectRatio(horizontal: Int, vertical: Int): Picker {
			this.optionCrop = true
			this.optionCropType = EasyImagePicker.CropImageType.ASPECT_RATIO

			return this
		}

		fun pick(source: ImageSource = EasyImagePicker.ImageSource.ANY) {
			this.imageSource = source

			this.requestImage()
		}

		//</editor-fold>

		//<editor-fold defaultstate="Collapsed" desc="Listener methods">

		fun listener(listener: (Bitmap?) -> Unit): Picker {
			this.successListener = listener
			this.errorListener = {
				listener(null)
			}

			return this
		}

		fun listener(listener: (Throwable?, Bitmap?) -> Unit): Picker {
			this.successListener = {
				listener(null, it)
			}

			this.errorListener = {
				listener(it, null)
			}

			return this
		}

		fun listener(success: (Bitmap?) -> Unit, error: (Throwable) -> Unit): Picker {
			this.successListener = success
			this.errorListener = error

			return this
		}

		//</editor-fold>

		//<editor-fold defaultstate="Collapsed" desc="Permission handling">

		fun handlePermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: Array<Int>): Boolean {
			if (requestCode == PERMISSION_REQUEST_CODE) {
				// Check if all the requested permissions were granted!
				if (grantResults.all { x -> x == PackageManager.PERMISSION_GRANTED }) {
					// Continue the image requesting
					this.requestImage()
				} else {
					this.handleError("Permissions denied!")
				}

				return true
			} else return false
		}

		private fun isPermissionGranted(vararg permissions: String): Boolean {
			var grantedPermissionCount = 0

			for (permission in permissions) {
				if (ActivityCompat.checkSelfPermission(this.context, permission) == PackageManager.PERMISSION_GRANTED)
					grantedPermissionCount++
			}

			return grantedPermissionCount == permissions.size
		}

		private fun requestPermissions(): Boolean {
			if (this.isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)) {
				return true
			} else {
				this.eventListenerFragment?.requestPermissions(
						arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA),
						PERMISSION_REQUEST_CODE
				)

				return false
			}
		}

		private fun checkManifestPermissions(): Boolean {
			if (!this.isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
				// The read_external_storage permission is either not_requested or denied
				this.handleError("The read external storage permission is denied!")
				return false
			} else if (!this.isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
				// The write_external_storage permission is either not_requested or denied
				this.handleError("The write external storage permission is denied!")
				return false
			} else if (!this.isPermissionGranted(Manifest.permission.CAMERA)) {
				// The camera permission is either not_requested or denied
				this.handleError("The camera permission is denied!")
				return false
			} else {
				// All permissions are gracefully granted, just continue
				return true
			}
		}

		private fun handleError(message: String) {
			this.handled = true
			this.errorListener?.invoke(Exception(message))
		}

		//</editor-fold>

		private fun requestImage() {
			if (!this.eventListenerFragment?.isAdded!!) {
				Handler(Looper.getMainLooper()).post(this::requestImage)
				return
			}


			// First of all, just check the manifest permissions
			if (this.requestPermissions()) {
				when (this.imageSource) {
					EasyImagePicker.ImageSource.GALLERY -> {
						Log.d("EasyImagePicker", "Requesting image from gallery...")
						val intent = this.createGalleryPickIntent()

						this.eventListenerFragment?.startActivityForResult(intent, GALLERY_PICKER_REQUEST_CODE)

						/*if (this.activity != null) {
							this.activity!!.startActivityForResult(intent, GALLERY_PICKER_REQUEST_CODE)
						} else {
							this.fragment!!.startActivityForResult(intent, GALLERY_PICKER_REQUEST_CODE)
						}*/
					}
					EasyImagePicker.ImageSource.CAMERA -> {
						Log.d("EasyImagePicker", "Requesting camera image capture...")

						val intent = this.createCameraCaptureIntent()

						this.eventListenerFragment?.startActivityForResult(intent, CAMERA_CAPTURE_REQUEST_CODE)

						/*if (this.activity != null) {
							this.activity!!.startActivityForResult(intent, CAMERA_CAPTURE_REQUEST_CODE)
						} else {
							this.fragment!!.startActivityForResult(intent, CAMERA_CAPTURE_REQUEST_CODE)
						}*/
					}
					EasyImagePicker.ImageSource.ANY -> {
						Log.d("EasyImagePicker", "Requesting user image source...")
						this.showImagePickerIntent()
					}
				}
			} else {
				Log.d("EasyImagePicker", "The permissions were denied or not_requested. Waiting them to be granted!")
			}
		}

		//<editor-fold defaultstate="Collapsed" desc="Intent handling">

		fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
			when (requestCode) {
				INTENT_CHOOSER_REQUEST_CODE -> {
					if (resultCode == Activity.RESULT_OK) {
						Log.d("EasyImagePicker", "User has selected the image source!")

						this.handleBitmapResponse(data)
					} else {
						Log.d("EasyImagePicker", "User has dismissed the intent chooser!")
					}
					return true
				}
				GALLERY_PICKER_REQUEST_CODE -> {
					if (resultCode == Activity.RESULT_OK) {
						Log.d("EasyImagePicker", "User has selected the image!")

						this.handleBitmapResponse(data)
					} else {
						Log.d("EasyImagePicker", "Gallery picking operation has either been canceled or failed!")
					}
					return true
				}
				CAMERA_CAPTURE_REQUEST_CODE -> {
					if (resultCode == Activity.RESULT_OK) {
						Log.d("EasyImagePicker", "User has captured the image!")

						this.handleBitmapResponse(data)
					} else {
						Log.d("EasyImagePicker", "Camera capturing operation has either been canceled or failed!")
					}
					return true
				}
				else -> return false
			}
		}

		private fun handleBitmapResponse(response: Intent?) {
			if (response != null) {
				when {
					// Check for the "return-data" behaviour
					response.hasExtra("data") -> {
						val image = response.extras!!.get("data") as Bitmap

						this.successListener?.invoke(image)
					}

					// Check for the URI "intent-data" behaviour
					response.data != null -> {
						val result = this.loadBitmapFromUri(response.data!!)

						this.successListener?.invoke(result)
					}

					// Check for the "output" extra
					response.hasExtra(MediaStore.EXTRA_OUTPUT) -> {
						val image = response.extras!!.get(MediaStore.EXTRA_OUTPUT) as? Bitmap

						this.successListener?.invoke(image)
					}

					else -> {
						this.handleError("Unable to get the gallery output!")
					}
				}
			} else if (this.temporaryFile != null) {
				val result = this.loadBitmapFromUri(this.temporaryFile!!)

				this.successListener?.invoke(result)
			} else {
				this.handleError("Some error has occurred!")
			}
		}

		private fun loadBitmapFromUri(uri: Uri): Bitmap? {
			// Loads the bitmap from the URI
			var rawBitmap = MediaStore.Images.Media.getBitmap(this.context.contentResolver, uri)
			if (rawBitmap == null) rawBitmap = BitmapFactory.decodeStream(this.context.contentResolver.openInputStream(uri))

			return this.handleBitmapRotation(uri, rawBitmap)
		}

		private fun handleBitmapRotation(uri: Uri, rawBitmap: Bitmap): Bitmap {
			try {
				// This inner function only rotates an image at a given angle
				fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
					val matrix = Matrix()
					matrix.postRotate(angle)
					return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
				}

				// Builds an exif interface according to device SDK
				val exifInterface =
						if (Build.VERSION.SDK_INT > 23) {
							ExifInterface(context.contentResolver.openInputStream(uri))
						} else {
							ExifInterface(uri.path)
						}

				return when (exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
					ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(rawBitmap, 90f)
					ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(rawBitmap, 180f)
					ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(rawBitmap, 270f)
					else -> rawBitmap
				}
			} catch (e: Exception) {
				// In case of any error, ignore it and return the raw bitmap
				e.printStackTrace()

				return rawBitmap
			}
		}

		@SuppressLint("SimpleDateFormat")
		private fun createTemporaryFile(): File {
			// Generate an unique filename
			val filename = "jpeg_${UUID.randomUUID().toString().replace("-", "")}"
			// Get the public directory
			val storagePath = this.context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
			// Return the file
			return File.createTempFile(filename, ".jpg", storagePath)
		}

		private fun createCameraCaptureIntent(): Intent {
			when {
				Build.VERSION.SDK_INT <= Build.VERSION_CODES.M -> {
					val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
					return intent
				}
				else -> {
					// Generate an temporary file to store the image
					this.temporaryFile = FileProvider.getUriForFile(this.eventListenerFragment?.context!!, this.fileProviderAuthority, this.createTemporaryFile())

					// Create an intent for picking the image
					val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
					intent.putExtra(MediaStore.EXTRA_OUTPUT, this.temporaryFile)

					// Return it
					return intent
				}
			}
		}

		private fun createGalleryPickIntent(): Intent {
			val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)

			// Check the sdk int
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				intent.putExtra(MediaStore.EXTRA_OUTPUT, this.temporaryFile)
			} else {
				intent.putExtra("return-data", true)
			}

			// Inline cropping option
			if (this.optionCrop) {
				intent.putExtra("crop", true)

				when (this.optionCropType) {
					EasyImagePicker.CropImageType.FREE_CROPPING -> {
						intent.putExtra("scale", true)
					}
					EasyImagePicker.CropImageType.ASPECT_RATIO -> {
						intent.putExtra("aspectX", this.cropAspectRatioHorizontal)
						intent.putExtra("aspectY", this.cropAspectRatioVertical)
					}
					EasyImagePicker.CropImageType.FIXED_SIZE -> {
						intent.putExtra("outputX", this.cropSizeWidth)
						intent.putExtra("outputY", this.cropSizeHeight)
					}
				}

				intent.putExtra("return-data", true)
			}

			return intent
		}

		private fun showImagePickerIntent() {
			// This inner function queries the package manager for applications able to handle the given intent
			fun queryApplications(intent: Intent, list: ArrayList<Intent>) {
				val packageManager = this.context.packageManager

				packageManager.queryIntentActivities(intent, 0).forEach {
					val targetIntent = Intent(intent)
					targetIntent.component = ComponentName(it.activityInfo.packageName, it.activityInfo.name)

					list.add(targetIntent)
				}
			}

			// Setup the intents
			val galleryPickIntent = this.createGalleryPickIntent()
			val cameraCaptureIntent = this.createCameraCaptureIntent()

			// Search the installed applications able to handle the provided intents
			val intentList = arrayListOf<Intent>()
			queryApplications(galleryPickIntent, intentList)
			queryApplications(cameraCaptureIntent, intentList)

			// Create an intent chooser
			val chooserIntent = Intent.createChooser(intentList.dropLast(1).first(), "Please pick this shit up")
			chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentList)

			// Show it
			this.eventListenerFragment?.startActivityForResult(chooserIntent, INTENT_CHOOSER_REQUEST_CODE)
			/*if (this.activity != null) {
				this.activity!!.startActivityForResult(chooserIntent, INTENT_CHOOSER_REQUEST_CODE)
			} else {
				this.fragment!!.startActivityForResult(chooserIntent, INTENT_CHOOSER_REQUEST_CODE)
			}*/
		}

		//</editor-fold>

	}

	/**
	 * This method receives an activity and retrieves an image picker instance
	 **/
	fun with(activity: AppCompatActivity): Picker {
		return Picker(activity)
	}

	/**
	 * This method receives a fragment and retrieves an image picker instance
	 **/
	fun with(fragment: Fragment): Picker {
		return Picker(fragment)
	}

}