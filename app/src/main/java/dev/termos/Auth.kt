package dev.termos

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Password storage (salted PBKDF2) + tiny key/value store for settings. */
object Auth {
    private fun p(c: Context) = c.getSharedPreferences("termos", Context.MODE_PRIVATE)

    fun hasPassword(c: Context) = p(c).contains("hash")

    fun setPassword(c: Context, pw: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p(c).edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash(pw, salt), Base64.NO_WRAP))
            .apply()
    }

    fun check(c: Context, pw: String): Boolean {
        val salt = Base64.decode(p(c).getString("salt", "") ?: return false, Base64.NO_WRAP)
        val want = Base64.decode(p(c).getString("hash", "") ?: return false, Base64.NO_WRAP)
        return MessageDigest.isEqual(want, hash(pw, salt))
    }

    private fun hash(pw: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pw.toCharArray(), salt, 120_000, 256)).encoded
}

object Store {
    private fun p(c: Context) = c.getSharedPreferences("termos_cfg", Context.MODE_PRIVATE)
    fun get(c: Context, k: String): String? = p(c).getString(k, null)
    fun put(c: Context, k: String, v: String) = p(c).edit().putString(k, v).apply()
}
