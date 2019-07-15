package quevedo.soares.leandro.lib.view

import android.annotation.SuppressLint
import android.content.Intent
import android.support.v4.app.Fragment

/**
 * @author Leandro Soares Quevedo
 * @author leandro.soares@operacao.rcadigital.com.br
 * @since 2019-07-12
 */
@SuppressLint("ValidFragment")
class EventStealerFragment : Fragment {

	private var onPermissionsResultListener: ((Int, Array<out String>, IntArray) -> Unit)? = null
	private var onActivityResultListener: ((Int, Int, Intent?) -> Unit)? = null

	constructor()

	constructor(onPermissionsResultListener: (Int, Array<out String>, IntArray) -> Unit, onActivityResultListener: (Int, Int, Intent?) -> Unit) {
		setup(onPermissionsResultListener, onActivityResultListener)
	}

	fun setup(onPermissionsResultListener: (Int, Array<out String>, IntArray) -> Unit, onActivityResultListener: (Int, Int, Intent?) -> Unit) {
		this.onPermissionsResultListener = onPermissionsResultListener
		this.onActivityResultListener = onActivityResultListener
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		onPermissionsResultListener?.invoke(requestCode, permissions, grantResults)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		onActivityResultListener?.invoke(requestCode, resultCode, data)
	}

}