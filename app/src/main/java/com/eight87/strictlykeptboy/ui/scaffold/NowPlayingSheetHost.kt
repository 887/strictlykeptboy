package com.eight87.strictlykeptboy.ui.scaffold

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.NowNextResolver
import com.eight87.strictlykeptboy.resolver.NowNextSnapshot
import com.eight87.strictlykeptboy.task.StubTaskPlaybackSource
import com.eight87.strictlykeptboy.task.TaskNowPlayingState
import com.eight87.strictlykeptboy.task.TaskQueueCommands
import com.eight87.strictlykeptboy.task.TaskTransportCommands
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow
import com.eight87.strictlykeptboy.ui.playing.ExpandedNowPlayingTaskBody
import com.eight87.strictlykeptboy.ui.playing.MiniPlayer
import com.eight87.strictlykeptboy.ui.playing.NowPlayingScreen
import com.eight87.strictlykeptboy.ui.tasks.LocalAnimatingDoneIds
import com.eight87.strictlykeptboy.ui.tasks.LocalAuthorDisplayNames
import com.eight87.strictlykeptboy.ui.tasks.LocalPraiseTerm
import com.eight87.strictlykeptboy.ui.tasks.LocalHonorific
import com.eight87.strictlykeptboy.ui.tasks.LocalNeutralMode
import com.eight87.strictlykeptboy.ui.tasks.TaskQuickAddRequest
import com.eight87.strictlykeptboy.ui.tasks.TasksViewState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Round 2.21 SOLID split — extracted from `SkbAppShell.kt`. This file
 * owns the bottom-anchored Now-Playing sheet host: MiniPlayer at peek,
 * NowPlayingScreen at fully-expanded, nested-scroll bridge, flick-
 * commit, task detail / quick-add overlays. Single reason to change:
 * sheet drag/animation behaviour.
 *
 * Round 2.16.A — verbatim port of tonearmboy `TonearmboyApp.kt` lines
 * 140-471 (the sheet-host block). Wraps a [content] layer (the app's
 * existing chrome) with a bottom-anchored sheet that hosts the
 * [MiniPlayer] at peek and [NowPlayingScreen] at fully-expanded.
 *
 * Matches tonearmboy verbatim:
 *  - peek = 118 dp
 *  - flick threshold = 0.05f (5% of sheet travel)
 *  - staggered crossfade: mini visible 0..0.5, full visible 0.5..1
 *  - nested-scroll connection drains queue overscroll → sheet progress
 *  - drag-start progress captured for direction-based flick commit
 *
 * Phase A reads from [StubTaskPlaybackSource]; Phase B replaces with
 * the real projector.
 */
