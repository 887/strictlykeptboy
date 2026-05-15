package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.a11y.labelString
import com.eight87.strictlykeptboy.git.AuthMethod
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RemoteBinding
import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.Transport
import com.eight87.strictlykeptboy.git.Uuid7
import com.eight87.strictlykeptboy.git.auth.DeviceFlowState
import com.eight87.strictlykeptboy.git.auth.OAuthToken
import com.eight87.strictlykeptboy.git.auth.PatCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// Test tags
const val TestTagAddRepo = "AddRepo"
const val TestTagAddRepoBranchLocal = "AddRepo-BranchLocal"
const val TestTagAddRepoBranchRemote = "AddRepo-BranchRemote"
const val TestTagAddRepoLocalForm = "AddRepo-LocalForm"
const val TestTagAddRepoRemoteProvider = "AddRepo-RemoteProvider"
const val TestTagAddRepoRemoteAuth = "AddRepo-RemoteAuth"
const val TestTagAddRepoRemoteForm = "AddRepo-RemoteForm"
const val TestTagAddRepoFinish = "AddRepo-Finish"
const val TestTagAddRepoNameField = "AddRepo-NameField"
const val TestTagAddRepoEmailField = "AddRepo-EmailField"
const val TestTagAddRepoDisplayField = "AddRepo-DisplayField"
const val TestTagAddRepoUrlField = "AddRepo-UrlField"
const val TestTagAddRepoPatField = "AddRepo-PatField"
const val TestTagAddRepoUsernameField = "AddRepo-UsernameField"
const val TestTagAddRepoOAuthProgress = "AddRepo-OAuthProgress"
const val TestTagAddRepoDone = "AddRepo-Done"
const val TestTagAddRepoCancel = "AddRepo-Cancel"
const val TestTagAddRepoAddAnother = "AddRepo-AddAnother"
const val TestTagAddRepoAuthOAuth = "AddRepo-AuthOAuth"
const val TestTagAddRepoAuthPat = "AddRepo-AuthPat"
const val TestTagAddRepoAuthSsh = "AddRepo-AuthSsh"

enum class AddRepoProvider { GitHub, Forgejo, GitLab, Other, AlreadyCloned }
enum class AddRepoAuth { OAuthDevice, ManualPat, Ssh }

/**
 * Result emitted when the Add-Repo flow finishes. The caller is responsible
 * for materializing the on-disk repo via `RepoBootstrap` + `GitRepo.init*` +
 * `RepoStore.add` — this navhost only collects the user's choices.
 */
sealed interface AddRepoResult {
    data class LocalOnly(
        val displayName: String,
        val identity: AuthorIdentity,
        val initialCalendarName: String,
    ) : AddRepoResult

    data class Remote(
        val displayName: String,
        val identity: AuthorIdentity,
        val initialCalendarName: String,
        val provider: AddRepoProvider,
        val instanceUrl: String?,
        val repoUrl: String,
        val auth: AddRepoAuth,
        val pat: PatCredential? = null,
        val oauthToken: OAuthToken? = null,
    ) : AddRepoResult
}

/**
 * Convenience: build a [RepoConfig] from a [AddRepoResult.LocalOnly] result.
 */
fun AddRepoResult.LocalOnly.toRepoConfig(rootDir: String): RepoConfig = RepoConfig(
    repoId = Uuid7.generate().toString(),
    displayName = displayName,
    rootDir = rootDir,
    remotes = emptyList(),
    primaryRemote = null,
    authorIdentity = identity,
)

/**
 * Convenience: build a [RepoConfig] from a [AddRepoResult.Remote] result.
 */
