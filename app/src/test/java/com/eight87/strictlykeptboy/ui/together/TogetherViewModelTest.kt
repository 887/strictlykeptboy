package com.eight87.strictlykeptboy.ui.together

import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.TimeSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TogetherViewModelTest {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun submit_emits_running_then_results_sorted_from_fake_finder() = runTest {
        val tz = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 5, 12).atStartOfDay(tz)
        val s1 = TimeSlot(today.plusHours(10), today.plusHours(12)) // 2h
        val s2 = TimeSlot(today.plusHours(14), today.plusHours(18)) // 4h
        val s3 = TimeSlot(today.plusHours(20), today.plusHours(21)) // 1h
        val fakeFinder = CommonTimeFinderPort { _ -> listOf(s1, s2, s3) }
        val busy = BusySource { ids, _, _, _ ->
            ids.associate { RepoRef(it) to emptyList() }
        }
        val options = MutableStateFlow(
            listOf(TogetherRepoOption("r1", "R1"), TogetherRepoOption("r2", "R2")),
        )
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val vm = TogetherViewModel(scope, options, busy, fakeFinder)

        vm.toggleRepo("r1")
        vm.toggleRepo("r2")
        vm.submit()

        when (val r = vm.result.value) {
            is TogetherResultState.Results -> {
                assertEquals(3, r.slots.size)
                assertEquals(s1, r.slots[0]) // fake returns in given order
            }
            else -> error("expected Results, got $r")
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun submit_with_no_repos_is_no_op() = runTest {
        val fakeFinder = CommonTimeFinderPort { emptyList() }
        val busy = BusySource { _, _, _, _ -> emptyMap() }
        val options = MutableStateFlow(listOf(TogetherRepoOption("r1", "R1")))
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val vm = TogetherViewModel(scope, options, busy, fakeFinder)

        vm.submit()
        assertTrue(vm.result.value is TogetherResultState.Idle)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun submit_with_no_slots_emits_empty() = runTest {
        val fakeFinder = CommonTimeFinderPort { emptyList() }
        val busy = BusySource { _, _, _, _ -> emptyMap() }
        val options = MutableStateFlow(listOf(TogetherRepoOption("r1", "R1")))
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val vm = TogetherViewModel(scope, options, busy, fakeFinder)
        vm.toggleRepo("r1")

        vm.submit()
        assertTrue(vm.result.value is TogetherResultState.Empty)
    }

    @Suppress("unused")
    private fun unused() = TestScope(Dispatchers.Unconfined) // keep imports symmetric
}
