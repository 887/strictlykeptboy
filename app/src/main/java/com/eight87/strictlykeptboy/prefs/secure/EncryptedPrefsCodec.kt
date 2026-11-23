package com.eight87.strictlykeptboy.prefs.secure

import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.util.Base64

/** A typed preference value, one variant per SharedPreferences storage type. */
sealed interface PrefValue {
    data class Str(val value: String) : PrefValue
    data class StrSet(val values: Set<String>) : PrefValue
    data class IntV(val value: Int) : PrefValue
    data class LongV(val value: Long) : PrefValue
    data class FloatV(val value: Float) : PrefValue
    data class Bool(val value: Boolean) : PrefValue
}

/**
 * Pure (JVM-testable) codec for the encrypted preference files.
 *
 * The on-disk layout is byte-for-byte the one androidx.security's (now deprecated)
 * EncryptedSharedPreferences wrote, so files created by earlier builds keep decrypting
 * with no migration step, and files written by this codec stay readable by those builds:
 *
 * - stored key   = Base64(DAEAD.encryptDeterministically(utf8(key), utf8(fileName)))
 * - stored value = Base64(AEAD.encrypt(payload, utf8(storedKey)))
 * - payload      = big-endian `int typeId` followed by the type's body:
 *   - 0 String     `int byteLength | utf8 bytes`
 *   - 1 String set `(int byteLength | utf8 bytes)*`
 *   - 2 Int `int`, 3 Long `long`, 4 Float `float`, 5 Boolean `byte (0|1)`
 *
 * A `null` key, a `null` String and a `null` String set are written as the
 * [NULL_VALUE] sentinel (a one-element set for the set case), as the legacy library did.
 */
