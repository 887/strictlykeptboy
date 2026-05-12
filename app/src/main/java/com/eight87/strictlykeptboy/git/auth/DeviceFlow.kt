package com.eight87.strictlykeptboy.git.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * RFC 8628 OAuth Device Flow client. Generic over the provider; concrete
 * configs in [GitHubAuth] (Phase B.5) and [ForgejoAuth] (Phase B.6).
 *
 * Usage:
 * ```
 * val flow = DeviceFlowClient(GitHubAuth.config(myClientId)).start()
 * flow.collect { state ->
 *     when (state) {
 *         is DeviceFlowState.ShowingCode -> showQrAndCode(state.userCode, state.verificationUri)
 *         is DeviceFlowState.Success     -> store(repoId, remote, state.token)
 *         is DeviceFlowState.Failed      -> showError(state.reason)
 *         else -> updateProgressUi(state)
 *     }
 * }
 * ```
 *
 * The flow re-emits a `Polling` state after each poll cycle so the UI can
 * animate progress. Server-issued `slow_down` increases the interval by 5s
 * per RFC 8628.
 */
class DeviceFlowClient(
    private val config: OAuthProviderConfig,
    private val http: OkHttpClient = OkHttpClient(),
    private val json: Json = DefaultJson,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun start(scopes: List<String> = config.defaultScopes): Flow<DeviceFlowState> = flow {
        emit(DeviceFlowState.RequestingCode)

        val device = try {
            requestDeviceCode(scopes)
        } catch (t: Throwable) {
            emit(DeviceFlowState.Failed(DeviceFlowError.Network(t)))
            return@flow
        }

        emit(DeviceFlowState.ShowingCode(
            userCode = device.userCode,
            verificationUri = device.verificationUri,
            verificationUriComplete = device.verificationUriComplete,
            expiresInSeconds = device.expiresIn,
        ))

        var interval = device.interval.coerceAtLeast(MIN_POLL_SECONDS)
        val deadline = clock() + device.expiresIn * 1_000L

        while (clock() < deadline) {
            delay(interval * 1_000L)
            emit(DeviceFlowState.Polling(device.userCode, device.verificationUri))

            val outcome = try {
                pollOnce(device.deviceCode)
            } catch (t: Throwable) {
                emit(DeviceFlowState.Failed(DeviceFlowError.Network(t)))
                return@flow
            }

            when (outcome) {
                PollOutcome.Pending -> Unit
                PollOutcome.SlowDown -> interval += 5
                is PollOutcome.Granted -> {
                    emit(DeviceFlowState.Success(outcome.token))
                    return@flow
                }
                PollOutcome.Expired -> {
                    emit(DeviceFlowState.Failed(DeviceFlowError.Expired))
                    return@flow
                }
                is PollOutcome.AccessDenied -> {
                    emit(DeviceFlowState.Failed(DeviceFlowError.AccessDenied(outcome.message)))
                    return@flow
                }
                is PollOutcome.OtherError -> {
                    emit(DeviceFlowState.Failed(DeviceFlowError.Unknown(outcome.error)))
                    return@flow
                }
            }
        }
        emit(DeviceFlowState.Failed(DeviceFlowError.Expired))
    }

    private fun requestDeviceCode(scopes: List<String>): DeviceCodePayload {
        val body = FormBody.Builder()
            .add("client_id", config.clientId)
            .add("scope", scopes.joinToString(" "))
            .build()
        val req = Request.Builder()
            .url(config.deviceCodeUrl)
            .header("Accept", "application/json")
            .post(body)
            .build()
        return http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            require(resp.isSuccessful) { "device-code request failed: ${resp.code} $text" }
            json.decodeFromString(DeviceCodeResponseAdapter, text).toPayload()
        }
    }

    private fun pollOnce(deviceCode: String): PollOutcome {
        val body = FormBody.Builder()
            .add("client_id", config.clientId)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()
        val req = Request.Builder()
            .url(config.tokenUrl)
            .header("Accept", "application/json")
            .post(body)
            .build()
        return http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val payload = json.decodeFromString(TokenResponseAdapter, text)
            when {
                payload.accessToken != null -> PollOutcome.Granted(
                    OAuthToken(
                        accessToken = payload.accessToken,
                        refreshToken = payload.refreshToken,
                        expiryEpochMs = payload.expiresIn?.let { clock() + it * 1_000L },
                    ),
                )
                payload.error == "authorization_pending" -> PollOutcome.Pending
                payload.error == "slow_down" -> PollOutcome.SlowDown
                payload.error == "expired_token" -> PollOutcome.Expired
                payload.error == "access_denied" -> PollOutcome.AccessDenied(payload.errorDescription ?: "denied")
                payload.error != null -> PollOutcome.OtherError(payload.error)
                else -> PollOutcome.OtherError("malformed response: $text")
            }
        }
    }

    private fun DeviceCodeResponse.toPayload() = DeviceCodePayload(
        deviceCode = deviceCode,
        userCode = userCode,
        verificationUri = verificationUri,
        verificationUriComplete = verificationUriComplete,
        expiresIn = expiresIn,
        interval = interval ?: 5,
    )

    private sealed interface PollOutcome {
        object Pending : PollOutcome
        object SlowDown : PollOutcome
        object Expired : PollOutcome
        data class Granted(val token: OAuthToken) : PollOutcome
        data class AccessDenied(val message: String) : PollOutcome
        data class OtherError(val error: String) : PollOutcome
    }

    private data class DeviceCodePayload(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val verificationUriComplete: String?,
        val expiresIn: Int,
        val interval: Int,
    )

    companion object {
        const val MIN_POLL_SECONDS = 1
        internal val DefaultJson = Json { ignoreUnknownKeys = true }
        internal val DeviceCodeResponseAdapter = DeviceCodeResponse.serializer()
        internal val TokenResponseAdapter = TokenResponse.serializer()
    }
}

