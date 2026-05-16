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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.task.StubTaskPlaybackSource
import com.eight87.strictlykeptboy.task.TaskNowPlayingState
import com.eight87.strictlykeptboy.task.TaskQueueCommands
import com.eight87.strictlykeptboy.task.TaskTransportCommands
import com.eight87.strictlykeptboy.ui.playing.ExpandedNowPlayingTaskBody
import com.eight87.strictlykeptboy.ui.playing.MiniPlayer
import com.eight87.strictlykeptboy.ui.playing.NowPlayingScreen
import com.eight87.strictlykeptboy.ui.tasks.TaskQuickAddRequest
import com.eight87.strictlykeptboy.ui.tasks.TasksViewState
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
    // Round 2.16.D — task detail / quick-add overlays migrated here
    // from TasksPane so they layer above the sheet per tonearmboy's
    // overlay convention.
    var openTask by remember {
        mutableStateOf<com.eight87.strictlykeptboy.ui.tasks.TaskItem?>(null)
    }
    var quickAddOpen by remember { mutableStateOf(false) }
    val tasksUi by tasksState.state.collectAsState()

    val sheetProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val nowPlayingListState = rememberLazyListState()

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
                        if (available.y > 0f &&
                            nowPlayingListState.firstVisibleItemIndex == 0 &&
                            nowPlayingListState.firstVisibleItemScrollOffset == 0
                        ) {
                            val travel = (screenHeightPx - effectivePeekPx).coerceAtLeast(1f)
                            val delta = available.y / travel
                            nestedDragDirection.value = 1
                            coroutineScope.launch {
                                sheetProgress.snapTo((sheetProgress.value - delta).coerceAtLeast(0f))
                            }
                            return Offset(0f, available.y)
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (source != NestedScrollSource.UserInput)
                            return Offset.Zero
                        if (available.y > 0f) {
                            val travel = (screenHeightPx - effectivePeekPx).coerceAtLeast(1f)
                            val delta = available.y / travel
                            nestedDragDirection.value = 1
                            coroutineScope.launch {
                                sheetProgress.snapTo((sheetProgress.value - delta).coerceAtLeast(0f))
                            }
                            return Offset(0f, available.y)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(
                        available: Velocity,
                    ): Velocity {
                        val dir = nestedDragDirection.value
                        val target = when {
                            dir > 0 -> 0f
                            dir < 0 -> 1f
                            else -> if (sheetProgress.value >= 0.5f) 1f else 0f
                        }
                        sheetProgress.animateTo(target)
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
                            bodyContent = {
                                ExpandedNowPlayingTaskBody(
                                    tasksState = tasksState,
                                    onOpenTask = { task -> openTask = task },
                                    onStartTask = onStartTask,
                                    onLongPressTask = { /* Phase D — TBD */ },
                                    bodyHeight = if (playbackState.hasMedia) {
                                        // Hero + transport ~ 480 dp; leave
                                        // most of the rest of the viewport
                                        // to the task body.
                                        (screenHeightDp - 560.dp).coerceAtLeast(240.dp)
                                    } else {
                                        // No hero → task body fills the
                                        // whole viewport minus top app bar.
                                        (screenHeightDp - 120.dp).coerceAtLeast(360.dp)
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
                    if (progress > 0.45f) Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp)
                            .alpha(nowPlayingAlpha),
                    ) {
                        com.eight87.strictlykeptboy.ui.tasks.TaskQuickAddFab(
                            onClick = { quickAddOpen = true },
                        )
                    }

                    if (showMiniPlayer) Box(
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
                        )
                    }
                }
            }
        }

        // Round 2.16.D.3 — TaskDetailSheet over NowPlayingScreen.
        // ModalBottomSheet renders above all sibling Box content per the
        // Compose dialog/sheet z-order convention, so no extra z-index
        // wrangling is needed.
        openTask?.let { t ->
            com.eight87.strictlykeptboy.ui.tasks.TaskDetailSheet(
                task = t,
                onDismiss = { openTask = null },
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
                onSubmit = { title, target ->
                    onWriteTask(
                        com.eight87.strictlykeptboy.ui.tasks.TaskQuickAddRequest(
                            title = title,
                            target = target,
                        ),
                    )
                    quickAddOpen = false
                },
            )
        }
    }
}
