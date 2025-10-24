package io.github.sensbin.mina.kcp.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Aes192 {
    private const val KEY_BITS = 192
    private const val IV_BYTES = 16

    fun generateKey(): SecretKey {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(KEY_BITS)
        return kg.generateKey()
    }

    fun deriveKeyFromPassword(password: String, salt: ByteArray = "sensebin@github".toByteArray()): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 192)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun encrypt(key: SecretKey, plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val ct = cipher.doFinal(plain)
        return iv + ct // IV || ciphertext
    }

    fun decrypt(key: SecretKey, packed: ByteArray): ByteArray {
        val iv = packed.copyOfRange(0, IV_BYTES)
        val ct = packed.copyOfRange(IV_BYTES, packed.size)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        return cipher.doFinal(ct)
    }
}
