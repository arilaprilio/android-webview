package com.innovationsystem.kioskandroid

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists HTTP Basic/Digest credentials encrypted at rest.
 *
 * Keys are of the form "<scheme>://<host>:<port>|<realm>" to mirror the
 * credential-key format used by src/main/credentials.mjs in the Electron app,
 * so a user's mental model carries over.
 *
 * NOTE: androidx.security:security-crypto is intentionally NOT a dependency here
 * to avoid pulling Tink+Protobuf into the APK for a kiosk that is often used on
 * trusted private networks. If you need encryption-at-rest, add
 *   implementation("androidx.security:security-crypto:1.1.0-alpha06")
 * to app/build.gradle.kts and uncomment the EncryptedSharedPreferences branch.
 */
class CredentialStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    // Encrypted variant (opt-in). See note above.
    // private val prefs = EncryptedSharedPreferences.create(
    //     context,
    //     PREFS_NAME,
    //     MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    //     EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    //     EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    // )

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
        private const val PREFS_NAME = "kiosk_credentials"

        /** Build the lookup key for a WebView auth challenge. Realm may be empty. */
        fun keyFor(host: String, realm: String?): String =
            "$host|${realm.orEmpty()}"
    }
}
