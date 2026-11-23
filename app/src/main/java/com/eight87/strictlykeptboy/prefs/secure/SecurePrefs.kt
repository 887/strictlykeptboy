package com.eight87.strictlykeptboy.prefs.secure

import android.content.Context
import android.content.SharedPreferences
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Opens an encrypted [SharedPreferences] file: Tink AES256-SIV for keys, AES256-GCM for
 * values, both keysets wrapped by an Android Keystore master key.
 *
 * Replaces androidx.security's deprecated `EncryptedSharedPreferences.create(..)` +
 * `MasterKey` while reusing its file name, keyset preference names and master-key alias, so
 * every file written by earlier builds opens unchanged (and the reverse). If the master key
 * does not exist yet, AndroidKeysetManager generates it under the same alias.
 */
object SecurePrefs {
    /** androidx.security's `MasterKey.DEFAULT_MASTER_KEY_ALIAS`, as a Tink keystore URI. */
    private const val MASTER_KEY_URI = "android-keystore://_androidx_security_master_key_"

    fun open(context: Context, fileName: String): SharedPreferences {
        DeterministicAeadConfig.register()
        AeadConfig.register()
        val app = context.applicationContext
        val keyCipher = keyset(app, fileName, EncryptedPrefsCodec.KEY_KEYSET_PREF, "AES256_SIV")
            .getPrimitive(RegistryConfiguration.get(), DeterministicAead::class.java)
        val valueCipher = keyset(app, fileName, EncryptedPrefsCodec.VALUE_KEYSET_PREF, "AES256_GCM")
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        return TinkEncryptedSharedPreferences(
            backing = app.getSharedPreferences(fileName, Context.MODE_PRIVATE),
            codec = EncryptedPrefsCodec(keyCipher, valueCipher, fileName),
        )
    }

    private fun keyset(context: Context, fileName: String, prefName: String, template: String): KeysetHandle =
        AndroidKeysetManager.Builder()
            .withKeyTemplate(KeyTemplates.get(template))
            .withSharedPref(context, prefName, fileName)
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
}
