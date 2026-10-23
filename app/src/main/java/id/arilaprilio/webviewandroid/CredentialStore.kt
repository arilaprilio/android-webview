package id.arilaprilio.webviewandroid

import android.content.Context

/**
 * Persists HTTP Basic/Digest credentials using SharedPreferences.
 *
 * Keys are of the form "<host>|<realm>" to mirror the credential-key format
 * used by the Electron version, so a user's mental model carries over.
 */
class CredentialStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Credential(val username: String, val password: String)

    fun put(key: String, cred: Credential) {
        prefs.edit()
            .putString("$key.u", cred.username)
            .putString("$key.p", cred.password)
            .apply()
    }

    fun get(key: String): Credential? {
        val u = prefs.getString("$key.u", null) ?: return null
        val p = prefs.getString("$key.p", null) ?: return null
        return Credential(u, p)
    }

    fun remove(key: String) {
        prefs.edit().remove("$key.u").remove("$key.p").apply()
    }

    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        private const val PREFS_NAME = "webview_credentials"

        /** Build the lookup key for a WebView auth challenge. Realm may be empty. */
        fun keyFor(host: String, realm: String?): String =
            "$host|${realm.orEmpty()}"
    }
}
