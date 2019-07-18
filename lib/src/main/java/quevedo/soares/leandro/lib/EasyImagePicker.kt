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
import android.os.*
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.util.Log
import quevedo.soares.leandro.lib.enumerator.CropImageType
import quevedo.soares.leandro.lib.enumerator.ImageSource
import quevedo.soares.leandro.lib.view.CropImageDialog
import quevedo.soares.leandro.lib.view.EventStealerFragment
import java.io.File
import java.util.*

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

	class ImagePickerRequest {

		//<editor-fold defaultstate="Collapsed" desc="Context variables">

		private var activity: AppCompatActivity? = null
		private var fragment: Fragment? = null
		private val context: Context
			get() {
				return when {
					eventListenerFragment != null && eventListenerFragment!!.context != null -> eventListenerFragment!!.context!!

					activity != null -> activity!!

					else -> fragment!!.context!!
				}
			}

		private val fragmentManager: FragmentManager by lazy {
			if (activity != null) {
				return@lazy activity!!.supportFragmentManager!!
			} else {
				return@lazy fragment!!.fragmentManager!!
			}
		}

		private var eventListenerFragment: EventStealerFragment? = null

		//</editor-fold>

		//<editor-fold defaultstate="Collapsed" desc="Storage variables">

		private var imageSource: ImageSource = ImageSource.ANY

		private var temporaryFile: Uri? = null
		private val fileProviderAuthority: String by lazy { this.context.applicationContext.packageName + ".provider" }

		//</editor-fold>

		//<editor-fold defaultstate="Collapsed" desc="Callbacks">

		private var successListener: ((Bitmap?) -> Unit)? = null
		private var errorListener: ((Throwable) -> Unit)? = null
		private var handled: Boolean = false

		//</editor-fold>

		//<editor-fold defaultstate="Collapsed" desc="Cropping options">
		private var optionCrop: Boolean = false
		private var waitingForCrop: Boolean = false
		internal var optionCropType: CropImageType = CropImageType.FREE_CROPPING

		internal var cropAspectRatioHorizontal: Int = 0
		internal var cropAspectRatioVertical: Int = 0

		internal var cropSizeWidth: Int = 0
		internal var cropSizeHeight: Int = 0

		internal var cropMinimumSizeWidth: Int? = null
		internal var cropMinimumSizeHeight: Int? = null

		internal var cropMaximumSizeWidth: Int? = null
		internal var cropMaximumSizeHeight: Int? = null
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

		/***
		 * This method creates the Fragment that will steal the OnActivityResult and OnPermissionsResult
		 ***/
		private fun createEventListener() {
			// Find the fragment
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

		private fun isCropAvailable(): Boolean {
			return false

			val packageManager = this.context.packageManager
			val intent = createCameraCaptureIntent()

			val list = arrayListOf<Intent>()
			packageManager.queryIntentActivities(intent, 0).forEach {
				val targetIntent = Intent(intent)
				targetIntent.component = ComponentName(it.activityInfo.packageName, it.activityInfo.name)

				list.add(targetIntent)
			}

			return list.isEmpty()
		}

		/***
		 * Enables the free-cropping option
		 ***/
		fun crop(): ImagePickerRequest {
			this.optionCrop = true
			this.optionCropType = CropImageType.FREE_CROPPING

			return this
		}

		/***
		 * Locks the image size when cropping
		 *
		 * Also, if cropping is disabled this option enables it
		 * @see crop
		 ***/
		fun size(width: Int, height: Int): ImagePickerRequest {
			this.optionCrop = true
			this.optionCropType = CropImageType.FIXED_SIZE
			this.cropSizeWidth = width
			this.cropSizeHeight = height

			return this
		}

		/***
		 * Sets the minimum image size when cropping
		 *
		 * Also, if cropping is disabled this option enables it
		 * @see crop
		 ***/
		fun minimumSize(width: Int, height: Int): ImagePickerRequest {
			this.optionCrop = true
			this.cropMinimumSizeWidth = width
			this.cropMinimumSizeHeight = height

			this.validateAspectRatio()

			return this
		}

		/***
		 * Sets the maximum image size when cropping
		 *
		 * Also, if cropping is disabled this option enables it
		 * @see crop
		 ***/
		fun maximumSize(width: Int, height: Int): ImagePickerRequest {
			this.optionCrop = true
			this.cropMaximumSizeWidth = width
			this.cropMaximumSizeHeight = height

			this.validateAspectRatio()

			return this
		}

		/***
		 * Locks the image aspect ratio when cropping
		 *
		 * Also, if cropping is disabled this option enables it
		 * @see crop
		 ***/
		fun aspectRatio(horizontal: Int, vertical: Int): ImagePickerRequest {
			this.optionCrop = true
			this.optionCropType = CropImageType.ASPECT_RATIO
			this.cropAspectRatioHorizontal = horizontal
			this.cropAspectRatioVertical = vertical

			this.validateAspectRatio()

			return this
		}

		/***
		 * Starts the image picker
		 *
		 * @param source Provides the source of the given image, can be CAMERA, GALLERY or ANY (Any meaning that user will select which source he wants)
		 ***/
		fun pick(source: ImageSource = ImageSource.ANY) {
			this.imageSource = source

			this.requestImage()
		}

		//</editor-fold>

		//<editor-fold defaultstate="Collapsed" desc="Listener methods">

		/***
		 * Sets the listener to be called when the bitmap is selected
		 *
		 * Null when none
		 ***/
		fun listener(listener: (Bitmap?) -> Unit): ImagePickerRequest {
			this.successListener = listener
			this.errorListener = {
				listener(null)
			}

			return this
		}

		/***
		 * Sets the listener to be called when the bitmap is selected
		 *
		 * Null when none
		 ***/
		fun listener(listener: (Throwable?, Bitmap?) -> Unit): ImagePickerRequest {
			this.successListener = {
				listener(null, it)
			}

			this.errorListener = {
				listener(it, null)
			}

			return this
		}

		/***
		 * Sets the listener to be called when the bitmap is selected
		 *
		 * Null when none
		 ***/
		fun listener(success: (Bitmap?) -> Unit, error: ((Throwable) -> Unit)?): ImagePickerRequest {
			this.successListener = success

			if (error != null)
				this.errorListener = error

			return this
		}

		//</editor-fold>

		//<editor-fold defaultstate="Collapsed" desc="Permission handling">

		private fun handlePermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: Array<Int>): Boolean {
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
			if (this.isPermissionGranted(
							Manifest.permission.READ_EXTERNAL_STORAGE,
							Manifest.permission.WRITE_EXTERNAL_STORAGE,
							Manifest.permission.CAMERA
					)
			) {
				return true
			} else {
				this.eventListenerFragment?.requestPermissions(
						arrayOf(
								Manifest.permission.READ_EXTERNAL_STORAGE,
								Manifest.permission.WRITE_EXTERNAL_STORAGE,
								Manifest.permission.CAMERA
						),
						PERMISSION_REQUEST_CODE
				)

				return false
			}
		}

		private fun handleError(message: String) {
			this.handled = true
			this.errorListener?.invoke(Exception(message))
		}

		//</editor-fold>

		private fun validateAspectRatio() {
			fun sizeMatchesAspectRatio(width: Int, height: Int, aspectRatio: Float): Boolean {
				val ratioHeight = width * aspectRatio
				return height.toFloat() == ratioHeight
			}

			// Ignores this validation when the aspect ratio is not set
			if (optionCropType != CropImageType.ASPECT_RATIO) return

			// Calculate the aspect ratio fraction
			val aspectRatio = (this.cropAspectRatioHorizontal / this.cropAspectRatioVertical).toFloat()

			// Check if the provided minimum width is on the locked ratio
			if (cropMinimumSizeWidth != null && cropMinimumSizeHeight != null) {
				if (!sizeMatchesAspectRatio(cropMinimumSizeWidth!!, cropMinimumSizeHeight!!, aspectRatio)) {
					Log.e("EasyImagePicker", "The provided minimum size does not match the aspect ratio")

					throw IllegalArgumentException("The provided minimum size does not match the aspect ratio")
				} else {
					Log.d("EasyImagePicker", "The provided minimum size matches the aspect ratio")
				}
			}

			// Check if the provided maximum width is on the locked ratio
			if (cropMaximumSizeWidth != null && cropMaximumSizeHeight != null) {
				if (!sizeMatchesAspectRatio(cropMaximumSizeWidth!!, cropMaximumSizeHeight!!, aspectRatio)) {
					Log.e("EasyImagePicker", "The provided maximum size does not match the aspect ratio")

					throw IllegalArgumentException("The provided maximum size does not match the aspect ratio")
				} else {
					Log.d("EasyImagePicker", "The provided maximum size matches the aspect ratio")
				}
			}
		}

		private fun requestImage() {
			// If the fragment is created but not added
			if (!this.eventListenerFragment?.isAdded!!) {
				Handler(Looper.getMainLooper()).post(this::requestImage)
				return
			}


			// First of all, just check the manifest permissions
			if (this.requestPermissions()) {
				when (this.imageSource) {
					ImageSource.GALLERY -> {
						Log.d("EasyImagePicker", "Requesting image from gallery...")
						val intent = this.createGalleryPickIntent()

						this.eventListenerFragment?.startActivityForResult(intent, GALLERY_PICKER_REQUEST_CODE)
					}
					ImageSource.CAMERA -> {
						Log.d("EasyImagePicker", "Requesting camera image capture...")

						val intent = this.createCameraCaptureIntent()

						this.eventListenerFragment?.startActivityForResult(intent, CAMERA_CAPTURE_REQUEST_CODE)
					}
					ImageSource.ANY -> {
						Log.d("EasyImagePicker", "Requesting user image source...")
						this.showImagePickerIntent()
					}
				}
			} else {
				Log.d("EasyImagePicker", "The permissions were denied or not_requested. Waiting them to be granted!")
			}
		}

		//<editor-fold defaultstate="Collapsed" desc="Bitmap handling">

		private fun loadBitmapFromUri(uri: Uri): Bitmap? {
			// Loads the bitmap from the URI
			var rawBitmap = MediaStore.Images.Media.getBitmap(this.context.contentResolver, uri)
			if (rawBitmap == null) rawBitmap =
					BitmapFactory.decodeStream(this.context.contentResolver.openInputStream(uri))

			return if (rawBitmap != null)
				this.handleBitmapRotation(uri, rawBitmap)
			else
				null
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

				return when (exifInterface.getAttributeInt(
						ExifInterface.TAG_ORIENTATION,
						ExifInterface.ORIENTATION_UNDEFINED
				)) {
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

		//</editor-fold>

		//<editor-fold defaultstate="Collapsed" desc="Intent handling">

		private fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
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

		@Suppress("CascadeIf")
		private fun handleBitmapResponse(response: Intent?) {
			fun handleBitmap(bitmap: Bitmap?) {
				// Check if it is waiting for crop
				if (this.waitingForCrop && bitmap != null) {
					// Open the crop dialog
					if (activity != null) {
						CropImageDialog.show(activity!!, this, bitmap)
					} else if (fragment != null) {
						CropImageDialog.show(activity!!, this, bitmap)
					}
				} else {
					this.successListener?.invoke(bitmap)
				}
			}

			if (response != null) {
				when {
					// Check for the "return-data" behaviour
					response.hasExtra("data") -> {
						val image = response.extras!!.get("data") as Bitmap

						handleBitmap(image)
					}

					// Check for the URI "intent-data" behaviour
					response.data != null -> {
						val result = this.loadBitmapFromUri(response.data!!)

						handleBitmap(result)
					}

					// Check for the "output" extra
					response.hasExtra(MediaStore.EXTRA_OUTPUT) -> {
						val image = response.extras!!.get(MediaStore.EXTRA_OUTPUT) as? Bitmap

						// Check if an image was provided
						if (image == null) {
							// Otherwise, check if a file path was provided
							(response.extras!!.get(MediaStore.EXTRA_OUTPUT) as? Uri)?.let { uri ->
								this.temporaryFile = uri
								handleBitmap(this.loadBitmapFromUri(uri))
							}
						} else {
							handleBitmap(image)
						}
					}

					else -> {
						this.handleError("Unable to get the gallery output!")
					}
				}
			} else if (this.temporaryFile != null) {
				val result = this.loadBitmapFromUri(this.temporaryFile!!)

				handleBitmap(result)
			} else {
				this.handleError("Some error has occurred!")
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

		private fun setupCroppingIntent(intent: Intent) {
			// Inline cropping option
			if (this.optionCrop) {
				if (this.isCropAvailable()) {
					intent.putExtra("crop", "true")

					when (this.optionCropType) {
						CropImageType.FREE_CROPPING -> {
							intent.putExtra("scale", "true")
						}
						CropImageType.ASPECT_RATIO -> {
							intent.putExtra("aspectX", this.cropAspectRatioHorizontal)
							intent.putExtra("aspectY", this.cropAspectRatioVertical)
						}
						CropImageType.FIXED_SIZE -> {
							intent.putExtra("outputX", this.cropSizeWidth)
							intent.putExtra("outputY", this.cropSizeHeight)
						}
					}

					intent.putExtra("return-data", true)
				} else {
					waitingForCrop = true
				}
			}
		}

		private fun createCameraCaptureIntent(): Intent {
			when {
				Build.VERSION.SDK_INT <= Build.VERSION_CODES.N -> {
					val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

					this.setupCroppingIntent(intent)

					// For old API`s and file exposure without FileProvider
					this.disableFileUriExposureCrash()

					return intent
				}
				Build.VERSION.SDK_INT <= Build.VERSION_CODES.M -> {
					val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

					this.setupCroppingIntent(intent)

					return intent
				}
				else -> {
					// Generate an temporary file to store the image
					this.temporaryFile = FileProvider.getUriForFile(
							this.context,
							this.fileProviderAuthority,
							this.createTemporaryFile()
					)

					// Create an intent for picking the image
					val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
					intent.putExtra(MediaStore.EXTRA_OUTPUT, this.temporaryFile)

					this.setupCroppingIntent(intent)

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

				// For old API`s and file exposure without FileProvider
				this.disableFileUriExposureCrash()
			}

			this.setupCroppingIntent(intent)

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
		}

		private fun disableFileUriExposureCrash() {
			// For old API`s and file exposure without FileProvider
			try {
				val method = StrictMode::class.java.getMethod("disableDeathOnFileUriExposure")
				method.invoke(null)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}

		//</editor-fold>

	}

	/***
	 * This method receives an activity and retrieves an image picker instance
	 *
	 * @param activity The parent
	 ***/
	fun with(activity: AppCompatActivity): ImagePickerRequest {
		return ImagePickerRequest(activity)
	}

	/***
	 * This method receives a fragment and retrieves an image picker instance
	 *
	 * @param fragment The parent
	 ***/
	fun with(fragment: Fragment): ImagePickerRequest {
		return ImagePickerRequest(fragment)
	}

}