package com.innovationsystem.kioskandroid

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.webkit.HttpAuthHandler
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Mirrors the HTTP auth dialog from src/main/authentication.mjs in the Electron app.
 *
 * The WebView HttpAuthHandler API expects either proceed(user, pass) or cancel();
 * closing/escaping the dialog MUST call cancel() otherwise the request hangs
 * and the WebView keeps the connection open indefinitely.
 */
object AuthDialog {

    fun show(
        context: Context,
        host: String,
        realm: String?,
        handler: HttpAuthHandler,
        rememberDefault: Boolean,
        onRemember: (user: String, pass: String) -> Unit,
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_auth, null, false)
        val message = view.findViewById<TextView>(R.id.auth_message)
        val userField = view.findViewById<EditText>(R.id.auth_user)
        val passField = view.findViewById<EditText>(R.id.auth_password)
        val remember = view.findViewById<CheckBox>(R.id.auth_remember).apply {
            isChecked = rememberDefault
            isEnabled = rememberDefault // mirrors the "Remember credentials" global toggle
        }

        message.text = context.getString(R.string.auth_message, host, realm ?: "")

        val dialog: Dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.auth_title)
            .setView(view)
            .setCancelable(false) // force an explicit button press — same contract as Electron
            .setPositiveButton(R.string.auth_sign_in) { _, _ ->
                val u = userField.text.toString()
                val p = passField.text.toString()
                if (remember.isChecked) onRemember(u, p)
                handler.proceed(u, p)
            }
            .setNegativeButton(R.string.auth_cancel) { _, _ -> handler.cancel() }
            .create()

        dialog.setOnDismissListener {
            // Safety: if the dialog is dismissed via any path other than the buttons,
            // the handler would otherwise leak. Harmless to call cancel() after proceed()
            // because WebKit guards it.
            handler.cancel()
        }
        dialog.show()
    }
}