data class OAuthProviderConfig(
    val deviceCodeUrl: String,
    val tokenUrl: String,
    val clientId: String,
    val defaultScopes: List<String>,
)

sealed interface DeviceFlowState {
    object RequestingCode : DeviceFlowState
    data class ShowingCode(
        val userCode: String,
        val verificationUri: String,
        val verificationUriComplete: String?,
        val expiresInSeconds: Int,
    ) : DeviceFlowState
    data class Polling(val userCode: String, val verificationUri: String) : DeviceFlowState
    data class Success(val token: OAuthToken) : DeviceFlowState
    data class Failed(val reason: DeviceFlowError) : DeviceFlowState
}

sealed interface DeviceFlowError {
    object Expired : DeviceFlowError
    data class Network(val cause: Throwable) : DeviceFlowError
    data class AccessDenied(val message: String) : DeviceFlowError
    data class Unknown(val error: String) : DeviceFlowError
}

@Serializable
internal data class DeviceCodeResponse(
    @kotlinx.serialization.SerialName("device_code") val deviceCode: String,
    @kotlinx.serialization.SerialName("user_code") val userCode: String,
    @kotlinx.serialization.SerialName("verification_uri") val verificationUri: String,
    @kotlinx.serialization.SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @kotlinx.serialization.SerialName("expires_in") val expiresIn: Int,
    @kotlinx.serialization.SerialName("interval") val interval: Int? = null,
)

@Serializable
internal data class TokenResponse(
    @kotlinx.serialization.SerialName("access_token") val accessToken: String? = null,
    @kotlinx.serialization.SerialName("refresh_token") val refreshToken: String? = null,
    @kotlinx.serialization.SerialName("expires_in") val expiresIn: Long? = null,
    @kotlinx.serialization.SerialName("token_type") val tokenType: String? = null,
    @kotlinx.serialization.SerialName("scope") val scope: String? = null,
    @kotlinx.serialization.SerialName("error") val error: String? = null,
    @kotlinx.serialization.SerialName("error_description") val errorDescription: String? = null,
)