class EncryptedPrefsCodec(
    private val keyCipher: DeterministicAead,
    private val valueCipher: Aead,
    private val fileName: String,
) {
    /** The deterministic storage key under which [key] is persisted. */
    fun storageKey(key: String?): String =
        b64.encodeToString(
            keyCipher.encryptDeterministically(
                (key ?: NULL_VALUE).toByteArray(Charsets.UTF_8),
                fileName.toByteArray(Charsets.UTF_8),
            ),
        )

    /** Reverses [storageKey]; used to enumerate a file's entries. */
    fun decodeStorageKey(storageKey: String): KeyDecodeResult {
        val plain = try {
            keyCipher.decryptDeterministically(b64d.decode(storageKey), fileName.toByteArray(Charsets.UTF_8))
        } catch (e: GeneralSecurityException) {
            return KeyDecodeResult.Undecryptable(e)
        } catch (e: IllegalArgumentException) {
            return KeyDecodeResult.Undecryptable(e)
        }
        val key = String(plain, Charsets.UTF_8)
        return KeyDecodeResult.Key(if (key == NULL_VALUE) null else key)
    }

    /** Encrypts [value] (or the null sentinel) for storage under [storageKey]. */
    fun encrypt(storageKey: String, value: PrefValue): String =
        b64.encodeToString(valueCipher.encrypt(payload(value), storageKey.toByteArray(Charsets.UTF_8)))

    /** Encrypts the legacy null-string sentinel (`putString(key, null)`). */
    fun encryptNullString(storageKey: String): String = encrypt(storageKey, PrefValue.Str(NULL_VALUE))

    /** Encrypts the legacy null-set sentinel (`putStringSet(key, null)`). */
    fun encryptNullStringSet(storageKey: String): String = encrypt(storageKey, PrefValue.StrSet(setOf(NULL_VALUE)))

    /** Decrypts a stored value; [DecodeResult] distinguishes null sentinels, tampering and bad payloads. */
    fun decrypt(storageKey: String, stored: String): DecodeResult {
        val plain = try {
            valueCipher.decrypt(b64d.decode(stored), storageKey.toByteArray(Charsets.UTF_8))
        } catch (e: GeneralSecurityException) {
            return DecodeResult.Undecryptable(e)
        } catch (e: IllegalArgumentException) {
            return DecodeResult.Undecryptable(e)
        }
        return try {
            parse(ByteBuffer.wrap(plain))
        } catch (_: BufferUnderflowException) {
            DecodeResult.Malformed
        }
    }

    private fun parse(buf: ByteBuffer): DecodeResult =
        when (val type = buf.getInt()) {
            TYPE_STRING -> {
                val s = readString(buf) ?: return DecodeResult.Malformed
                if (s == NULL_VALUE) DecodeResult.NullValue else DecodeResult.Value(PrefValue.Str(s))
            }
            TYPE_STRING_SET -> {
                val out = LinkedHashSet<String>()
                while (buf.hasRemaining()) out += readString(buf) ?: return DecodeResult.Malformed
                if (out == setOf(NULL_VALUE)) DecodeResult.NullValue else DecodeResult.Value(PrefValue.StrSet(out))
            }
            TYPE_INT -> DecodeResult.Value(PrefValue.IntV(buf.getInt()))
            TYPE_LONG -> DecodeResult.Value(PrefValue.LongV(buf.getLong()))
            TYPE_FLOAT -> DecodeResult.Value(PrefValue.FloatV(buf.getFloat()))
            TYPE_BOOLEAN -> DecodeResult.Value(PrefValue.Bool(buf.get() != 0.toByte()))
            else -> DecodeResult.UnknownType(type)
        }

    private fun readString(buf: ByteBuffer): String? {
        val len = buf.getInt()
        if (len < 0 || len > buf.remaining()) return null
        val bytes = ByteArray(len)
        buf.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun payload(value: PrefValue): ByteArray =
        when (value) {
            is PrefValue.Str -> {
                val bytes = value.value.toByteArray(Charsets.UTF_8)
                ByteBuffer.allocate(Int.SIZE_BYTES * 2 + bytes.size)
                    .putInt(TYPE_STRING).putInt(bytes.size).put(bytes).array()
            }
            is PrefValue.StrSet -> {
                val parts = value.values.map { it.toByteArray(Charsets.UTF_8) }
                val buf = ByteBuffer.allocate(Int.SIZE_BYTES * (1 + parts.size) + parts.sumOf { it.size })
                buf.putInt(TYPE_STRING_SET)
                parts.forEach { buf.putInt(it.size).put(it) }
                buf.array()
            }
            is PrefValue.IntV -> ByteBuffer.allocate(Int.SIZE_BYTES * 2).putInt(TYPE_INT).putInt(value.value).array()
            is PrefValue.LongV ->
                ByteBuffer.allocate(Int.SIZE_BYTES + Long.SIZE_BYTES).putInt(TYPE_LONG).putLong(value.value).array()
            is PrefValue.FloatV ->
                ByteBuffer.allocate(Int.SIZE_BYTES + Float.SIZE_BYTES).putInt(TYPE_FLOAT).putFloat(value.value).array()
            is PrefValue.Bool ->
                ByteBuffer.allocate(Int.SIZE_BYTES + 1).putInt(TYPE_BOOLEAN).put(if (value.value) 1 else 0).array()
        }

    sealed interface DecodeResult {
        data class Value(val value: PrefValue) : DecodeResult
        /** The legacy null sentinel — the caller should treat the entry as absent. */
        data object NullValue : DecodeResult
        data class Undecryptable(val cause: Exception) : DecodeResult
        data class UnknownType(val typeId: Int) : DecodeResult
        data object Malformed : DecodeResult
    }

    sealed interface KeyDecodeResult {
        data class Key(val key: String?) : KeyDecodeResult
        data class Undecryptable(val cause: Exception) : KeyDecodeResult
    }

    companion object {
        /** Sentinel the legacy library used for null keys / values. */
        const val NULL_VALUE = "__NULL__"

        /** Keyset entries Tink's AndroidKeysetManager keeps in the same file (plaintext keys). */
        const val KEY_KEYSET_PREF = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
        const val VALUE_KEYSET_PREF = "__androidx_security_crypto_encrypted_prefs_value_keyset__"

        fun isReservedKey(key: String?): Boolean = key == KEY_KEYSET_PREF || key == VALUE_KEYSET_PREF

        private const val TYPE_STRING = 0
        private const val TYPE_STRING_SET = 1
        private const val TYPE_INT = 2
        private const val TYPE_LONG = 3
        private const val TYPE_FLOAT = 4
        private const val TYPE_BOOLEAN = 5
        private val b64: Base64.Encoder = Base64.getEncoder()
        private val b64d: Base64.Decoder = Base64.getDecoder()
    }
}
