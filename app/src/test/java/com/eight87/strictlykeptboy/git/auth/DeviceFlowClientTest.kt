package com.eight87.strictlykeptboy.git.auth

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase B.5 — Device Flow happy path + error paths. Uses OkHttp's MockWebServer
 * to stand in for the provider's `/device/code` + `/oauth/access_token` endpoints.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceFlowClientTest {

    private lateinit var server: MockWebServer
    private lateinit var config: OAuthProviderConfig
    private lateinit var client: DeviceFlowClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        config = OAuthProviderConfig(
            deviceCodeUrl = server.url("/login/device/code").toString(),
            tokenUrl = server.url("/login/oauth/access_token").toString(),
            clientId = "test-client-id",
            defaultScopes = listOf("repo", "admin:public_key"),
        )
        client = DeviceFlowClient(config, http = OkHttpClient())
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun happyPathPendingThenGranted() = runTest {
        // Device-code request: returns user-code + 1-second poll interval.
        server.enqueue(MockResponse().setBody("""
            {"device_code":"DC-1","user_code":"BATS-1234",
             "verification_uri":"https://example.com/device",
             "expires_in":900,"interval":1}
        """.trimIndent()))
        // First poll: pending.
        server.enqueue(MockResponse().setBody("""{"error":"authorization_pending"}"""))
        // Second poll: granted.
        server.enqueue(MockResponse().setBody("""
            {"access_token":"gho_abc","token_type":"bearer","scope":"repo,admin:public_key"}
        """.trimIndent()))

        val states = client.start().toList()
        advanceUntilIdle()

        assertEquals(DeviceFlowState.RequestingCode, states[0])
        assertTrue("ShowingCode emitted", states[1] is DeviceFlowState.ShowingCode)
        assertEquals("BATS-1234", (states[1] as DeviceFlowState.ShowingCode).userCode)
        assertTrue("Polling emitted", states.any { it is DeviceFlowState.Polling })
        val success = states.last() as DeviceFlowState.Success
        assertEquals("gho_abc", success.token.accessToken)
    }

    @Test fun slowDownIncreasesInterval() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"device_code":"DC","user_code":"X","verification_uri":"https://e.com/d",
             "expires_in":900,"interval":1}
        """.trimIndent()))
        // Server says slow_down then grant.
        server.enqueue(MockResponse().setBody("""{"error":"slow_down"}"""))
        server.enqueue(MockResponse().setBody("""{"access_token":"tok"}"""))

        val states = client.start().toList()
        advanceUntilIdle()

        assertTrue(states.last() is DeviceFlowState.Success)
        // 3 HTTP requests total: device-code + 2 polls.
        assertEquals(3, server.requestCount)
    }

    @Test fun accessDeniedShortCircuits() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"device_code":"DC","user_code":"X","verification_uri":"https://e.com/d",
             "expires_in":900,"interval":1}
        """.trimIndent()))
        server.enqueue(MockResponse().setBody("""
            {"error":"access_denied","error_description":"user said no"}
        """.trimIndent()))

        val states = client.start().toList()
        advanceUntilIdle()

        val failed = states.last() as DeviceFlowState.Failed
        val denied = failed.reason as DeviceFlowError.AccessDenied
        assertEquals("user said no", denied.message)
    }

    @Test fun expiredTokenShortCircuits() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"device_code":"DC","user_code":"X","verification_uri":"https://e.com/d",
             "expires_in":900,"interval":1}
        """.trimIndent()))
        server.enqueue(MockResponse().setBody("""{"error":"expired_token"}"""))

        val states = client.start().toList()
        advanceUntilIdle()

        val failed = states.last() as DeviceFlowState.Failed
        assertTrue(failed.reason is DeviceFlowError.Expired)
    }

    @Test fun deviceCodeNetworkErrorEmitsFailed() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("upstream broke"))

        val states = client.start().toList()
        advanceUntilIdle()

        val failed = states.last() as DeviceFlowState.Failed
        assertTrue(failed.reason is DeviceFlowError.Network)
    }
}
