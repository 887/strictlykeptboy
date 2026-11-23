package com.eight87.strictlykeptboy.prefs.secure

import android.content.SharedPreferences
import android.util.Log
import com.eight87.strictlykeptboy.prefs.secure.EncryptedPrefsCodec.DecodeResult
import com.eight87.strictlykeptboy.prefs.secure.EncryptedPrefsCodec.KeyDecodeResult
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [SharedPreferences] whose keys and values are encrypted with [codec] on top of a plain
 * backing file. Behaviour mirrors androidx.security's deprecated EncryptedSharedPreferences
 * (same wire format, same null sentinels, same "empty set reads as default" quirk, same
 * listener semantics) so every store that used to receive one gets an equivalent object.
 *
 * One deliberate difference: a value that fails to decrypt or parse reads as the caller's
 * default (logged) instead of throwing [SecurityException], so a corrupted entry degrades to
 * "not set" rather than crashing the store's owner.
 */
class TinkEncryptedSharedPreferences(
    private val backing: SharedPreferences,
    private val codec: EncryptedPrefsCodec,
) : SharedPreferences {
    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    private fun read(key: String?): PrefValue? {
        requireNotReserved(key)
        val storageKey = codec.storageKey(key)
        val stored = backing.getString(storageKey, null) ?: return null
        return when (val r = codec.decrypt(storageKey, stored)) {
            is DecodeResult.Value -> r.value
            DecodeResult.NullValue -> null
            is DecodeResult.Undecryptable -> null.also { Log.w(TAG, "undecryptable value", r.cause) }
            is DecodeResult.UnknownType -> null.also { Log.w(TAG, "unknown value type ${r.typeId}") }
            DecodeResult.Malformed -> null.also { Log.w(TAG, "malformed value payload") }
        }
    }

    override fun getAll(): Map<String, *> {
        val out = HashMap<String, Any?>()
        for (storageKey in backing.all.keys) {
            if (EncryptedPrefsCodec.isReservedKey(storageKey)) continue
            when (val k = codec.decodeStorageKey(storageKey)) {
                is KeyDecodeResult.Key -> k.key?.let { out[it] = read(it)?.unwrap() }
                is KeyDecodeResult.Undecryptable -> Log.w(TAG, "undecryptable key", k.cause)
            }
        }
        return out
    }

    override fun getString(key: String?, defValue: String?): String? =
        (read(key) as? PrefValue.Str)?.value ?: defValue

    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? =
        (read(key) as? PrefValue.StrSet)?.values?.takeIf { it.isNotEmpty() } ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = (read(key) as? PrefValue.IntV)?.value ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = (read(key) as? PrefValue.LongV)?.value ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (read(key) as? PrefValue.FloatV)?.value ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (read(key) as? PrefValue.Bool)?.value ?: defValue

    override fun contains(key: String?): Boolean {
        requireNotReserved(key)
        return backing.contains(codec.storageKey(key))
    }

    override fun edit(): SharedPreferences.Editor = Editor(backing.edit())

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners -= listener
    }

    private inner class Editor(private val inner: SharedPreferences.Editor) : SharedPreferences.Editor {
        private val changed = CopyOnWriteArrayList<String?>()

        @Volatile private var clearRequested = false

        private fun put(key: String?, encode: (String) -> String): SharedPreferences.Editor {
            requireNotReserved(key)
            changed += key
            val storageKey = codec.storageKey(key)
            inner.putString(storageKey, encode(storageKey))
            return this
        }

        override fun putString(key: String?, value: String?) =
            put(key) { sk -> if (value == null) codec.encryptNullString(sk) else codec.encrypt(sk, PrefValue.Str(value)) }

        override fun putStringSet(key: String?, values: Set<String>?) =
            put(key) { sk ->
                if (values == null) codec.encryptNullStringSet(sk) else codec.encrypt(sk, PrefValue.StrSet(values))
            }

        override fun putInt(key: String?, value: Int) = put(key) { codec.encrypt(it, PrefValue.IntV(value)) }

        override fun putLong(key: String?, value: Long) = put(key) { codec.encrypt(it, PrefValue.LongV(value)) }

        override fun putFloat(key: String?, value: Float) = put(key) { codec.encrypt(it, PrefValue.FloatV(value)) }

        override fun putBoolean(key: String?, value: Boolean) = put(key) { codec.encrypt(it, PrefValue.Bool(value)) }

        override fun remove(key: String?): SharedPreferences.Editor {
            requireNotReserved(key)
            inner.remove(codec.storageKey(key))
            changed += key
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }

        override fun commit(): Boolean {
            clearIfRequested()
            return try {
                inner.commit()
            } finally {
                notifyListeners()
            }
        }

        override fun apply() {
            clearIfRequested()
            inner.apply()
            notifyListeners()
        }

        /** Like the legacy editor: clear() drops every user entry but keeps the keysets. */
        private fun clearIfRequested() {
            if (!clearRequested) return
            clearRequested = false
            for (key in getAll().keys) {
                if (key !in changed) inner.remove(codec.storageKey(key))
            }
        }

        private fun notifyListeners() {
            for (listener in listeners) {
                for (key in changed) listener.onSharedPreferenceChanged(this@TinkEncryptedSharedPreferences, key)
            }
            changed.clear()
        }
    }

    private companion object {
        const val TAG = "SecurePrefs"

        fun requireNotReserved(key: String?) {
            if (EncryptedPrefsCodec.isReservedKey(key)) {
                throw SecurityException("$key is a reserved key for the encryption keyset.")
            }
        }

        fun PrefValue.unwrap(): Any =
            when (this) {
                is PrefValue.Str -> value
                is PrefValue.StrSet -> values
                is PrefValue.IntV -> value
                is PrefValue.LongV -> value
                is PrefValue.FloatV -> value
                is PrefValue.Bool -> value
            }
    }
}
