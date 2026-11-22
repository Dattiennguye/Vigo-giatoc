package vn.vigo.nepnha.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** epoch millis */
    val expiresAt: Long,
    val userId: String,
    val email: String?,
)

/**
 * Lưu phiên đăng nhập đã mã hóa AES-GCM bằng khóa trong Android Keystore (khóa không rời phần cứng/hệ thống).
 * Nếu giải mã thất bại (ví dụ người dùng đổi khóa màn hình trên một số máy) thì coi như đã đăng xuất.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    @Synchronized
    fun load(): Session? = try {
        val iv = prefs.getString("iv", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val data = prefs.getString("data", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        if (iv == null || data == null) {
            null
        } else {
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val o = JSONObject(String(c.doFinal(data), Charsets.UTF_8))
            Session(
                accessToken = o.getString("a"),
                refreshToken = o.getString("r"),
                expiresAt = o.getLong("e"),
                userId = o.getString("u"),
                email = o.optString("m").takeIf { it.isNotEmpty() },
            )
        }
    } catch (_: Exception) {
        clear()
        null
    }

    @Synchronized
    fun save(s: Session) {
        val json = JSONObject()
            .put("a", s.accessToken).put("r", s.refreshToken).put("e", s.expiresAt)
            .put("u", s.userId).put("m", s.email ?: "")
            .toString()
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val enc = c.doFinal(json.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(enc, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val ALIAS = "nepnha_session_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
