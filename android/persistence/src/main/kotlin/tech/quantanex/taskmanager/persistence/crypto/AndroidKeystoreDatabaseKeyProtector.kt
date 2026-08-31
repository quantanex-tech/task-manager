package tech.quantanex.taskmanager.persistence.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreDatabaseKeyProtector(
    private val alias: String = DEFAULT_ALIAS,
) : DatabaseKeyProtector {
    override val capability: KeystoreCapability
        get() = KeystoreCapability(hardwareBacked = readHardwareBackedCapability())

    override fun wrap(plaintext: ByteArray): ByteArray = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(plaintext)
        encode(cipher.iv, ciphertext)
    } catch (error: KeyPermanentlyInvalidatedException) {
        throw DatabaseKeyProtectionException(DatabaseKeyBootstrapError.KeyInvalidated, error)
    } catch (error: Exception) {
        throw DatabaseKeyProtectionException(DatabaseKeyBootstrapError.KeyUnavailable, error)
    }

    override fun unwrap(blob: ByteArray): ByteArray = try {
        val wrapped = decode(blob)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, wrapped.iv))
        cipher.doFinal(wrapped.ciphertext)
    } catch (error: KeyPermanentlyInvalidatedException) {
        throw DatabaseKeyProtectionException(DatabaseKeyBootstrapError.KeyInvalidated, error)
    } catch (error: IllegalArgumentException) {
        throw DatabaseKeyProtectionException(DatabaseKeyBootstrapError.CorruptKeyMaterial, error)
    } catch (error: javax.crypto.AEADBadTagException) {
        throw DatabaseKeyProtectionException(DatabaseKeyBootstrapError.CorruptKeyMaterial, error)
    } catch (error: Exception) {
        throw DatabaseKeyProtectionException(DatabaseKeyBootstrapError.KeyUnavailable, error)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    @Suppress("DEPRECATION")
    private fun readHardwareBackedCapability(): Boolean? = try {
        val secretKey = getOrCreateSecretKey()
        val factory = SecretKeyFactory.getInstance(secretKey.algorithm, ANDROID_KEYSTORE)
        val keyInfo = factory.getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo
        keyInfo.isInsideSecureHardware
    } catch (_: Exception) {
        null
    }

    private fun encode(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(iv.size <= UShort.MAX_VALUE.toInt())
        return ByteBuffer.allocate(MAGIC.size + Integer.BYTES + java.lang.Short.BYTES + iv.size + ciphertext.size)
            .put(MAGIC)
            .putInt(WRAPPER_VERSION)
            .putShort(iv.size.toShort())
            .put(iv)
            .put(ciphertext)
            .array()
    }

    private fun decode(blob: ByteArray): WrappedKeyBlob {
        if (blob.size < MAGIC.size + Integer.BYTES + java.lang.Short.BYTES) {
            throw IllegalArgumentException("Wrapped key blob is too short")
        }
        val buffer = ByteBuffer.wrap(blob)
        val magic = ByteArray(MAGIC.size)
        buffer.get(magic)
        if (!magic.contentEquals(MAGIC)) throw IllegalArgumentException("Unexpected wrapped key magic")
        if (buffer.int != WRAPPER_VERSION) throw IllegalArgumentException("Unsupported wrapped key version")
        val ivSize = buffer.short.toInt()
        if (ivSize <= 0 || ivSize > buffer.remaining()) throw IllegalArgumentException("Invalid wrapped key IV")
        val iv = ByteArray(ivSize)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)
        if (ciphertext.isEmpty()) throw IllegalArgumentException("Missing wrapped key ciphertext")
        return WrappedKeyBlob(iv, ciphertext)
    }

    private data class WrappedKeyBlob(val iv: ByteArray, val ciphertext: ByteArray)

    companion object {
        const val DEFAULT_ALIAS = "task-manager-database-key-wrapper-v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val WRAPPER_VERSION = 1
        private val MAGIC = byteArrayOf(0x54, 0x4d, 0x4b, 0x57)
    }
}
