package com.basar.filebridge.security

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PasswordCrypto {

    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    fun roomKeyFor(password: String): String =
        sha256Hex("filebridge-room-v1:$password")

    private fun aesKeyFor(password: String): SecretKeySpec {
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest("filebridge-aes-v1:$password".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(password: String, plainJson: String): JSONObject {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, aesKeyFor(password), GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherText = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("type", "encrypted")
            .put("data", Base64.encodeToString(iv + cipherText, Base64.NO_WRAP))
    }

    fun decrypt(password: String, envelope: JSONObject): JSONObject {
        val combined = Base64.decode(envelope.getString("data"), Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, IV_BYTES)
        val cipherText = combined.copyOfRange(IV_BYTES, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKeyFor(password), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plainBytes = cipher.doFinal(cipherText)
        return JSONObject(String(plainBytes, Charsets.UTF_8))
    }

    private fun sha256Hex(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
