package com.eight87.strictlykeptboy.ui.import_export

import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.port.ics.IcsParseReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase P — narrow view-state port for the Import / Export screen.
 *
 * **R.X.1 (ISP):** the screen takes this, not the whole RepoStore /
 * EntityWriter. The composition root (MainActivity) wires concrete
 * SAF + writer side-effects through the `onConfirmedImport` callback.
 */
class ImportExportViewState(
    val repos: StateFlow<List<RepoConfig>>,
    /** Invoked by [confirmPreview] once the user has accepted the parse summary. */
    private val onConfirmedImport: (IcsParseReport) -> Unit,
) {
    private val _pendingPreview = MutableStateFlow<IcsParseReport?>(null)
    val pendingPreview: StateFlow<IcsParseReport?> = _pendingPreview

    fun showPreview(report: IcsParseReport) {
        _pendingPreview.value = report
    }

    fun confirmPreview() {
        val p = _pendingPreview.value ?: return
        onConfirmedImport(p)
        _pendingPreview.value = null
    }

    fun cancelPreview() {
        _pendingPreview.value = null
    }
}
