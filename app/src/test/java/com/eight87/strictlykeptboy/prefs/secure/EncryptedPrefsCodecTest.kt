package com.eight87.strictlykeptboy.prefs.secure

import com.eight87.strictlykeptboy.prefs.secure.EncryptedPrefsCodec.DecodeResult
import com.eight87.strictlykeptboy.prefs.secure.EncryptedPrefsCodec.KeyDecodeResult
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.Parameters
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.crypto.tink.daead.PredefinedDeterministicAeadParameters
import java.nio.ByteBuffer
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pure-JVM tests for the legacy-compatible encrypted-prefs wire format. */
class EncryptedPrefsCodecTest {
    private lateinit var daead: DeterministicAead
    private lateinit var aead: Aead
    private lateinit var codec: EncryptedPrefsCodec

    @Before fun setUp() {
        DeterministicAeadConfig.register()
        AeadConfig.register()
        daead = newKeyset(PredefinedDeterministicAeadParameters.AES256_SIV)
            .getPrimitive(RegistryConfiguration.get(), DeterministicAead::class.java)
        aead = newKeyset(PredefinedAeadParameters.AES256_GCM)
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        codec = EncryptedPrefsCodec(daead, aead, "secrets_v1")
    }

    private fun roundTrip(v: PrefValue): DecodeResult {
        val k = codec.storageKey("k")
        return codec.decrypt(k, codec.encrypt(k, v))
    }

    private fun newKeyset(params: Parameters): KeysetHandle =
        KeysetHandle.newBuilder()
            .addEntry(KeysetHandle.generateEntryFromParameters(params).withRandomId().makePrimary())
            .build()

    @Test fun roundTripsEveryType() {
        val values = listOf(
            PrefValue.Str(""),
            PrefValue.Str("ghp_token-123"),
            PrefValue.Str("ünïcødé ✓ 🐈"),
            PrefValue.StrSet(emptySet()),
            PrefValue.StrSet(setOf("a", "ünï", "")),
            PrefValue.IntV(Int.MIN_VALUE),
            PrefValue.LongV(Long.MAX_VALUE),
            PrefValue.FloatV(-1.5f),
            PrefValue.Bool(true),
            PrefValue.Bool(false),
        )
        for (v in values) assertEquals(DecodeResult.Value(v), roundTrip(v))
    }

    @Test fun nullSentinelsDecodeAsNull() {
        val k = codec.storageKey("k")
        assertEquals(DecodeResult.NullValue, codec.decrypt(k, codec.encryptNullString(k)))
        assertEquals(DecodeResult.NullValue, codec.decrypt(k, codec.encryptNullStringSet(k)))
    }

    @Test fun storageKeyIsDeterministicReversibleAndOpaque() {
        assertEquals(codec.storageKey("a"), codec.storageKey("a"))
        assertNotEquals(codec.storageKey("a"), codec.storageKey("b"))
        assertTrue("pat.token" !in codec.storageKey("pat.token"))
        assertEquals(KeyDecodeResult.Key("pat.token"), codec.decodeStorageKey(codec.storageKey("pat.token")))
        assertEquals(KeyDecodeResult.Key(null), codec.decodeStorageKey(codec.storageKey(null)))
        assertTrue(codec.decodeStorageKey("not base64!!") is KeyDecodeResult.Undecryptable)
    }

    @Test fun storageKeyIsBoundToTheFileName() {
        val other = EncryptedPrefsCodec(daead, aead, "repos_v1")
        assertNotEquals(codec.storageKey("k"), other.storageKey("k"))
        assertTrue(other.decodeStorageKey(codec.storageKey("k")) is KeyDecodeResult.Undecryptable)
    }

    /** Pins the legacy layout byte-for-byte: key = b64(SIV(key, file)), value AAD = stored key. */
    @Test fun matchesLegacyLayout() {
        val legacyKey = Base64.getEncoder().encodeToString(
            daead.encryptDeterministically("k".toByteArray(), "secrets_v1".toByteArray()),
        )
        assertEquals(legacyKey, codec.storageKey("k"))
        val bytes = "v1".toByteArray()
        val payload = ByteBuffer.allocate(8 + bytes.size).putInt(0).putInt(bytes.size).put(bytes).array()
        val legacyValue = Base64.getEncoder().encodeToString(aead.encrypt(payload, legacyKey.toByteArray()))
        assertEquals(DecodeResult.Value(PrefValue.Str("v1")), codec.decrypt(legacyKey, legacyValue))
        val boolPayload = ByteBuffer.allocate(5).putInt(5).put(1).array()
        val legacyBool = Base64.getEncoder().encodeToString(aead.encrypt(boolPayload, legacyKey.toByteArray()))
        assertEquals(DecodeResult.Value(PrefValue.Bool(true)), codec.decrypt(legacyKey, legacyBool))
    }

    @Test fun valueIsBoundToItsKey() {
        val a = codec.storageKey("a")
        val stored = codec.encrypt(a, PrefValue.Str("secret"))
        assertTrue(codec.decrypt(codec.storageKey("b"), stored) is DecodeResult.Undecryptable)
        assertTrue(codec.decrypt(a, "not base64!!") is DecodeResult.Undecryptable)
    }

    @Test fun rejectsUnknownTypeAndTruncatedPayloads() {
        val k = codec.storageKey("n")
        fun seal(bytes: ByteArray) = Base64.getEncoder().encodeToString(aead.encrypt(bytes, k.toByteArray()))
        assertEquals(DecodeResult.UnknownType(9), codec.decrypt(k, seal(ByteBuffer.allocate(4).putInt(9).array())))
        assertEquals(DecodeResult.Malformed, codec.decrypt(k, seal(ByteArray(2))))
        assertEquals(
            DecodeResult.Malformed,
            codec.decrypt(k, seal(ByteBuffer.allocate(8).putInt(0).putInt(50).array())),
        )
        assertEquals(DecodeResult.Malformed, codec.decrypt(k, seal(ByteBuffer.allocate(6).putInt(3).array())))
    }
}
