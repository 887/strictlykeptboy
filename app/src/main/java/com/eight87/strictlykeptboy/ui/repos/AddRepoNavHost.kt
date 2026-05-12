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
import androidx.compose.ui.unit.dp
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
) {
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
            text = "Add repo",
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
            ) { Text("Cancel") }
        }
    }
}

@Composable
private fun BranchStep(onLocal: () -> Unit, onRemote: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How would you like to start?", style = MaterialTheme.typography.titleMedium)
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
                Text("Create local-only", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Phone-only repo. No sync, no remote — recommended for first-time users.",
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
                Text("Connect to a remote", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Clone or push to GitHub, Forgejo, GitLab, or another git host.",
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
        Text("Local-only repo", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = displayName, onValueChange = onDisplayName,
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoDisplayField),
        )
        OutlinedTextField(
            value = identityName, onValueChange = onIdentityName,
            label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoNameField),
        )
        OutlinedTextField(
            value = identityEmail, onValueChange = onIdentityEmail,
            label = { Text("Your email") },
            modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoEmailField),
        )
        OutlinedTextField(
            value = calendarName, onValueChange = onCalendarName,
            label = { Text("Initial calendar name") },
            modifier = Modifier.fillMaxWidth(),
        )
        StepButtons(onBack = onBack, onNext = onFinish, nextLabel = "Finish")
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
        Text("Pick your provider", style = MaterialTheme.typography.titleMedium)
        AddRepoProvider.entries.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = provider == p, onClick = { onProvider(p) })
                Text(p.label())
            }
        }
        if (provider == AddRepoProvider.Forgejo || provider == AddRepoProvider.GitLab) {
            OutlinedTextField(
                value = instanceUrl, onValueChange = onInstanceUrl,
                label = { Text("Instance URL") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        StepButtons(onBack = onBack, onNext = onNext, nextLabel = "Next")
    }
}

private fun AddRepoProvider.label(): String = when (this) {
    AddRepoProvider.GitHub -> "GitHub"
    AddRepoProvider.Forgejo -> "Forgejo / Gitea"
    AddRepoProvider.GitLab -> "GitLab"
    AddRepoProvider.Other -> "Other (generic git URL)"
    AddRepoProvider.AlreadyCloned -> "Already cloned locally"
}

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
        Text("How should we authenticate?", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = auth == AddRepoAuth.OAuthDevice,
                onClick = { onAuth(AddRepoAuth.OAuthDevice) },
                modifier = Modifier.testTag(TestTagAddRepoAuthOAuth),
            )
            Text("Sign in with browser (OAuth Device Flow)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = auth == AddRepoAuth.ManualPat,
                onClick = { onAuth(AddRepoAuth.ManualPat) },
                modifier = Modifier.testTag(TestTagAddRepoAuthPat),
            )
            Text("Use a Personal Access Token (PAT)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = auth == AddRepoAuth.Ssh,
                onClick = { onAuth(AddRepoAuth.Ssh) },
                modifier = Modifier.testTag(TestTagAddRepoAuthSsh),
            )
            Text("SSH key (deferred)")
        }

        if (auth == AddRepoAuth.OAuthDevice) {
            OAuthDeviceFlowProgress(
                factory = deviceFlowFactory,
                provider = provider,
                instanceUrl = instanceUrl,
                onTokenObtained = onTokenObtained,
            )
        }
        StepButtons(onBack = onBack, onNext = onNext, nextLabel = "Next")
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
                if (factory == null) "OAuth flow will run when you tap Next."
                else "Starting…",
                style = MaterialTheme.typography.bodySmall,
            )
            DeviceFlowState.RequestingCode -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.height(16.dp).padding(end = 4.dp))
                Text("Requesting code…")
            }
            is DeviceFlowState.ShowingCode -> {
                Text("Open ${s.verificationUri}", style = MaterialTheme.typography.bodyMedium)
                Text("Enter code: ${s.userCode}", style = MaterialTheme.typography.titleMedium)
            }
            is DeviceFlowState.Polling -> Text("Waiting for browser approval…")
            is DeviceFlowState.Success -> Text("Signed in.")
            is DeviceFlowState.Failed -> Text(
                "OAuth failed: ${s.reason::class.simpleName}",
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
        Text("Repo + identity", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = repoUrl, onValueChange = onRepoUrl,
            label = { Text("Repo URL") },
            modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoUrlField),
        )
        OutlinedTextField(
            value = displayName, onValueChange = onDisplayName,
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoDisplayField),
        )
        OutlinedTextField(
            value = identityName, onValueChange = onIdentityName,
            label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoNameField),
        )
        OutlinedTextField(
            value = identityEmail, onValueChange = onIdentityEmail,
            label = { Text("Your email") },
            modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoEmailField),
        )
        OutlinedTextField(
            value = calendarName, onValueChange = onCalendarName,
            label = { Text("Initial calendar name") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (auth == AddRepoAuth.ManualPat) {
            OutlinedTextField(
                value = patUsername, onValueChange = onPatUsername,
                label = { Text("PAT username") },
                modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoUsernameField),
            )
            OutlinedTextField(
                value = patToken, onValueChange = onPatToken,
                label = { Text("PAT token") },
                modifier = Modifier.fillMaxWidth().testTag(TestTagAddRepoPatField),
            )
        }
        StepButtons(onBack = onBack, onNext = onFinish, nextLabel = "Finish")
    }
}

@Composable
private fun DoneStep(onAddAnother: () -> Unit, onClose: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag(TestTagAddRepoDone),
    ) {
        Text(
            "Repo added.",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag(TestTagAddRepoFinish),
        )
        TextButton(
            onClick = onAddAnother,
            modifier = Modifier.testTag(TestTagAddRepoAddAnother),
        ) { Text("+ Add another remote") }
        Button(onClick = onClose) { Text("Done") }
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
        ) { Text("Back") }
        Button(
            onClick = onNext,
            modifier = Modifier.testTag(TestTagAddRepoNext),
        ) { Text(nextLabel) }
    }
}