fun AddRepoResult.Remote.toRepoConfig(rootDir: String): RepoConfig {
    val transport = when (auth) {
        AddRepoAuth.Ssh -> Transport.Ssh
        AddRepoAuth.OAuthDevice -> Transport.HttpsOAuth
        AddRepoAuth.ManualPat -> Transport.HttpsPat
    }
    val method = when (auth) {
        AddRepoAuth.Ssh -> AuthMethod.Ssh
        AddRepoAuth.OAuthDevice -> when (provider) {
            AddRepoProvider.Forgejo -> AuthMethod.OAuthForgejo
            else -> AuthMethod.OAuthGitHub
        }
        AddRepoAuth.ManualPat -> AuthMethod.ManualPat
    }
    val binding = RemoteBinding(
        name = RemoteName.ORIGIN,
        url = repoUrl,
        transport = transport,
        authMethod = method,
    )
    return RepoConfig(
        repoId = Uuid7.generate().toString(),
        displayName = displayName,
        rootDir = rootDir,
        remotes = listOf(binding),
        primaryRemote = binding.name,
        authorIdentity = identity,
    )
}

internal enum class Step { Branch, Local, RemoteProvider, RemoteAuth, RemoteForm, Done }

/**
 * Phase I.2 — multi-screen Add-Repo flow. Implemented as a single
 * stateful Compose host that swaps the visible step via [Step]. Avoids a
 * navigation library dependency for v1.
 *
 * @param deviceFlowFactory hook for tests / OAuth screen — receives the
 *   provider and returns a [DeviceFlowState] flow. Pass `null` if the flow
 *   isn't being exercised (caller is on PAT path).
 */
