package com.eight87.strictlykeptboy.caldav.auth

import com.eight87.strictlykeptboy.git.auth.OAuthProviderConfig

/**
 * Phase Y.3 — OAuth Device Flow configurations for the CalDAV providers
 * that support it. Re-uses the existing [com.eight87.strictlykeptboy.git.auth.DeviceFlowClient]
 * machinery (Phase SE-D) so we don't duplicate the RFC 8628 state
 * machine.
 *
 * Apple iCloud has no CalDAV OAuth surface — falls back to app-specific
 * passwords (see `CalDavCredential.AppPassword`).
 */
object CalDavOAuthConfigs {

    /**
     * Google Calendar CalDAV access. Endpoint:
     * `apidata.googleusercontent.com/caldav/v2/<email>/events`.
     * Uses the standard Google Device Flow OAuth scopes.
     */
    fun google(clientId: String): OAuthProviderConfig = OAuthProviderConfig(
        deviceCodeUrl = "https://oauth2.googleapis.com/device/code",
        tokenUrl = "https://oauth2.googleapis.com/token",
        clientId = clientId,
        defaultScopes = listOf("https://www.googleapis.com/auth/calendar"),
    )

    /**
     * Microsoft 365 (Outlook / Exchange). Note: many Office 365 tenants
     * disable basic auth completely and require OAuth 2.0 via the
     * `common` v2.0 endpoint. The CalDAV endpoint is
     * `outlook.office.com/<tenant>/...` and the access token must carry
     * the `Calendars.ReadWrite` scope.
     */
    fun microsoft(clientId: String): OAuthProviderConfig = OAuthProviderConfig(
        deviceCodeUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/devicecode",
        tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
        clientId = clientId,
        defaultScopes = listOf("Calendars.ReadWrite", "offline_access"),
    )
}
