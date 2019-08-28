package quevedo.soares.leandro.lib.view

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import android.view.*
import quevedo.soares.leandro.lib.util.Log

/**
 * @author Leandro Soares Quevedo
 * @author leandrosoaresquevedo@gmail.com
 * @since 2019-07-01
 */
abstract class BaseDialog : DialogFragment() {

	private lateinit var rootView: View
	private var dismissed: Boolean = false

	//<editor-fold defaultstate="Collapsed" desc="Abstract event handlers">

	/**
	 * Initializes variables and components
	 */
	protected abstract fun onInitValues()

	/**
	 * Destroys the fragment
	 */
	protected abstract fun onDispose()

	/**
	 * Loads services and resources
	 */
	protected abstract fun onPreload(inflater: LayoutInflater, parent: ViewGroup?): View

	//</editor-fold>

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		this.rootView = this.onPreload(inflater, container)

		Handler().post(this::onInitValues)

		return this.rootView
	}

	@Suppress("LiftReturnOrAssignment")
	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val dialog = object : Dialog(context, theme) {
			override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
				if (dismissed) return false
				else return super.dispatchTouchEvent(ev)
			}
		}

		this.initializeDialog(dialog, initialized = false)

		return dialog
	}

	override fun onStart() {
		super.onStart()

		this.initializeDialog(dialog, true)

		rootView.measure(0, 0)
	}

	private fun initializeDialog(dialog: Dialog, initialized: Boolean) {
		dialog.setCancelable(true)
		dialog.setCanceledOnTouchOutside(false)

		dialog.window?.let {
			val layoutParams = it.attributes
			layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
			layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
			layoutParams.gravity = Gravity.CENTER
			layoutParams.dimAmount = 0f

			it.attributes = layoutParams

			it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

		}

		dialog.setOnDismissListener {
			dismiss()
		}
	}

	override fun dismiss() {
		try {
			if (this.dismissed) return
			this.dismissed = true

			this.onDispose()

			super.dismiss()
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	override fun show(manager: FragmentManager, tag: String) {
		try {
			val ft = manager.beginTransaction()
			ft.add(this, tag)
			ft.commit()
		} catch (e: IllegalStateException) {
			Log.e("Exception", e)
		}

	}

}