@Composable
fun AddRepoNavHost(
    onCancel: () -> Unit,
    onFinish: (AddRepoResult) -> Unit,
    modifier: Modifier = Modifier,
    deviceFlowFactory: ((AddRepoProvider, String?) -> Flow<DeviceFlowState>)? = null,
    /**
     * Round 2.17 Phase E.1 — when non-null and reporting `NeedsPicking`,
     * an inline storage step renders instead of the form. Once the gate
     * flips to `Confirmed` (via the host firing the SAF parent picker
     * or the inline "Keep inside the app" button), the form appears.
     */
    storageGate: com.eight87.strictlykeptboy.prefs.ParentLocationGate.State? = null,
    onPickExternalStorage: () -> Unit = {},
    onPickInternalStorage: () -> Unit = {},
) {
    // Phase E.1 — pre-add gate. Short-circuit the form when no parent
    // is confirmed yet; the inline step matches the wizard's storage
    // step copy so the user sees the same prompt either way.
    if (storageGate is com.eight87.strictlykeptboy.prefs.ParentLocationGate.State.NeedsPicking) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .testTag(TestTagAddRepo)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.add_repo_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            com.eight87.strictlykeptboy.ui.wizard.StorageStep(
                onPickExternal = onPickExternalStorage,
                onPickInternal = onPickInternalStorage,
            )
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag(TestTagAddRepoCancel),
            ) { Text(stringResource(R.string.dialog_cancel)) }
        }
        return
    }

    var step by remember { mutableStateOf(Step.Branch) }
    var displayName by remember { mutableStateOf("") }
    var identityName by remember { mutableStateOf("") }
    var identityEmail by remember { mutableStateOf("") }
    var calendarName by remember { mutableStateOf("personal") }

    // Remote-path state
    var provider by remember { mutableStateOf(AddRepoProvider.GitHub) }
    var instanceUrl by remember { mutableStateOf("") }
    var repoUrl by remember { mutableStateOf("") }
    var auth by remember { mutableStateOf(AddRepoAuth.OAuthDevice) }
    var patToken by remember { mutableStateOf("") }
    var patUsername by remember { mutableStateOf("") }
    var oauthToken by remember { mutableStateOf<OAuthToken?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTagAddRepo)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.add_repo_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        when (step) {
            Step.Branch -> BranchStep(
                onLocal = { step = Step.Local },
                onRemote = { step = Step.RemoteProvider },
            )
            Step.Local -> LocalStep(
                displayName = displayName, onDisplayName = { displayName = it },
                identityName = identityName, onIdentityName = { identityName = it },
                identityEmail = identityEmail, onIdentityEmail = { identityEmail = it },
                calendarName = calendarName, onCalendarName = { calendarName = it },
                onBack = { step = Step.Branch },
                onFinish = {
                    onFinish(
                        AddRepoResult.LocalOnly(
                            displayName = displayName.ifBlank { "local-only" },
                            identity = AuthorIdentity(
                                identityName.ifBlank { "me" },
                                identityEmail.ifBlank { "me@example.com" },
                            ),
                            initialCalendarName = calendarName.ifBlank { "personal" },
                        ),
                    )
                    step = Step.Done
                },
            )
            Step.RemoteProvider -> RemoteProviderStep(
                provider = provider, onProvider = { provider = it },
                instanceUrl = instanceUrl, onInstanceUrl = { instanceUrl = it },
                onBack = { step = Step.Branch },
                onNext = { step = Step.RemoteAuth },
            )
            Step.RemoteAuth -> RemoteAuthStep(
                provider = provider,
                instanceUrl = instanceUrl.takeIf { it.isNotBlank() },
                auth = auth, onAuth = { auth = it },
                deviceFlowFactory = deviceFlowFactory,
                onTokenObtained = { oauthToken = it },
                onBack = { step = Step.RemoteProvider },
                onNext = { step = Step.RemoteForm },
            )
            Step.RemoteForm -> RemoteFormStep(
                displayName = displayName, onDisplayName = { displayName = it },
                identityName = identityName, onIdentityName = { identityName = it },
                identityEmail = identityEmail, onIdentityEmail = { identityEmail = it },
                calendarName = calendarName, onCalendarName = { calendarName = it },
                repoUrl = repoUrl, onRepoUrl = { repoUrl = it },
                auth = auth,
                patUsername = patUsername, onPatUsername = { patUsername = it },
                patToken = patToken, onPatToken = { patToken = it },
                onBack = { step = Step.RemoteAuth },
                onFinish = {
                    onFinish(
                        AddRepoResult.Remote(
                            displayName = displayName.ifBlank { "remote-repo" },
                            identity = AuthorIdentity(
                                identityName.ifBlank { "me" },
                                identityEmail.ifBlank { "me@example.com" },
                            ),
                            initialCalendarName = calendarName.ifBlank { "personal" },
                            provider = provider,
                            instanceUrl = instanceUrl.takeIf { it.isNotBlank() },
                            repoUrl = repoUrl,
                            auth = auth,
                            pat = if (auth == AddRepoAuth.ManualPat) {
                                PatCredential(
                                    username = patUsername.ifBlank { "git" },
                                    token = patToken,
                                )
                            } else null,
                            oauthToken = oauthToken,
                        ),
                    )
                    step = Step.Done
                },
            )
            Step.Done -> DoneStep(
                onAddAnother = {
                    // Reset transient remote-state to add another remote.
                    repoUrl = ""
                    patToken = ""
                    patUsername = ""
                    oauthToken = null
                    step = Step.RemoteProvider
                },
                onClose = onCancel,
            )
        }

        Spacer(Modifier.height(8.dp))
        if (step != Step.Done) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag(TestTagAddRepoCancel),
            ) { Text(stringResource(R.string.dialog_cancel)) }
        }
    }
}