@Composable
internal fun NowPlayingSheetHost(
    source: Any = StubTaskPlaybackSource,
    tasksState: TasksViewState = remember { TasksViewState() },
    onWriteTask: (TaskQuickAddRequest) -> Unit = {},
    onStartTask: ((String) -> Unit)? = null,
    /**
     * Round 2.25 Phase B — optional Now/Next snapshot stream. When
     * non-null, MiniPlayer renders the right-column `Next: …` block
     * and overrides the empty-state title with `Now: …` (D-2.25.c).
     */
    nowNextFlow: StateFlow<NowNextSnapshot>? = null,
    content: @Composable () -> Unit,
) {
    // Round 2.16.B — the source is one object satisfying the three
    // facets the ported composables consume. Phase A used the singleton
    // [StubTaskPlaybackSource]; Phase B injects the real
    // `TaskTransportAdapter` from AppGraph (or anything else
    // satisfying the union of the three interfaces).
    val now = source as TaskNowPlayingState
    val transport = source as TaskTransportCommands
    val queue = source as TaskQueueCommands
    val playbackState by now.state.collectAsState()

    // Round 2.25 Phase B — Now/Next snapshot + 60s local relative-time
    // ticker (D-2.25.f). The `tickNow` state is only used to recompute
    // the formatted relative copy; the snapshot itself comes from the
    // flow and isn't re-queried on tick.
    val nowNextSnap = nowNextFlow?.collectAsState()?.value
    var tickNow by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(nowNextFlow) {
        if (nowNextFlow == null) return@LaunchedEffect
        while (true) {
            tickNow = Instant.now()
            kotlinx.coroutines.delay(60_000L)
        }
    }
    val nextRelative = nowNextSnap?.next?.let {
        NowNextResolver.formatRelative(Duration.between(tickNow, it.start.toInstant()))
    }
    // Round 2.16.D — task detail / quick-add overlays migrated here
    // from TasksPane so they layer above the sheet per tonearmboy's
    // overlay convention.
    var openTask by remember {
        mutableStateOf<com.eight87.strictlykeptboy.ui.tasks.TaskItem?>(null)
    }
    // D-41 fix — track a short cooldown after the detail sheet is dismissed
    // so that the touch-up event from the dismiss swipe gesture does NOT
    // propagate to the parent sheet's draggable and dismiss it too.
    // The cooldown matches the typical ModalBottomSheet settle animation
    // duration (~300ms). During this window, downward drag on the parent
    // sheet is suppressed.
    var detailJustDismissed by remember { mutableStateOf(false) }
    var quickAddOpen by remember { mutableStateOf(false) }
    val tasksUi by tasksState.state.collectAsState()

    // D-28 — undo snackbar infrastructure. A channel carries task IDs
    // from the toggleDone path (via onShowUndoSnackbar) into this
    // LaunchedEffect which shows the snackbar and handles the result.
    val snackbarHostState = remember { SnackbarHostState() }
    val undoChannel = remember { Channel<String>(Channel.CONFLATED) }
    val markedDoneLabel = stringResource(R.string.task_marked_done_snackbar)
    val markedDoneIdentityLabel = stringResource(R.string.task_marked_done_snackbar_identity, tasksUi.praiseTerm.ifEmpty { "good boy" })
    LaunchedEffect(tasksState) {
        tasksState.onShowUndoSnackbar = { taskId -> undoChannel.trySend(taskId) }
    }
    LaunchedEffect(undoChannel) {
        for (taskId in undoChannel) {
            // D-44 — the mark-done snackbar is a CONFIRMATION toast only, no Undo
            // action. The earlier Undo button was a false affordance: undoDone()
            // could not reliably restore a completion through the read-only-demo /
            // Room / in-memory / override tangle (verified broken on-device + by two
            // QA walks). The "Mark done?" confirm dialog is the real safety gate
            // (and its copy already says completion can't be un-finished from
            // history); to reverse a completion the user un-checks it in the Done
            // view. Short duration is fine for a passive confirmation.
            // D-33 — use identity-driven copy when praiseTerm is set and neutral off.
            val label = if (!tasksUi.neutralMode && tasksUi.praiseTerm.isNotEmpty()) {
                markedDoneIdentityLabel
            } else {
                markedDoneLabel
            }
            snackbarHostState.showSnackbar(
                message = label,
                duration = SnackbarDuration.Short,
            )
        }
    }

    val sheetProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val nowPlayingListState = rememberLazyListState()
    // D-42 — track whether a swipe gesture is currently in progress so
    // the FAB onClick is ignored during swipe-release events.
    var swipeInProgress by remember { mutableStateOf(false) }

    val openNowPlayingSheet: () -> Unit = remember {
        { coroutineScope.launch { sheetProgress.animateTo(1f) }; Unit }
    }
    val closeSheet: () -> Unit = remember {
        { coroutineScope.launch { sheetProgress.animateTo(0f) }; Unit }
    }

    // Round 2.16 post-DONE — peek is always visible on Schedule. When no
    // task is active, the MiniPlayer renders an empty-state row (checklist
    // icon + "No active task / Tap to pick one") that opens the sheet on
    // tap. This obsoletes the D.7 stacked Tasks FAB.
    val showMiniPlayer = true

    BackHandler(enabled = sheetProgress.value > 0f) {
        closeSheet()
    }

    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val screenHeightPx = with(density) { screenHeightDp.toPx() }.coerceAtLeast(1f)
    // D-27 — provide animatingDoneIds down the tree so TaskTodayView can apply
    // the completion beat fade without ExpandedNowPlayingTaskBody needing changes.
    // D-43 fix — provide authorDisplayNames so TaskRow's AuthorBubble resolves
    // author UUIDs to display names ("BK" for "Boy Keeper") without needing an
    // explicit parameter threaded through ExpandedNowPlayingTaskBody.
    CompositionLocalProvider(
        LocalAnimatingDoneIds provides tasksUi.animatingDoneIds,
        LocalAuthorDisplayNames provides tasksUi.authorDisplayNames,
        // D-33 — surface identity.toml praise term + honorific + neutral mode
        // into the task UI copy without threading through every intermediate
        // composable. Empty string = fall back to hardcoded defaults in leaves.
        LocalPraiseTerm provides tasksUi.praiseTerm,
        LocalHonorific provides tasksUi.honorific,
        LocalNeutralMode provides tasksUi.neutralMode,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        val peekDp = 118.dp
        val peekPx = with(density) { peekDp.toPx() }
        val effectivePeekPx = if (showMiniPlayer) peekPx else 0f

        val progress = sheetProgress.value
        val miniAlpha = (1f - kotlin.math.min(progress * 2f, 1f)).coerceIn(0f, 1f)
        val nowPlayingAlpha = (kotlin.math.max(progress - 0.5f, 0f) * 2f).coerceIn(0f, 1f)

        val dragStartProgress = remember { mutableStateOf<Float?>(null) }
        val onSheetDragDelta: (Float) -> Unit = { delta ->
            coroutineScope.launch {
                // D-41 re-fix — when a task detail sheet is open OR was just
                // dismissed, block downward drag on the parent sheet. The
                // `detailJustDismissed` cooldown covers the case where the
                // modal sheet dismiss gesture fires a touch-up that reaches
                // the parent's draggable after `openTask` is already null.
                if ((openTask != null || detailJustDismissed) && delta > 0f) return@launch
                // D-25 re-fix — only allow downward drag (delta > 0 = user
                // dragging down = progress decreases) when the list IS at the
                // very top. If the list has scrolled even one pixel, a downward
                // drag is a list-scroll gesture, not a sheet-dismiss intent.
                // The drag-handle component passes its own drags through this
                // same callback and the handle is NOT within the LazyColumn, so
                // handle drags are unaffected (the handle Row sits above the
                // LazyColumn in the Column layout).
                val listAtTop = nowPlayingListState.firstVisibleItemIndex == 0 &&
                    nowPlayingListState.firstVisibleItemScrollOffset == 0
                if (delta > 0f && !listAtTop) return@launch
                swipeInProgress = true
                if (dragStartProgress.value == null) {
                    dragStartProgress.value = sheetProgress.value
                }
                val travel = (screenHeightPx - effectivePeekPx).coerceAtLeast(1f)
                val next = (sheetProgress.value - delta / travel).coerceIn(0f, 1f)
                sheetProgress.snapTo(next)
            }
        }
        val onSheetDragSettle: () -> Unit = {
            coroutineScope.launch {
                swipeInProgress = false
                val start = dragStartProgress.value ?: 0f
                val end = sheetProgress.value
                // Round 2.16.G — flick-commit math factored to
                // [flickCommitTarget] so it can be unit-tested without
                // standing up the full draggable + nested-scroll host.
                val target = flickCommitTarget(start = start, end = end)
                sheetProgress.animateTo(target)
                dragStartProgress.value = null
            }
        }

        // ---- Layer 1: existing app chrome (with bottom inset = peek). ----
        val libraryBottomPad = if (showMiniPlayer) peekDp else 0.dp
        Box(modifier = Modifier.fillMaxSize().padding(bottom = libraryBottomPad)) {
            content()
        }

        // ---- Layer 2: bottom-anchored sheet (Auxio-style). ----
        // Round 2.16.D.7 — the sheet container is always rendered so the
        // D.7 FAB can animate it open even with no active task. The peek
        // (mini-player) still only renders when `hasMedia` is true; an
        // unopened sheet with no media has effectivePeekPx=0 and progress
        // 0 → sheetHeight 0, so nothing is visible.
        run {
            val sheetHeightPx = effectivePeekPx + progress * (screenHeightPx - effectivePeekPx)
            val sheetHeightDp = with(density) { sheetHeightPx.toDp() }

            val nestedDragDirection = remember { mutableStateOf(0) }
            val sheetNestedScroll = remember(screenHeightPx, effectivePeekPx) {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (source != NestedScrollSource.UserInput)
                            return Offset.Zero
                        if (available.y < 0f && sheetProgress.value < 1f) {
                            val travel = (screenHeightPx - effectivePeekPx).coerceAtLeast(1f)
                            val delta = -available.y / travel
                            nestedDragDirection.value = -1
                            coroutineScope.launch {
                                sheetProgress.snapTo((sheetProgress.value + delta).coerceAtMost(1f))
                            }
                            return Offset(0f, available.y)
                        }
                        // D-25 fix — remove the nested-scroll dismiss path for
                        // downward swipes on the sheet CONTENT. Sheet dismissal
                        // is now exclusively the drag handle's responsibility
                        // (the outer `.draggable()` on this Box). Previously this
                        // block consumed downward-overscroll-at-top and moved
                        // sheetProgress toward 0; that caused any fast downward
                        // fling through the list to dismiss the sheet once the
                        // list bounced back to the top. The handle still dismisses
                        // (via onSheetDragDelta / onSheetDragSettle), so the user
                        // can always pull the sheet down from the handle.
                        return Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (source != NestedScrollSource.UserInput)
                            return Offset.Zero
                        // D-25 fix — no longer consume downward overscroll here.
                        // See onPreScroll comment above.
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(
                        available: Velocity,
                    ): Velocity {
                        val dir = nestedDragDirection.value
                        // Only snap if the sheet was actually pulled upward
                        // (dir < 0 = user was expanding). Never snap to close
                        // via nested scroll — handle-only dismiss.
                        if (dir < 0) {
                            sheetProgress.animateTo(1f)
                        }
                        // If dir == 0 (no sheet movement): do nothing, let the
                        // list fling propagate.
                        // If dir > 0: this case no longer occurs since onPreScroll
                        // no longer sets dir=1 for downward swipes.
                        nestedDragDirection.value = 0
                        return Velocity.Zero
                    }
                }
            }

            val sheetDraggable = rememberDraggableState { delta ->
                onSheetDragDelta(delta)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(sheetHeightDp)
                    .background(MaterialTheme.colorScheme.surface)
                    .clipToBounds()
                    .nestedScroll(sheetNestedScroll)
                    .draggable(
                        state = sheetDraggable,
                        orientation = Orientation.Vertical,
                        onDragStopped = { onSheetDragSettle() },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(screenHeightDp),
                ) {
                    if (progress > 0.45f) Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(nowPlayingAlpha),
                    ) {
                        NowPlayingScreen(
                            nowPlayingState = now,
                            transport = transport,
                            queueCommands = queue,
                            onBack = closeSheet,
                            nowPlayingListState = nowPlayingListState,
                            // Round 2.16.D — replace the music queue with
                            // the task views: chip-strip + selected
                            // Combined/Today/Per-list/Standing/Shopping.
                            // The body is hoisted as a LazyItemScope-
                            // scoped slot so it can claim viewport height
                            // when needed.
                            showHeroCard = playbackState.hasMedia,
                            // D-25 fix — give the drag handle its own
                            // dedicated gesture surface so downward drags
                            // from the handle area always reach the sheet
                            // host rather than being captured by the inner
                            // LazyColumn.
                            onSheetDragDelta = onSheetDragDelta,
                            onSheetDragSettle = onSheetDragSettle,
                            bodyContent = {
                                // The body fills the real sheet viewport itself
                                // (fillParentMaxHeight) — no more guessed height.
                                ExpandedNowPlayingTaskBody(
                                    tasksState = tasksState,
                                    onOpenTask = { task -> openTask = task },
                                    onStartTask = onStartTask,
                                    onLongPressTask = { task ->
                                        // D-51 fix — pin-to-today for standing (no-deadline)
                                        // tasks. Long-pressing a standing task toggles its
                                        // pinned state; pinned tasks surface in Today's
                                        // "Pinned" section. Non-standing tasks: no-op for now.
                                        if (task.standing) {
                                            tasksState.pinStanding(task.id, !task.pinnedForToday)
                                        }
                                    },
                                )
                            },
                        )
                    }

                    // Round 2.16.D.2 — TaskQuickAdd FAB anchored bottom-end
                    // of the expanded sheet. Tapping does NOT collapse the
                    // sheet (we drive only the quick-add overlay flag).
                    // Visible alpha follows the expanded-sheet crossfade so
                    // it fades in with NowPlayingScreen.
                    // D-42 fix — guard onClick against swipe-release events.
                    // `swipeInProgress` is true during any active drag and is
                    // cleared in onSheetDragSettle. A swipe ending near the
                    // FAB fires a touch-up that Compose routes to the FAB's
                    // onClick; the guard makes the callback a no-op while any
                    // drag is in flight, so only deliberate taps open Quick Add.
                    if (progress > 0.45f) Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp)
                            .alpha(nowPlayingAlpha),
                    ) {
                        com.eight87.strictlykeptboy.ui.tasks.TaskQuickAddFab(
                            onClick = { if (!swipeInProgress) quickAddOpen = true },
                        )
                    }

                    // D-16 fix — only compose the MiniPlayer when miniAlpha > 0f.
                    // At alpha=0 (sheet fully expanded) the Box still consumed
                    // pointer events in the top ~309px, making the first task-row
                    // checkbox and the Today rail center unreachable.
                    if (showMiniPlayer && miniAlpha > 0f) Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(peekDp)
                            .alpha(miniAlpha),
                    ) {
                        MiniPlayer(
                            state = playbackState,
                            onTogglePlayPause = transport::togglePlayPause,
                            onClose = transport::stop,
                            onExpand = openNowPlayingSheet,
                            onSkipNext = transport::seekToNext,
                            onSkipPrevious = transport::seekToPrevious,
                            onPlayButtonLongPress = { transport.stop() },
                            onToggleShuffle = transport::toggleShuffle,
                            onCycleRepeat = transport::cycleRepeatMode,
                            onSeekTo = transport::seekTo,
                            onSheetDragDelta = onSheetDragDelta,
                            onSheetDragSettle = onSheetDragSettle,
                            nowTitle = nowNextSnap?.now?.title,
                            nowEmoji = nowNextSnap?.now?.emoji,
                            nextTitle = nowNextSnap?.next?.title,
                            nextEmoji = nowNextSnap?.next?.emoji,
                            nextRelative = nextRelative,
                        )
                    }
                }
            }
        }

        // Round 2.16.D.3 — TaskDetailSheet over NowPlayingScreen.
        // ModalBottomSheet renders above all sibling Box content per the
        // Compose dialog/sheet z-order convention, so no extra z-index
        // wrangling is needed.
        // D-41 fix — onDismiss starts a 350ms cooldown (`detailJustDismissed`)
        // that blocks the parent sheet's downward drag in `onSheetDragDelta`.
        // This prevents the detail's swipe-down dismiss gesture from
        // propagating to the parent sheet and collapsing it too.
        openTask?.let { t ->
            com.eight87.strictlykeptboy.ui.tasks.TaskDetailSheet(
                task = t,
                onDismiss = {
                    openTask = null
                    detailJustDismissed = true
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(350L)
                        detailJustDismissed = false
                    }
                },
                onEdit = { /* Phase EE — editor stub */ },
                onToggleDone = { tasksState.toggleDone(t.id) },
            )
        }

        // Round 2.16.D.2 — TaskQuickAdd sheet (modal) over NowPlayingScreen.
        if (quickAddOpen) {
            val initialTarget = tasksUi.todolists.firstOrNull()?.let {
                com.eight87.strictlykeptboy.ui.tasks.QuickAddTarget.Todolist(it)
            }
            com.eight87.strictlykeptboy.ui.tasks.TaskQuickAddSheet(
                todolists = tasksUi.todolists,
                initialTarget = initialTarget,
                onDismiss = { quickAddOpen = false },
                // D-40 — receive the optional due date from the sheet and
                // forward it in the request so the disk-write path can choose
                // between StandingTask (null) and dated Task (non-null).
                onSubmit = { title, target, due ->
                    onWriteTask(
                        com.eight87.strictlykeptboy.ui.tasks.TaskQuickAddRequest(
                            title = title,
                            target = target,
                            due = due,
                        ),
                    )
                    quickAddOpen = false
                },
            )
        }

        // D-28 — undo snackbar, anchored at the bottom of the sheet host
        // so it appears above the mini-player peek.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showMiniPlayer) 118.dp else 16.dp),
        )
    }
    } // end CompositionLocalProvider(LocalAnimatingDoneIds)
}