@Composable
private fun BranchStep(onLocal: () -> Unit, onRemote: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.add_repo_branch_prompt), style = MaterialTheme.typography.titleMedium)
        Card(
            onClick = onLocal,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagAddRepoBranchLocal),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.add_repo_local_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.add_repo_local_blurb),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Card(
            onClick = onRemote,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagAddRepoBranchRemote),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.add_repo_remote_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.add_repo_remote_blurb),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun LocalStep(
    displayName: String, onDisplayName: (String) -> Unit,
    identityName: String, onIdentityName: (String) -> Unit,
    identityEmail: String, onIdentityEmail: (String) -> Unit,
    calendarName: String, onCalendarName: (String) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier.testTag(TestTagAddRepoLocalForm),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.add_repo_local_form_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = displayName, onValueChange = onDisplayName,
            label = { Text(stringResource(R.string.add_repo_field_display_name)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoDisplayField),
        )
        OutlinedTextField(
            value = identityName, onValueChange = onIdentityName,
            label = { Text(stringResource(R.string.add_repo_field_your_name)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoNameField),
        )
        OutlinedTextField(
            value = identityEmail, onValueChange = onIdentityEmail,
            label = { Text(stringResource(R.string.add_repo_field_your_email)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoEmailField),
        )
        OutlinedTextField(
            value = calendarName, onValueChange = onCalendarName,
            label = { Text(stringResource(R.string.add_repo_field_initial_calendar)) },
            modifier = Modifier.fillMaxWidth(),
        )
        StepButtons(onBack = onBack, onNext = onFinish, nextLabel = stringResource(R.string.add_repo_step_finish))
    }
}

@Composable
private fun RemoteProviderStep(
    provider: AddRepoProvider, onProvider: (AddRepoProvider) -> Unit,
    instanceUrl: String, onInstanceUrl: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier.testTag(TestTagAddRepoRemoteProvider),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.add_repo_pick_provider), style = MaterialTheme.typography.titleMedium)
        AddRepoProvider.entries.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = provider == p, onClick = { onProvider(p) })
                Text(p.labelString())
            }
        }
        if (provider == AddRepoProvider.Forgejo || provider == AddRepoProvider.GitLab) {
            OutlinedTextField(
                value = instanceUrl, onValueChange = onInstanceUrl,
                label = { Text(stringResource(R.string.add_repo_instance_url)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        StepButtons(onBack = onBack, onNext = onNext, nextLabel = stringResource(R.string.add_repo_step_next))
    }
}

// Phase U.4 / F11: provider labels resolved via ui/a11y/EnumLabels.kt
// (`AddRepoProvider.labelString()`). The private extension previously here
// is removed — call sites use `p.labelString()` directly.

@Composable
private fun RemoteAuthStep(
    provider: AddRepoProvider,
    instanceUrl: String?,
    auth: AddRepoAuth, onAuth: (AddRepoAuth) -> Unit,
    deviceFlowFactory: ((AddRepoProvider, String?) -> Flow<DeviceFlowState>)?,
    onTokenObtained: (OAuthToken) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier.testTag(TestTagAddRepoRemoteAuth),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.add_repo_auth_prompt), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = auth == AddRepoAuth.OAuthDevice,
                onClick = { onAuth(AddRepoAuth.OAuthDevice) },
                modifier = Modifier.testTag(TestTagAddRepoAuthOAuth),
            )
            Text(stringResource(R.string.add_repo_auth_oauth))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = auth == AddRepoAuth.ManualPat,
                onClick = { onAuth(AddRepoAuth.ManualPat) },
                modifier = Modifier.testTag(TestTagAddRepoAuthPat),
            )
            Text(stringResource(R.string.add_repo_auth_pat))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = auth == AddRepoAuth.Ssh,
                onClick = { onAuth(AddRepoAuth.Ssh) },
                modifier = Modifier.testTag(TestTagAddRepoAuthSsh),
            )
            Text(stringResource(R.string.add_repo_auth_ssh))
        }

        if (auth == AddRepoAuth.OAuthDevice) {
            OAuthDeviceFlowProgress(
                factory = deviceFlowFactory,
                provider = provider,
                instanceUrl = instanceUrl,
                onTokenObtained = onTokenObtained,
            )
        }
        StepButtons(onBack = onBack, onNext = onNext, nextLabel = stringResource(R.string.add_repo_step_next))
    }
}

@Composable
private fun OAuthDeviceFlowProgress(
    factory: ((AddRepoProvider, String?) -> Flow<DeviceFlowState>)?,
    provider: AddRepoProvider,
    instanceUrl: String?,
    onTokenObtained: (OAuthToken) -> Unit,
) {
    val flow = remember(factory, provider, instanceUrl) {
        factory?.invoke(provider, instanceUrl) ?: flowOf()
    }
    var state by remember { mutableStateOf<DeviceFlowState?>(null) }
    LaunchedEffect(flow) {
        flow.collect { s ->
            state = s
            if (s is DeviceFlowState.Success) onTokenObtained(s.token)
        }
    }
    Column(
        modifier = Modifier.testTag(TestTagAddRepoOAuthProgress),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (val s = state) {
            null -> Text(
                if (factory == null) stringResource(R.string.add_repo_oauth_next_hint)
                else stringResource(R.string.add_repo_oauth_starting),
                style = MaterialTheme.typography.bodySmall,
            )
            DeviceFlowState.RequestingCode -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.height(16.dp).padding(end = 4.dp))
                Text(stringResource(R.string.add_repo_oauth_requesting_code))
            }
            is DeviceFlowState.ShowingCode -> {
                Text(stringResource(R.string.add_repo_oauth_open_url, s.verificationUri), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.add_repo_oauth_enter_code, s.userCode), style = MaterialTheme.typography.titleMedium)
            }
            is DeviceFlowState.Polling -> Text(stringResource(R.string.add_repo_oauth_waiting))
            is DeviceFlowState.Success -> Text(stringResource(R.string.add_repo_oauth_signed_in))
            is DeviceFlowState.Failed -> Text(
                stringResource(R.string.add_repo_oauth_failed, s.reason::class.simpleName ?: "Unknown"),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RemoteFormStep(
    displayName: String, onDisplayName: (String) -> Unit,
    identityName: String, onIdentityName: (String) -> Unit,
    identityEmail: String, onIdentityEmail: (String) -> Unit,
    calendarName: String, onCalendarName: (String) -> Unit,
    repoUrl: String, onRepoUrl: (String) -> Unit,
    auth: AddRepoAuth,
    patUsername: String, onPatUsername: (String) -> Unit,
    patToken: String, onPatToken: (String) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier.testTag(TestTagAddRepoRemoteForm),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.add_repo_form_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = repoUrl, onValueChange = onRepoUrl,
            label = { Text(stringResource(R.string.add_repo_field_repo_url)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoUrlField),
        )
        OutlinedTextField(
            value = displayName, onValueChange = onDisplayName,
            label = { Text(stringResource(R.string.add_repo_field_display_name)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoDisplayField),
        )
        OutlinedTextField(
            value = identityName, onValueChange = onIdentityName,
            label = { Text(stringResource(R.string.add_repo_field_your_name)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoNameField),
        )
        OutlinedTextField(
            value = identityEmail, onValueChange = onIdentityEmail,
            label = { Text(stringResource(R.string.add_repo_field_your_email)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoEmailField),
        )
        OutlinedTextField(
            value = calendarName, onValueChange = onCalendarName,
            label = { Text(stringResource(R.string.add_repo_field_initial_calendar)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (auth == AddRepoAuth.ManualPat) {
            OutlinedTextField(
                value = patUsername, onValueChange = onPatUsername,
                label = { Text(stringResource(R.string.add_repo_pat_username)) },
                modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoUsernameField),
            )
            OutlinedTextField(
                value = patToken, onValueChange = onPatToken,
                label = { Text(stringResource(R.string.add_repo_pat_token)) },
                modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoPatField),
            )
        }
        StepButtons(onBack = onBack, onNext = onFinish, nextLabel = stringResource(R.string.add_repo_step_finish))
    }
}

@Composable
private fun DoneStep(onAddAnother: () -> Unit, onClose: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag(TestTagAddRepoDone),
    ) {
        Text(
            stringResource(R.string.add_repo_done_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag(TestTagAddRepoFinish),
        )
        TextButton(
            onClick = onAddAnother,
            modifier = Modifier.testTag(TestTagAddRepoAddAnother),
        ) { Text(stringResource(R.string.add_repo_add_another)) }
        Button(onClick = onClose) { Text(stringResource(R.string.add_repo_done_close)) }
    }
}

const val TestTagAddRepoNext = "AddRepo-Next"
const val TestTagAddRepoBack = "AddRepo-Back"

@Composable
private fun StepButtons(onBack: () -> Unit, onNext: () -> Unit, nextLabel: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.testTag(TestTagAddRepoBack),
        ) { Text(stringResource(R.string.add_repo_step_back)) }
        Button(
            onClick = onNext,
            modifier = Modifier.testTag(TestTagAddRepoNext),
        ) { Text(nextLabel) }
    }
}
